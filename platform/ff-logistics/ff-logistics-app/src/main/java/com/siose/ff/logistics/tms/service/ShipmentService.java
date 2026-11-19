package com.siose.ff.logistics.tms.service;

import com.siose.ff.base.persistence.DatabaseSequences;
import com.siose.ff.base.web.ResourceNotFoundException;
import com.siose.ff.logistics.standards.TransportMode;
import com.siose.ff.logistics.tms.domain.Incoterm;
import com.siose.ff.logistics.tms.domain.Shipment;
import com.siose.ff.logistics.tms.domain.ShipmentRepository;
import com.siose.ff.logistics.tms.domain.ShipmentStatus;
import com.siose.ff.logistics.tms.domain.TransportLeg;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Shipments of the caller's tenant: planning them and reading them back. */
@Service
public class ShipmentService {

    /** Created in V4__tms_init.sql. */
    private static final String SHIPMENT_SEQUENCE = "tms_shipment_seq";

    private final ShipmentRepository shipments;
    private final DatabaseSequences sequences;

    ShipmentService(ShipmentRepository shipments, DatabaseSequences sequences) {
        this.shipments = shipments;
        this.sequences = sequences;
    }

    public record NewShipment(
            @NotBlank @Size(max = 140) String consignorName,
            @NotBlank @Size(max = 140) String consigneeName,
            @NotBlank String equipmentCode,
            @NotBlank String sscc,
            @NotNull Incoterm incoterm,
            @NotBlank @Size(max = 120) String origin,
            @NotBlank @Size(max = 120) String destination,
            @NotNull @Positive Double grossWeightKg,
            @NotEmpty @Size(max = 10) List<@Valid @NotNull NewLeg> legs) {
    }

    public record NewLeg(
            @NotNull TransportMode mode,
            @NotBlank @Size(max = 120) String origin,
            @NotBlank @Size(max = 120) String destination,
            @NotNull @Positive Double distanceKm,
            @NotBlank @Size(max = 60) String carrierReference) {
    }

    public record ShipmentView(UUID id, String tenantId, String shipmentNumber, String ecmrNumber,
            String consignorName, String consigneeName, String equipmentCode, String sscc, Incoterm incoterm,
            String origin, String destination, double grossWeightKg, ShipmentStatus status, FootprintView footprint,
            List<LegView> legs, Instant createdAt) {
    }

    public record FootprintView(double totalDistanceKm, double co2eKg, double roadOnlyCo2eKg, double savedCo2eKg,
            double reductionPercent) {
    }

    public record LegView(int sequenceNo, TransportMode mode, String origin, String destination, double distanceKm,
            String carrierReference) {
    }

    /**
     * A refused plan takes no shipment number. A gap in the numbers can still happen (the insert
     * fails after the number was taken), and is allowed; a number used twice is not.
     */
    @Transactional
    public ShipmentView plan(NewShipment request) {
        Shipment.Plan plan = new Shipment.Plan(request.consignorName(), request.consigneeName(),
                request.equipmentCode(), request.sscc(), request.incoterm(), request.origin(), request.destination(),
                request.grossWeightKg(), request.legs().stream()
                        .map(leg -> new Shipment.PlannedLeg(leg.mode(), leg.origin(), leg.destination(),
                                leg.distanceKm(), leg.carrierReference()))
                        .toList());
        return view(shipments.save(Shipment.plan(plan, () -> sequences.next(SHIPMENT_SEQUENCE))));
    }

    @Transactional(readOnly = true)
    public List<ShipmentView> list() {
        return shipments.findAllByOrderByCreatedAtDesc().stream().map(ShipmentService::view).toList();
    }

    @Transactional(readOnly = true)
    public ShipmentView get(UUID id) {
        return view(shipments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment " + id + " not found")));
    }

    private static ShipmentView view(Shipment s) {
        return new ShipmentView(s.getId(), s.getTenantId(), s.getShipmentNumber(), s.getEcmrNumber(),
                s.getConsignorName(), s.getConsigneeName(), s.getEquipmentCode(), s.getSscc(), s.getIncoterm(),
                s.getOrigin(), s.getDestination(), s.getGrossWeightKg(), s.getStatus(),
                new FootprintView(s.getTotalDistanceKm(), s.getCo2eKg(), s.getRoadOnlyCo2eKg(), s.getSavedCo2eKg(),
                        s.getReductionPercent()),
                s.getLegs().stream().map(ShipmentService::view).toList(), s.getCreatedAt());
    }

    private static LegView view(TransportLeg leg) {
        return new LegView(leg.getSequenceNo(), leg.getMode(), leg.getOrigin(), leg.getDestination(),
                leg.getDistanceKm(), leg.getCarrierReference());
    }
}
