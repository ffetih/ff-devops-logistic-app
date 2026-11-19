package com.siose.ff.logistics.tms.domain;

/**
 * Where a shipment stands, in the order it gets there. A shipment only moves forward: a step
 * may be skipped (a carrier reports the departure without a separate pickup), never repeated
 * or taken back.
 */
public enum ShipmentStatus {

    /** Booked with its legs and footprint; nothing has moved yet. */
    PLANNED,

    /** The carrier has collected the goods from the consignor. */
    PICKED_UP,

    /** The first leg has left the origin. */
    DEPARTED
}
