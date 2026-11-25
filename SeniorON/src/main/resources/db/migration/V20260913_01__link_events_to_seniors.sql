ALTER TABLE event
    ADD COLUMN senior_id BIGINT NULL,
    ADD CONSTRAINT fk_event_senior
        FOREIGN KEY (senior_id)
            REFERENCES seniors (senior_id);

UPDATE event e
JOIN seniors s ON s.parent_user_id = e.triggered_users_id
SET e.senior_id = s.senior_id
WHERE e.senior_id IS NULL;

CREATE INDEX idx_event_senior_id
    ON event (senior_id);
