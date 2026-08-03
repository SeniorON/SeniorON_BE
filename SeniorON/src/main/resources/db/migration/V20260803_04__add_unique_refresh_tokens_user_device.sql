CREATE TABLE refresh_tokens (
    refresh_token_id BIGINT NOT NULL AUTO_INCREMENT,
    users_id BIGINT NOT NULL,
    device_identifier VARCHAR(255),
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked BIT NOT NULL DEFAULT 0,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (refresh_token_id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_users
        FOREIGN KEY (users_id)
        REFERENCES users (users_id)
);

CREATE INDEX idx_refresh_tokens_user_device
    ON refresh_tokens (users_id, device_identifier);

CREATE INDEX idx_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);
