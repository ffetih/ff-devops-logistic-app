-- Delivery: when, and who signed for the goods at the consignee (the e-CMR's "goods received").
ALTER TABLE tms_shipments ADD COLUMN delivered_at TIMESTAMPTZ;
ALTER TABLE tms_shipments ADD COLUMN received_by VARCHAR(140);

-- Optimistic locking. Every update checks and raises it: UPDATE ... SET version = n + 1 WHERE
-- id = ? AND version = n. Of two requests that read the same version, only the first update
-- finds its row; the second changes nothing and fails. NOT NULL with a default, so the rows
-- that exist already start at 0.
ALTER TABLE tms_shipments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
