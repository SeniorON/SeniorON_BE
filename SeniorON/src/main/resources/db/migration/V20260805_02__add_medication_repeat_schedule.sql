ALTER TABLE medication
    ADD COLUMN schedule_start_date DATE NULL,
    ADD COLUMN schedule_end_date DATE NULL,
    ADD COLUMN repeat_type VARCHAR(20) NULL,
    ADD COLUMN repeat_interval INT NULL,
    ADD COLUMN repeat_end_type VARCHAR(20) NULL,
    ADD COLUMN duration_weeks INT NULL;

UPDATE medication
SET schedule_start_date = DATE(effective_from),
    repeat_type = 'WEEKLY',
    repeat_interval = 1,
    repeat_end_type = 'ONGOING'
WHERE schedule_start_date IS NULL;

ALTER TABLE medication
    MODIFY COLUMN schedule_start_date DATE NOT NULL,
    MODIFY COLUMN repeat_type VARCHAR(20) NOT NULL,
    MODIFY COLUMN repeat_interval INT NOT NULL,
    MODIFY COLUMN repeat_end_type VARCHAR(20) NOT NULL;