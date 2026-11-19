-- Spring Modulith's event publication registry, the outbox: one row per event and listener,
-- written in the transaction that publishes the event, and completed when the listener has
-- succeeded. A row that stays open is an event a listener has not handled yet. The schema is the
-- one Spring Modulith 2 documents for PostgreSQL; the app does not create it itself
-- (spring.modulith.events.jdbc.schema-initialization.enabled: false).
CREATE TABLE event_publication (
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
