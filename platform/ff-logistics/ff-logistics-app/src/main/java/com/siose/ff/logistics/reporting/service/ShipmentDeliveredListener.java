package com.siose.ff.logistics.reporting.service;

import com.siose.ff.base.tenant.TenantContext;
import com.siose.ff.base.tenant.TenantContext.TenantInfo;
import com.siose.ff.logistics.tms.ShipmentDelivered;
import java.util.Set;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hears {@link ShipmentDelivered} after the TMS transaction has committed, on a thread of its own.
 *
 * <p>The event publication registry has written one row for this listener in the TMS
 * transaction. It marks the row completed when this method returns; if it throws, or the pod
 * stops first, the row stays open and the event is delivered again later.
 *
 * <p>Not {@code @ApplicationModuleListener}, which would also open the transaction: Hibernate
 * takes the tenant when the transaction starts, before the method could bind it. So the tenant
 * is bound from the event first, then the transactional projection runs.
 */
@Component
class ShipmentDeliveredListener {

    private final ShipmentDeliveryProjection projection;

    ShipmentDeliveredListener(ShipmentDeliveryProjection projection) {
        this.projection = projection;
    }

    @Async
    @TransactionalEventListener
    void on(ShipmentDelivered event) {
        TenantContext.setCurrent(new TenantInfo(event.tenantId(), "system:shipment-delivered", Set.of(), "EVENT"));
        try {
            projection.record(event);
        } finally {
            TenantContext.clear();
        }
    }
}
