package com.siose.ff.logistics.geo.api;

import com.siose.ff.base.storage.DocumentTransfer;
import com.siose.ff.logistics.geo.service.ProofOfDeliveryService;
import com.siose.ff.logistics.geo.service.RoutePlanService;
import com.siose.ff.logistics.geo.service.RoutePlanService.NewRoute;
import com.siose.ff.logistics.geo.service.RoutePlanService.RoutePlanView;
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
@RequestMapping("/api/v1/geo/routes")
@PreAuthorize("hasAnyRole('DISPATCHER', 'TENANT_ADMIN')")
public class RoutePlanController {

    private final RoutePlanService service;
    private final ProofOfDeliveryService proofs;

    public RoutePlanController(RoutePlanService service, ProofOfDeliveryService proofs) {
        this.service = service;
        this.proofs = proofs;
    }

    /** Orders the stops and numbers the plan; the answer has the stops in driving order. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoutePlanView plan(@Valid @RequestBody NewRoute request) {
        return service.plan(request);
    }

    @GetMapping
    public List<RoutePlanView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public RoutePlanView get(@PathVariable UUID id) {
        return service.get(id);
    }

    /**
     * Where the client PUTs the stop's signature, with the headers the answer names; valid 15
     * minutes. Refused with 409 once a signature is stored: documents are written once.
     */
    @PostMapping("/{routeId}/stops/{stopId}/epod/upload-link")
    public DocumentTransfer epodUploadLink(@PathVariable UUID routeId, @PathVariable UUID stopId) {
        return proofs.uploadLink(routeId, stopId);
    }

    /** Where the client GETs the stop's signature; 404 until it is uploaded. */
    @GetMapping("/{routeId}/stops/{stopId}/epod/download-link")
    public DocumentTransfer epodDownloadLink(@PathVariable UUID routeId, @PathVariable UUID stopId) {
        return proofs.downloadLink(routeId, stopId);
    }
}
