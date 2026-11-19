package com.siose.ff.logistics.tms;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Published when the consignee has signed for a shipment. It lives in the module's base package,
 * so it is part of the TMS API: other modules may listen to it, and see nothing else of TMS.
 *
 * <p>It carries what a listener needs, so no listener has to read TMS tables, and it carries the
 * tenant: a listener runs later, on another thread, where no request and so no tenant is bound.
 *
 * <p>{@code @Externalized}: with externalization on, the same event also goes to Kafka as JSON, on
 * {@link #TOPIC}, keyed by the shipment id, so every message about one shipment lands on one
 * partition (arrival order is still not guaranteed: see {@link ShipmentProgressed}). Systems outside
 * this application read it, and it is stored as JSON
 * in {@code event_publication} until every listener has handled it, possibly across a release:
 * add fields, never rename or remove them.
 */
@Externalized(ShipmentDelivered.TOPIC + "::#{#this.shipmentId().toString()}")
public record ShipmentDelivered(
        UUID shipmentId,
        String tenantId,
        String shipmentNumber,
        String customerOrderReference,
        double totalDistanceKm,
        double savedCo2eKg,
        Instant deliveredAt) {

    public static final String TOPIC = "ff.logistics.tms.shipment-delivered";
}
