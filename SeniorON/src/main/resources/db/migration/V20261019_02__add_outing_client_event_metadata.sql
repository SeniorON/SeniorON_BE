ALTER TABLE event
    ADD COLUMN client_event_id VARCHAR(128) NULL,
    ADD COLUMN detected_at DATETIME(6) NULL;
