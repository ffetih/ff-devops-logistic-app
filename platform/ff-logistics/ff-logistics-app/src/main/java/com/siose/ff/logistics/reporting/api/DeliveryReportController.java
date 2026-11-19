package com.siose.ff.logistics.reporting.api;

import com.siose.ff.logistics.reporting.service.ShipmentDeliveryProjection;
import com.siose.ff.logistics.reporting.service.ShipmentDeliveryProjection.DeliveryView;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reporting/deliveries")
@PreAuthorize("hasAnyRole('REPORT_VIEWER', 'TENANT_ADMIN')")
public class DeliveryReportController {

    private final ShipmentDeliveryProjection projection;

    public DeliveryReportController(ShipmentDeliveryProjection projection) {
        this.projection = projection;
    }

    /** The tenant's delivered shipments, as reporting has recorded them, newest first. */
    @GetMapping
    public List<DeliveryView> deliveries() {
        return projection.deliveries();
    }
}
