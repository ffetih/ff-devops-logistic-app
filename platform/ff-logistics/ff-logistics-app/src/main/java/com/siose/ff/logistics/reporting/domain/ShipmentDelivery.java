package com.siose.ff.logistics.reporting.domain;

import com.siose.ff.base.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A delivered shipment, as reporting counts it: reporting's own copy, filled from the event. */
@Entity
@Table(name = "rep_shipment_deliveries")
public class ShipmentDelivery extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID shipmentId;

    @Column(nullable = false, length = 20)
    private String shipmentNumber;

    @Column(length = 64)
    private String customerOrderReference;

    private double totalDistanceKm;

    @Column(name = "saved_co2e_kg")
    private double savedCo2eKg;

    @Column(nullable = false)
    private Instant deliveredAt;

    protected ShipmentDelivery() {
    }

    public ShipmentDelivery(UUID shipmentId, String shipmentNumber, String customerOrderReference,
            double totalDistanceKm, double savedCo2eKg, Instant deliveredAt) {
        this.shipmentId = shipmentId;
        this.shipmentNumber = shipmentNumber;
        this.customerOrderReference = customerOrderReference;
        this.totalDistanceKm = totalDistanceKm;
        this.savedCo2eKg = savedCo2eKg;
        this.deliveredAt = deliveredAt;
    }

    public UUID getShipmentId() {
        return shipmentId;
    }

    public String getShipmentNumber() {
        return shipmentNumber;
    }

    public String getCustomerOrderReference() {
        return customerOrderReference;
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public double getSavedCo2eKg() {
        return savedCo2eKg;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
