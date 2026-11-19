package com.siose.ff.logistics.outbox;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.siose.ff.logistics.tms.ShipmentDelivered;
import com.siose.ff.logistics.tms.TmsRequests;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * A listener that fails, and the resubmission that delivers its event again. The schedule is
 * set far apart and the first wait to zero, so the test runs the resubmission itself.
 */
@SpringBootTest(properties = {
        "logistics.events.resubmit-delay=PT1H",
        "logistics.events.resubmit-min-age=PT0S",
        "logistics.events.max-attempts=3"})
@AutoConfigureMockMvc
@Import(IncompleteEventResubmissionTest.FailingListenerConfiguration.class)
class IncompleteEventResubmissionTest {

    /**
     * Fails a given number of times per shipment, then succeeds; counts every call. Used only
     * through its methods: {@code @Async} makes the bean a proxy, whose own fields are empty.
     */
    static class FailingListener {

        private final Map<UUID, Integer> failuresLeft = new ConcurrentHashMap<>();
        private final Map<UUID, AtomicInteger> calls = new ConcurrentHashMap<>();

        public void failFirst(UUID shipment, int times) {
            failuresLeft.put(shipment, times);
        }

        public int calls(UUID shipment) {
            return calls.getOrDefault(shipment, new AtomicInteger()).get();
        }

        @Async
        @TransactionalEventListener
        public void on(ShipmentDelivered event) {
            calls.computeIfAbsent(event.shipmentId(), id -> new AtomicInteger()).incrementAndGet();
            int left = failuresLeft.getOrDefault(event.shipmentId(), 0);
            if (left > 0) {
                failuresLeft.put(event.shipmentId(), left - 1);
                throw new IllegalStateException("Listener down (test)");
            }
        }
    }

    @TestConfiguration
    static class FailingListenerConfiguration {

        @Bean
        FailingListener failingListener() {
            return new FailingListener();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FailingListener listener;

    @Autowired
    private IncompleteEventResubmission resubmission;

    private final String tenant = newTenant();

    /** Plans, departs and delivers a shipment whose event the failing listener fails this often. */
    private UUID deliveredShipment(int failures) throws Exception {
        String body = mvc.perform(post("/api/v1/tms/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(TmsRequests.order("PO-" + UUID.randomUUID()))
                        .with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        listener.failFirst(id, failures);
        mvc.perform(post("/api/v1/tms/shipments/" + id + "/departure").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/tms/shipments/" + id + "/delivery").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedBy\": \"M. Schneider\"}").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isOk());
        return id;
    }

    /** The failing listener's row for this shipment: status, attempts, completed or not. */
    private Map<String, Object> row(UUID shipment) {
        return jdbc.queryForMap("select status, completion_attempts, completion_date from event_publication"
                + " where listener_id like '%FailingListener%' and serialized_event like ?", "%" + shipment + "%");
    }

    private int calls(UUID shipment) {
        return listener.calls(shipment);
    }

    @Test
    void aFailedListenerGetsTheEventAgain() throws Exception {
        UUID shipment = deliveredShipment(1);
        await().atMost(Duration.ofSeconds(10)).until(() -> "FAILED".equals(row(shipment).get("status")));

        assertThat(resubmission.resubmitOutstanding()).isTrue();

        await().atMost(Duration.ofSeconds(10)).until(() -> row(shipment).get("completion_date") != null);
        assertThat(calls(shipment)).isEqualTo(2);
        assertThat(((Number) row(shipment).get("completion_attempts")).intValue()).isEqualTo(2);
    }

    @Test
    void afterTheMaximumAttemptsTheRowStaysOpen() throws Exception {
        UUID shipment = deliveredShipment(10);
        await().atMost(Duration.ofSeconds(10)).until(() -> "FAILED".equals(row(shipment).get("status")));

        for (int run = 0; run < 4; run++) {
            int before = calls(shipment);
            resubmission.resubmitOutstanding();
            if (before < 3) {
                await().atMost(Duration.ofSeconds(10)).until(() -> calls(shipment) > before
                        && "FAILED".equals(row(shipment).get("status")));
            }
        }

        // Attempt 1 by the delivery, 2 and 3 by resubmissions; then no more.
        assertThat(calls(shipment)).isEqualTo(3);
        assertThat(row(shipment).get("completion_date")).isNull();
    }
}
