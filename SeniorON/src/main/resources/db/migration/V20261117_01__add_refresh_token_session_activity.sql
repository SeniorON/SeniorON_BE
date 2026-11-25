ALTER TABLE refresh_tokens
    ADD COLUMN session_id VARCHAR(36) NULL,
    ADD COLUMN keep_logged_in BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN last_active_at DATETIME(6) NULL;

UPDATE refresh_tokens
SET session_id = UUID(),
    last_active_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP(6))
WHERE session_id IS NULL
   OR last_active_at IS NULL;

ALTER TABLE refresh_tokens
    MODIFY COLUMN session_id VARCHAR(36) NOT NULL,
    MODIFY COLUMN last_active_at DATETIME(6) NOT NULL,
    ADD CONSTRAINT uk_refresh_tokens_session_id UNIQUE (session_id);
