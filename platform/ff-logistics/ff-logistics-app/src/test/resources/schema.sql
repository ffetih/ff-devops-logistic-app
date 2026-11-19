-- The tests build their tables from the entities (ddl-auto create-drop), which knows nothing of
-- sequences or of tables without an entity. Those are created here; in the cluster the Flyway
-- scripts create them.
CREATE SEQUENCE IF NOT EXISTS wms_sscc_serial_seq START WITH 100001 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS tms_shipment_seq START WITH 10001 INCREMENT BY 1;

-- Spring Modulith's event publication registry (V8__event_publication.sql), for H2.
CREATE TABLE IF NOT EXISTS event_publication (
    id                     UUID                     NOT NULL,
    listener_id            VARCHAR(512)             NOT NULL,
    event_type             VARCHAR(512)             NOT NULL,
    serialized_event       VARCHAR(4000)            NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 VARCHAR(20),
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
