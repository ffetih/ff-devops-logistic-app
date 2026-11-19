package com.siose.ff.logistics.reporting.service;

import com.siose.ff.logistics.reporting.domain.ShipmentDelivery;
import com.siose.ff.logistics.reporting.domain.ShipmentDeliveryRepository;
import com.siose.ff.logistics.tms.ShipmentDelivered;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reporting's delivery figures, kept up to date from {@link ShipmentDelivered}. An event arrives
 * at least once: a listener that failed or was cut off is run again. Recording is therefore
 * idempotent; the second time, the shipment is already there and nothing happens.
 */
@Service
public class ShipmentDeliveryProjection {

    private final ShipmentDeliveryRepository deliveries;

    ShipmentDeliveryProjection(ShipmentDeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    public record DeliveryView(UUID shipmentId, String shipmentNumber, String customerOrderReference,
            double totalDistanceKm, double savedCo2eKg, Instant deliveredAt) {
    }

    /** @return false when this shipment was recorded before */
    @Transactional
    public boolean record(ShipmentDelivered event) {
        if (deliveries.existsByShipmentId(event.shipmentId())) {
            return false;
        }
        deliveries.save(new ShipmentDelivery(event.shipmentId(), event.shipmentNumber(),
                event.customerOrderReference(), event.totalDistanceKm(), event.savedCo2eKg(), event.deliveredAt()));
        return true;
    }

    @Transactional(readOnly = true)
    public List<DeliveryView> deliveries() {
        return deliveries.findAllByOrderByDeliveredAtDesc().stream()
                .map(d -> new DeliveryView(d.getShipmentId(), d.getShipmentNumber(), d.getCustomerOrderReference(),
                        d.getTotalDistanceKm(), d.getSavedCo2eKg(), d.getDeliveredAt()))
                .toList();
    }
}
