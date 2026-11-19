-- A stop may have a time window: the earliest and the latest arrival, in minutes after the
-- vehicle leaves the depot. Both or neither; null for the stops planned before.
ALTER TABLE geo_route_stops ADD COLUMN ready_minutes INTEGER;
ALTER TABLE geo_route_stops ADD COLUMN due_minutes INTEGER;
