CREATE TABLE IF NOT EXISTS family_photo_group (
    family_photo_group_id BIGINT NOT NULL AUTO_INCREMENT,
    family_photo_id BIGINT NOT NULL,
    photo_group_id BIGINT NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,

    PRIMARY KEY (family_photo_group_id),

    CONSTRAINT uk_family_photo_group_photo
        UNIQUE (family_photo_id, photo_group_id),

    CONSTRAINT fk_family_photo_group_family_photo
        FOREIGN KEY (family_photo_id)
            REFERENCES family_photo (family_photo_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_family_photo_group_photo_group
        FOREIGN KEY (photo_group_id)
            REFERENCES photo_group (photo_group_id)
            ON DELETE CASCADE,

    INDEX idx_family_photo_group_group_photo (
        photo_group_id,
        family_photo_id
    )
);

INSERT INTO family_photo_group (
    family_photo_id,
    photo_group_id,
    created_at,
    updated_at
)
SELECT
    fp.family_photo_id,
    fp.photo_group_id,
    COALESCE(fp.created_at, CURRENT_TIMESTAMP(6)),
    COALESCE(fp.updated_at, CURRENT_TIMESTAMP(6))
FROM family_photo fp
WHERE fp.photo_group_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM family_photo_group fpg
      WHERE fpg.family_photo_id = fp.family_photo_id
        AND fpg.photo_group_id = fp.photo_group_id
  );