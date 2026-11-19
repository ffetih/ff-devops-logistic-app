package com.siose.ff.logistics.reporting;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.siose.ff.base.tenant.TenantContext;
import com.siose.ff.base.tenant.TenantContext.TenantInfo;
import com.siose.ff.logistics.reporting.service.ShipmentDeliveryProjection;
import com.siose.ff.logistics.tms.ShipmentDelivered;
import com.siose.ff.logistics.tms.TmsRequests;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ShipmentDeliveredEventTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ShipmentDeliveryProjection projection;

    private final String tenant = newTenant();

    private String shipmentId;
    private String shipmentNumber;

    /** A shipment of the TMS test, booked under an order number and on its way. */
    @BeforeEach
    void departed() throws Exception {
        String body = mvc.perform(post("/api/v1/tms/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(TmsRequests.order("PO-" + UUID.randomUUID()))
                        .with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        shipmentId = JsonPath.read(body, "$.id");
        shipmentNumber = JsonPath.read(body, "$.shipmentNumber");
        mvc.perform(post("/api/v1/tms/shipments/" + shipmentId + "/departure").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isOk());
    }

    private void deliver(int expectedStatus) throws Exception {
        mvc.perform(post("/api/v1/tms/shipments/" + shipmentId + "/delivery").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedBy\": \"M. Schneider\"}")
                        .with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().is(expectedStatus));
    }

    private int publications(boolean completed) {
        Integer count = jdbc.queryForObject("select count(*) from event_publication where serialized_event like ?"
                + (completed ? " and completion_date is not null" : ""), Integer.class, "%" + shipmentId + "%");
        return count == null ? 0 : count;
    }

    @Test
    void aDeliveryReachesReporting() throws Exception {
        deliver(200);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mvc.perform(get("/api/v1/reporting/deliveries").with(as(tenant, "REPORT_VIEWER")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(1))
                        .andExpect(jsonPath("$[0].shipmentNumber").value(shipmentNumber))
                        .andExpect(jsonPath("$[0].savedCo2eKg").value(1912.9)));
        // One row in the outbox, for the one listener, and it is completed.
        await().atMost(Duration.ofSeconds(10)).until(() -> publications(true) == 1);
        assertThat(publications(false)).isEqualTo(1);
    }

    @Test
    void aRefusedDeliveryPublishesNothing() throws Exception {
        deliver(200);
        deliver(409);

        await().atMost(Duration.ofSeconds(10)).until(() -> publications(true) == 1);
        assertThat(publications(false)).isEqualTo(1);
    }

    @Test
    void anotherTenantSeesNoneOfIt() throws Exception {
        deliver(200);
        await().atMost(Duration.ofSeconds(10)).until(() -> publications(true) == 1);

        mvc.perform(get("/api/v1/reporting/deliveries").with(as(newTenant(), "REPORT_VIEWER")))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/reporting/deliveries").with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isForbidden());
    }

    /** At least once: the same event a second time changes nothing. */
    @Test
    void recordingTheSameEventTwiceKeepsOneRow() {
        ShipmentDelivered event = new ShipmentDelivered(UUID.randomUUID(), tenant, "SHP-99999", null,
                2580.0, 1912.9, Instant.now());
        TenantContext.setCurrent(new TenantInfo(tenant, "test", Set.of(), "TEST"));
        try {
            assertThat(projection.record(event)).isTrue();
            assertThat(projection.record(event)).isFalse();
            assertThat(projection.deliveries()).hasSize(1);
        } finally {
            TenantContext.clear();
        }
    }
}
