CREATE TABLE IF NOT EXISTS senior_permission_setting (
    senior_id BIGINT NOT NULL,
    location_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    inactivity_detection_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (senior_id),
    CONSTRAINT fk_senior_permission_setting_senior
        FOREIGN KEY (senior_id)
            REFERENCES seniors (senior_id)
);

INSERT INTO senior_permission_setting (
    senior_id,
    location_enabled,
    inactivity_detection_enabled,
    created_at,
    updated_at
)
SELECT
    s.senior_id,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM seniors s
WHERE NOT EXISTS (
    SELECT 1
    FROM senior_permission_setting sps
    WHERE sps.senior_id = s.senior_id
);
