CREATE TABLE s3_deletion_task (
    s3_deletion_task_id BIGINT NOT NULL AUTO_INCREMENT,
    object_key VARCHAR(1024) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    last_error VARCHAR(512) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (s3_deletion_task_id),
    INDEX idx_s3_deletion_task_due (next_attempt_at, s3_deletion_task_id)
);
