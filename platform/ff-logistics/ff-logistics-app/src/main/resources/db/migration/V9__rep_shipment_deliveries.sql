-- Reporting's own copy of delivered shipments, filled from the TMS event ShipmentDelivered.
-- Reporting reads only this table, never tms_shipments: the modules share events, not tables.
-- Events arrive at least once, so the unique shipment_id makes a repeated one harmless.
CREATE TABLE rep_shipment_deliveries (
    id                       UUID PRIMARY KEY,
    tenant_id                VARCHAR(64)      NOT NULL,
    shipment_id              UUID             NOT NULL,
    shipment_number          VARCHAR(20)      NOT NULL,
    customer_order_reference VARCHAR(64),
    total_distance_km        DOUBLE PRECISION NOT NULL,
    saved_co2e_kg            DOUBLE PRECISION NOT NULL,
    delivered_at             TIMESTAMPTZ      NOT NULL,
    created_at               TIMESTAMPTZ      NOT NULL,
    updated_at               TIMESTAMPTZ      NOT NULL,
    CONSTRAINT uq_rep_shipment_deliveries_shipment UNIQUE (shipment_id)
);

CREATE INDEX idx_rep_shipment_deliveries_tenant ON rep_shipment_deliveries (tenant_id, delivered_at DESC);
