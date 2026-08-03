ALTER TABLE refresh_tokens
    DROP INDEX idx_refresh_tokens_user_device,
    ADD CONSTRAINT uk_refresh_tokens_user_device UNIQUE (users_id, device_identifier);
