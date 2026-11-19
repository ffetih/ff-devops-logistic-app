package com.siose.ff.logistics.tms;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With externalization on, the shipment events also leave the app: they go to Kafka through the
 * same event publication registry. The broker runs inside the test JVM, so what a consumer outside
 * the app reads is checked for real: topic, key, order and JSON fields.
 */
@SpringBootTest(properties = {
        "spring.modulith.events.externalization.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        // A database of its own: this context's registry rows must not mix with the other contexts'.
        "spring.datasource.url=jdbc:h2:mem:logistics_kafka;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
@EmbeddedKafka
@DirtiesContext
class ShipmentEventsExternalizationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private JdbcTemplate jdbc;

    private final String tenant = newTenant();

    @Test
    void pickupDepartureAndDeliveryReachTheirTopicsKeyedByTheShipment() throws Exception {
        String reference = "PO-" + UUID.randomUUID();
        String body = mvc.perform(post("/api/v1/tms/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(TmsRequests.order(reference)).with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        String number = JsonPath.read(body, "$.shipmentNumber");
        for (String step : List.of("pickup", "departure")) {
            mvc.perform(post("/api/v1/tms/shipments/" + id + "/" + step).with(as(tenant, "LOGISTICS_PLANNER")))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/tms/shipments/" + id + "/delivery").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedBy\": \"J. Becker\"}").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isOk());

        // Not necessarily in this order: each event is sent on its own after its transaction, so
        // the departure can overtake the pickup (it did, in the first run of this test). A consumer
        // orders the steps of a shipment by occurredAt, not by arrival.
        List<ConsumerRecord<String, String>> progress = read(ShipmentProgressed.TOPIC, id, 2);
        assertThat(progress).extracting(record -> JsonPath.<String>read(record.value(), "$.stage"))
                .containsExactlyInAnyOrder("PICKED_UP", "DEPARTED");
        assertThat(progress).extracting(ConsumerRecord::partition).as("one key, one partition").containsOnly(
                progress.getFirst().partition());
        String pickedUp = stage(progress, "PICKED_UP");
        String departed = stage(progress, "DEPARTED");
        assertThat(JsonPath.<String>read(departed, "$.tenantId")).isEqualTo(tenant);
        assertThat(JsonPath.<String>read(departed, "$.shipmentNumber")).isEqualTo(number);
        assertThat(JsonPath.<String>read(departed, "$.customerOrderReference")).isEqualTo(reference);
        assertThat(Instant.parse(JsonPath.read(departed, "$.occurredAt")))
                .isAfterOrEqualTo(Instant.parse(JsonPath.read(pickedUp, "$.occurredAt")));

        String delivered = read(ShipmentDelivered.TOPIC, id, 1).getFirst().value();
        assertThat(JsonPath.<String>read(delivered, "$.shipmentId")).isEqualTo(id);
        assertThat(JsonPath.<String>read(delivered, "$.tenantId")).isEqualTo(tenant);
        assertThat(JsonPath.<Double>read(delivered, "$.savedCo2eKg")).isEqualTo(1912.9);
        assertThat(JsonPath.<Double>read(delivered, "$.totalDistanceKm")).isEqualTo(2580.0);
        assertThat(Instant.parse(JsonPath.read(delivered, "$.deliveredAt"))).isBeforeOrEqualTo(Instant.now());

        // The delivery has two listeners, reporting inside the app and the Kafka externalizer:
        // two registry rows, both completed.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(publications(id, "completion_date is not null")).isEqualTo(4);
            assertThat(publications(id, "completion_date is null")).isZero();
        });
    }

    private static String stage(List<ConsumerRecord<String, String>> records, String stage) {
        return records.stream().map(ConsumerRecord::value)
                .filter(value -> stage.equals(JsonPath.read(value, "$.stage")))
                .findFirst().orElseThrow();
    }

    /** The messages with this key on this topic, in partition order; fails after 30 s without them. */
    private List<ConsumerRecord<String, String>> read(String topic, String key, int count) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "externalization-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plusSeconds(30);
            while (Instant.now().isBefore(deadline) && found.size() < count) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        found.add(record);
                    }
                }
            }
        }
        assertThat(found).as("messages with key %s on %s", key, topic).hasSize(count);
        return found;
    }

    private int publications(String shipmentId, String condition) {
        Integer count = jdbc.queryForObject("select count(*) from event_publication where serialized_event like ? and "
                + condition, Integer.class, "%" + shipmentId + "%");
        return count == null ? 0 : count;
    }
}
