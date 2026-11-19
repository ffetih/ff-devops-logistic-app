-- TMS: shipments and their transport legs. A shipment moves one piece of equipment (container
-- or trailer) from origin to destination over one or more legs, each by one transport mode.
-- Every row belongs to one tenant; the app filters by tenant_id on every query.

-- Shipment numbers (SHP-10001) and e-CMR numbers come from this sequence: unique across pods
-- and restarts, never handed out twice.
CREATE SEQUENCE tms_shipment_seq START WITH 10001 INCREMENT BY 1;

CREATE TABLE tms_shipments (
    id                UUID PRIMARY KEY,
    tenant_id         VARCHAR(64)      NOT NULL,
    shipment_number   VARCHAR(20)      NOT NULL,
    ecmr_number       VARCHAR(20)      NOT NULL,
    consignor_name    VARCHAR(140)     NOT NULL,
    consignee_name    VARCHAR(140)     NOT NULL,
    equipment_code    VARCHAR(11)      NOT NULL,
    sscc              VARCHAR(18)      NOT NULL,
    incoterm          VARCHAR(3)       NOT NULL,
    origin            VARCHAR(120)     NOT NULL,
    destination       VARCHAR(120)     NOT NULL,
    gross_weight_kg   DOUBLE PRECISION NOT NULL,
    -- The footprint as calculated when the shipment was planned: kept, not recalculated, so a
    -- later change of emission factors does not rewrite what was reported.
    total_distance_km DOUBLE PRECISION NOT NULL,
    co2e_kg           DOUBLE PRECISION NOT NULL,
    road_only_co2e_kg DOUBLE PRECISION NOT NULL,
    saved_co2e_kg     DOUBLE PRECISION NOT NULL,
    reduction_percent DOUBLE PRECISION NOT NULL,
    status            VARCHAR(20)      NOT NULL,
    created_at        TIMESTAMPTZ      NOT NULL,
    updated_at        TIMESTAMPTZ      NOT NULL,
    CONSTRAINT uq_tms_shipments_number UNIQUE (shipment_number),
    CONSTRAINT uq_tms_shipments_ecmr UNIQUE (ecmr_number)
);

CREATE TABLE tms_transport_legs (
    id                UUID PRIMARY KEY,
    tenant_id         VARCHAR(64)      NOT NULL,
    shipment_id       UUID             NOT NULL REFERENCES tms_shipments (id),
    sequence_no       INTEGER          NOT NULL,
    mode              VARCHAR(20)      NOT NULL,
    origin            VARCHAR(120)     NOT NULL,
    destination       VARCHAR(120)     NOT NULL,
    distance_km       DOUBLE PRECISION NOT NULL,
    carrier_reference VARCHAR(60)      NOT NULL,
    created_at        TIMESTAMPTZ      NOT NULL,
    updated_at        TIMESTAMPTZ      NOT NULL,
    CONSTRAINT uq_tms_transport_legs_sequence UNIQUE (shipment_id, sequence_no)
);

CREATE INDEX idx_tms_shipments_tenant ON tms_shipments (tenant_id, created_at DESC);
CREATE INDEX idx_tms_transport_legs_shipment ON tms_transport_legs (shipment_id);
