package com.siose.ff.logistics.tms.api;

import com.siose.ff.logistics.tms.service.ShipmentService;
import com.siose.ff.logistics.tms.service.ShipmentService.NewShipment;
import com.siose.ff.logistics.tms.service.ShipmentService.ShipmentView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tms/shipments")
@PreAuthorize("hasAnyRole('LOGISTICS_PLANNER', 'TENANT_ADMIN')")
public class ShipmentController {

    private final ShipmentService service;

    public ShipmentController(ShipmentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentView plan(@Valid @RequestBody NewShipment request) {
        return service.plan(request);
    }

    /** The carrier has collected the goods. Refused once the shipment is further along. */
    @PostMapping("/{id}/pickup")
    public ShipmentView pickUp(@PathVariable UUID id) {
        return service.pickUp(id);
    }

    /** The first leg has left. May follow the pickup or skip it. */
    @PostMapping("/{id}/departure")
    public ShipmentView depart(@PathVariable UUID id) {
        return service.depart(id);
    }

    @GetMapping
    public List<ShipmentView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ShipmentView get(@PathVariable UUID id) {
        return service.get(id);
    }
}
