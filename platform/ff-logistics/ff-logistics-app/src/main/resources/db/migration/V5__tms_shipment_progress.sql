-- When the carrier collected the shipment, and when it left the origin. Empty until then, and
-- for the shipments planned before this version: a column added to a table with rows must
-- either allow NULL or have a default (expand), because the old version keeps writing rows
-- without it while the canary runs.
ALTER TABLE tms_shipments ADD COLUMN picked_up_at TIMESTAMPTZ;
ALTER TABLE tms_shipments ADD COLUMN departed_at TIMESTAMPTZ;
