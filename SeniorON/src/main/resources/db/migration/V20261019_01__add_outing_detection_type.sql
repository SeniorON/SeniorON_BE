ALTER TABLE event
    ADD COLUMN detection_type VARCHAR(32) NULL;

UPDATE event
SET detection_type = 'TRANSITION'
WHERE event_type = 'OUTING_RETURN';
