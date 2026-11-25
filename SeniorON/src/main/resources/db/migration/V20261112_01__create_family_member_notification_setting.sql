CREATE TABLE IF NOT EXISTS family_member_notification_setting (
    family_member_id BIGINT NOT NULL,
    inactivity_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    risk_link_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    outing_return_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (family_member_id),
    CONSTRAINT fk_family_member_notification_setting_family_member
        FOREIGN KEY (family_member_id)
            REFERENCES family_member (family_member_id)
            ON DELETE CASCADE
);

INSERT INTO family_member_notification_setting (
    family_member_id,
    inactivity_enabled,
    risk_link_enabled,
    outing_return_enabled,
    created_at,
    updated_at
)
SELECT
    fm.family_member_id,
    ns.inactivity_enabled,
    ns.risk_link_enabled,
    ns.outing_return_enabled,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM notification_setting ns
JOIN seniors s ON s.parent_user_id = ns.user_id
JOIN family_member fm ON fm.family_id = s.family_id
JOIN users u ON u.users_id = fm.users_id
WHERE u.role = 'CHILD'
  AND NOT EXISTS (
      SELECT 1
      FROM family_member_notification_setting fmns
      WHERE fmns.family_member_id = fm.family_member_id
  );
