CREATE TABLE IF NOT EXISTS family_photo_senior (
    family_photo_senior_id BIGINT NOT NULL AUTO_INCREMENT,
    family_photo_id BIGINT NOT NULL,
    senior_id BIGINT NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,

    PRIMARY KEY (family_photo_senior_id),

    CONSTRAINT uk_family_photo_senior_photo_senior
        UNIQUE (family_photo_id, senior_id),

    CONSTRAINT fk_family_photo_senior_family_photo
        FOREIGN KEY (family_photo_id)
            REFERENCES family_photo (family_photo_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_family_photo_senior_senior
        FOREIGN KEY (senior_id)
            REFERENCES seniors (senior_id)
            ON DELETE CASCADE,

    INDEX idx_family_photo_senior_senior_photo (
        senior_id,
        family_photo_id
    )
);
