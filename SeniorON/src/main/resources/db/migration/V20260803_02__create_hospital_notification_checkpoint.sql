CREATE TABLE hospital_notification_checkpoint
(
    job_name          VARCHAR(100) NOT NULL,
    last_processed_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (job_name)
);