ALTER TABLE family_photo
    ADD COLUMN idempotency_key VARCHAR(36) NULL;

CREATE UNIQUE INDEX uk_family_photo_user_idempotency
    ON family_photo (users_id, idempotency_key);