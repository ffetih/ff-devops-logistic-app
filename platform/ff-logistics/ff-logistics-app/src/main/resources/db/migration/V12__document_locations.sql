-- Where a document is stored: s3://<bucket>/<tenant>/<category>/<reference>/<file>, reserved when
-- its first upload link is asked for. Null until then, also for every row from before.
ALTER TABLE geo_route_stops ADD COLUMN epod_document_uri VARCHAR(300);
ALTER TABLE tms_shipments ADD COLUMN ecmr_document_uri VARCHAR(300);
