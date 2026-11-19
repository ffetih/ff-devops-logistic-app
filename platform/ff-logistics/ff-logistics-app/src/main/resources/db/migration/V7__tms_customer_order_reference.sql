-- The customer's own order number, so a client that retries a request it got no answer to
-- finds the shipment it already created instead of booking a second one. Optional: shipments
-- planned without one, and those planned before this version, keep NULL.
ALTER TABLE tms_shipments ADD COLUMN customer_order_reference VARCHAR(64);

-- One shipment per order number and tenant. The database is the referee when two requests
-- with the same new number arrive together: only one insert can commit. NULLs never clash.
CREATE UNIQUE INDEX uq_tms_shipments_customer_order ON tms_shipments (tenant_id, customer_order_reference);
