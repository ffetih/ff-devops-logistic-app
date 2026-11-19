package com.siose.ff.logistics.tms.domain;

import com.siose.ff.base.persistence.TenantAwareEntity;
import com.siose.ff.logistics.standards.EmissionsCalculator;
import com.siose.ff.logistics.standards.EmissionsCalculator.Footprint;
import com.siose.ff.logistics.standards.Gs1Standards;
import com.siose.ff.logistics.standards.Iso6346ContainerCode;
import com.siose.ff.logistics.standards.TransportMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * One consignment moved for a customer: one piece of equipment, labelled with the SSCC of what
 * it carries, from origin to destination over a chain of transport legs.
 *
 * <p>The e-CMR number is the electronic consignment note's: the road waybill of the CMR
 * convention, which every road leg in Europe travels under. The CO2e footprint is calculated
 * once, when the shipment is planned, and kept as it was.
 *
 * <p>The shipment and its legs are one aggregate: legs are created only through the shipment,
 * which checks that they form one unbroken chain.
 */
@Entity
@Table(name = "tms_shipments")
public class Shipment extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String shipmentNumber;

    @Column(nullable = false, length = 20)
    private String ecmrNumber;

    @Column(nullable = false, length = 140)
    private String consignorName;

    @Column(nullable = false, length = 140)
    private String consigneeName;

    @Column(nullable = false, length = 11)
    private String equipmentCode;

    @Column(nullable = false, length = 18)
    private String sscc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Incoterm incoterm;

    @Column(nullable = false, length = 120)
    private String origin;

    @Column(nullable = false, length = 120)
    private String destination;

    private double grossWeightKg;

    private double totalDistanceKm;

    private double co2eKg;

    private double roadOnlyCo2eKg;

    private double savedCo2eKg;

    private double reductionPercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status;

    private Instant pickedUpAt;

    private Instant departedAt;

    private Instant deliveredAt;

    @Column(length = 140)
    private String receivedBy;

    /**
     * Optimistic lock: two requests that read the same shipment and both change it cannot both
     * commit. The second fails, so a shipment is delivered once even when two drivers' apps
     * confirm at the same moment.
     */
    @Version
    private long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "shipment_id", nullable = false)
    @OrderBy("sequenceNo")
    private List<TransportLeg> legs = new ArrayList<>();

    protected Shipment() {
    }

    /** What a planner books. Legs are given in order; each starts where the previous one ended. */
    public record Plan(String consignorName, String consigneeName, String equipmentCode, String sscc,
            Incoterm incoterm, String origin, String destination, double grossWeightKg, List<PlannedLeg> legs) {
    }

    public record PlannedLeg(TransportMode mode, String origin, String destination, double distanceKm,
            String carrierReference) {
    }

    /**
     * @param numbers hands out the shipment's serial number ({@code tms_shipment_seq}); asked only
     *                once the plan is valid, so a refused plan does not use up a number
     * @throws IllegalArgumentException if the equipment code or SSCC is not valid, or the legs do
     *                                  not form one chain from origin to destination
     */
    public static Shipment plan(Plan plan, LongSupplier numbers) {
        String equipmentCode = plan.equipmentCode().trim().toUpperCase();
        if (!Iso6346ContainerCode.isValid(equipmentCode)) {
            throw new IllegalArgumentException("Equipment " + plan.equipmentCode()
                    + " is not an ISO 6346 code with a valid check digit");
        }
        if (!Gs1Standards.isValidSscc18(plan.sscc())) {
            throw new IllegalArgumentException("SSCC " + plan.sscc() + " is not 18 digits with a valid GS1 check digit");
        }
        if (plan.grossWeightKg() <= 0) {
            throw new IllegalArgumentException("Gross weight must be more than 0 kg");
        }

        Shipment shipment = new Shipment();
        shipment.consignorName = plan.consignorName().trim();
        shipment.consigneeName = plan.consigneeName().trim();
        shipment.equipmentCode = equipmentCode;
        shipment.sscc = plan.sscc();
        shipment.incoterm = plan.incoterm();
        shipment.origin = plan.origin().trim();
        shipment.destination = plan.destination().trim();
        shipment.grossWeightKg = plan.grossWeightKg();
        shipment.status = ShipmentStatus.PLANNED;

        String expectedOrigin = shipment.origin;
        for (PlannedLeg leg : plan.legs()) {
            if (!leg.origin().trim().equalsIgnoreCase(expectedOrigin)) {
                throw new IllegalArgumentException("Leg " + (shipment.legs.size() + 1) + " starts at " + leg.origin().trim()
                        + ", but the route is at " + expectedOrigin);
            }
            if (leg.distanceKm() <= 0) {
                throw new IllegalArgumentException("Leg " + (shipment.legs.size() + 1) + " needs a distance above 0 km");
            }
            shipment.legs.add(new TransportLeg(shipment.legs.size() + 1, leg.mode(), leg.origin().trim(),
                    leg.destination().trim(), leg.distanceKm(), leg.carrierReference().trim()));
            expectedOrigin = leg.destination().trim();
        }
        if (!expectedOrigin.equalsIgnoreCase(shipment.destination)) {
            throw new IllegalArgumentException("The last leg ends at " + expectedOrigin
                    + ", not at the destination " + shipment.destination);
        }

        Footprint footprint = EmissionsCalculator.footprint(shipment.grossWeightKg, shipment.legs.stream()
                .map(leg -> new EmissionsCalculator.Leg(leg.getMode(), leg.getDistanceKm()))
                .toList());
        shipment.totalDistanceKm = footprint.totalDistanceKm();
        shipment.co2eKg = footprint.co2eKg();
        shipment.roadOnlyCo2eKg = footprint.roadOnlyCo2eKg();
        shipment.savedCo2eKg = footprint.savedCo2eKg();
        shipment.reductionPercent = footprint.reductionPercent();

        long number = numbers.getAsLong();
        shipment.shipmentNumber = "SHP-" + number;
        shipment.ecmrNumber = "ECMR-" + number;
        return shipment;
    }

    /** The carrier has collected the goods. */
    public void pickUp(Instant at) {
        advanceTo(ShipmentStatus.PICKED_UP);
        pickedUpAt = at;
    }

    /** The first leg has left the origin. */
    public void depart(Instant at) {
        advanceTo(ShipmentStatus.DEPARTED);
        departedAt = at;
    }

    /** The consignee has signed for the goods; {@code receivedBy} is the name under the signature. */
    public void deliver(Instant at, String receivedBy) {
        advanceTo(ShipmentStatus.DELIVERED);
        deliveredAt = at;
        this.receivedBy = receivedBy.trim();
    }

    /** Forward only: the target must come after the current status. */
    private void advanceTo(ShipmentStatus target) {
        if (target.compareTo(status) <= 0) {
            throw TmsConflictException.statusConflict(shipmentNumber, status, target);
        }
        status = target;
    }

    public UUID getId() {
        return id;
    }

    public String getShipmentNumber() {
        return shipmentNumber;
    }

    public String getEcmrNumber() {
        return ecmrNumber;
    }

    public String getConsignorName() {
        return consignorName;
    }

    public String getConsigneeName() {
        return consigneeName;
    }

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public String getSscc() {
        return sscc;
    }

    public Incoterm getIncoterm() {
        return incoterm;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public double getGrossWeightKg() {
        return grossWeightKg;
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public double getCo2eKg() {
        return co2eKg;
    }

    public double getRoadOnlyCo2eKg() {
        return roadOnlyCo2eKg;
    }

    public double getSavedCo2eKg() {
        return savedCo2eKg;
    }

    public double getReductionPercent() {
        return reductionPercent;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public Instant getPickedUpAt() {
        return pickedUpAt;
    }

    public Instant getDepartedAt() {
        return departedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public List<TransportLeg> getLegs() {
        return List.copyOf(legs);
    }
}
