CREATE TABLE IF NOT EXISTS family_photo_view (
    family_photo_view_id BIGINT NOT NULL AUTO_INCREMENT,
    family_photo_id BIGINT NOT NULL,
    parent_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,

    PRIMARY KEY (family_photo_view_id),

    CONSTRAINT uk_family_photo_view_photo_parent
        UNIQUE (family_photo_id, parent_user_id),

    CONSTRAINT fk_family_photo_view_family_photo
        FOREIGN KEY (family_photo_id)
            REFERENCES family_photo (family_photo_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_family_photo_view_parent_user
        FOREIGN KEY (parent_user_id)
            REFERENCES users (users_id)
            ON DELETE CASCADE,

    INDEX idx_family_photo_view_parent_photo (
        parent_user_id,
        family_photo_id
    )
);

INSERT INTO family_photo_view (
    family_photo_id,
    parent_user_id,
    created_at,
    updated_at
)
SELECT DISTINCT
    fp.family_photo_id,
    fm.users_id,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM family_photo fp
JOIN family_photo_group fpg
  ON fpg.family_photo_id = fp.family_photo_id
JOIN photo_group_family pgf
  ON pgf.photo_group_id = fpg.photo_group_id
JOIN family_member fm
  ON fm.family_id = pgf.family_id
JOIN users parent
  ON parent.users_id = fm.users_id
 AND parent.role = 'PARENT'
WHERE fp.viewed_by_parent = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM family_photo_view fpv
      WHERE fpv.family_photo_id = fp.family_photo_id
        AND fpv.parent_user_id = fm.users_id
  );
