package com.siose.ff.logistics.tms.domain;

import com.siose.ff.base.web.ConflictException;

/** A request that clashes with where a shipment already stands: 409, one problem type per case. */
public class TmsConflictException extends ConflictException {

    private final String slug;
    private final String title;

    private TmsConflictException(String slug, String title, String message) {
        super(message);
        this.slug = slug;
        this.title = title;
    }

    static TmsConflictException statusConflict(String shipmentNumber, ShipmentStatus current, ShipmentStatus target) {
        return new TmsConflictException("shipment-status-conflict", "Shipment Status Conflict",
                "Shipment " + shipmentNumber + " is " + current + "; it cannot become " + target
                        + ", a shipment only moves forward");
    }

    @Override
    public String problemSlug() {
        return slug;
    }

    @Override
    public String problemTitle() {
        return title;
    }
}
