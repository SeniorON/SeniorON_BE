CREATE TABLE IF NOT EXISTS family_user_report (
    family_user_report_id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_user_id BIGINT NOT NULL,
    reported_user_id BIGINT NOT NULL,
    family_photo_id BIGINT NULL,
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

    PRIMARY KEY (family_user_report_id),

    CONSTRAINT uk_family_user_report_reported_reporter
        UNIQUE (reported_user_id, reporter_user_id),

    CONSTRAINT fk_family_user_report_reporter_user
        FOREIGN KEY (reporter_user_id)
            REFERENCES users (users_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_family_user_report_reported_user
        FOREIGN KEY (reported_user_id)
            REFERENCES users (users_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_family_user_report_family_photo
        FOREIGN KEY (family_photo_id)
            REFERENCES family_photo (family_photo_id)
            ON DELETE SET NULL,

    CONSTRAINT fk_family_user_report_processed_admin
        FOREIGN KEY (processed_by_admin_id)
            REFERENCES admin (admin_id)
            ON DELETE SET NULL,

    INDEX idx_family_user_report_reporter_hidden_reported (
        reporter_user_id,
        hidden,
        reported_user_id
    ),

    INDEX idx_family_user_report_image_key_status (
        image_key,
        status
    ),

    INDEX idx_family_user_report_status_created (
        status,
        created_at
    )
);