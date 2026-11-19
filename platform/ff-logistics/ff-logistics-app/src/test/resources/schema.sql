-- The tests build their tables from the entities (ddl-auto create-drop), which knows nothing of
-- sequences. The ones DatabaseSequences reads are created here; in the cluster the Flyway
-- scripts create them.
CREATE SEQUENCE IF NOT EXISTS wms_sscc_serial_seq START WITH 100001 INCREMENT BY 1;
