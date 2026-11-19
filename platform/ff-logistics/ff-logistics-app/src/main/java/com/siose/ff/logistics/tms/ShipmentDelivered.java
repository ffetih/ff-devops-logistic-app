package com.siose.ff.logistics.tms;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when the consignee has signed for a shipment. It lives in the module's base package,
 * so it is part of the TMS API: other modules may listen to it, and see nothing else of TMS.
 *
 * <p>It carries what a listener needs, so no listener has to read TMS tables, and it carries the
 * tenant: a listener runs later, on another thread, where no request and so no tenant is bound.
 *
 * <p>The event is stored as JSON in {@code event_publication} until its listeners have handled
 * it, possibly across a release: add fields, never rename or remove them.
 */
public record ShipmentDelivered(
        UUID shipmentId,
        String tenantId,
        String shipmentNumber,
        String customerOrderReference,
        double totalDistanceKm,
        double savedCo2eKg,
        Instant deliveredAt) {
}
