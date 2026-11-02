
ALTER TABLE inquiry
    ADD COLUMN completed_at DATETIME(6) NULL,
ADD COLUMN completed_by_admin_id BIGINT NULL;

ALTER TABLE inquiry
    ADD CONSTRAINT fk_inquiry_completed_by_admin
        FOREIGN KEY (completed_by_admin_id)
            REFERENCES admin(admin_id);
