CREATE TABLE IF NOT EXISTS family_photo_report (
    family_photo_report_id BIGINT NOT NULL AUTO_INCREMENT,
    family_photo_id BIGINT NULL,
    reporter_user_id BIGINT NOT NULL,
    image_key VARCHAR(255) NOT NULL,
    reason VARCHAR(30) NOT NULL,
    detail VARCHAR(200) NULL,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    processed_at DATETIME(6) NULL,
    processed_by_admin_id BIGINT NULL,
    admin_memo VARCHAR(500) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,

    PRIMARY KEY (family_photo_report_id),

    CONSTRAINT uk_family_photo_report_photo_reporter
        UNIQUE (family_photo_id, reporter_user_id),

    CONSTRAINT fk_family_photo_report_family_photo
        FOREIGN KEY (family_photo_id)
            REFERENCES family_photo (family_photo_id)
            ON DELETE SET NULL,

    CONSTRAINT fk_family_photo_report_reporter_user
        FOREIGN KEY (reporter_user_id)
            REFERENCES users (users_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_family_photo_report_processed_admin
        FOREIGN KEY (processed_by_admin_id)
            REFERENCES admin (admin_id)
            ON DELETE SET NULL,

    INDEX idx_family_photo_report_reporter_hidden_photo (
        reporter_user_id,
        hidden,
        family_photo_id
    ),

    INDEX idx_family_photo_report_status_created (
        status,
        created_at
    )
);