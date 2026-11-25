CREATE TABLE IF NOT EXISTS email_verification_rate_limit (
    rate_limit_key VARCHAR(191) NOT NULL,
    request_count INT NOT NULL,
    window_started_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,

    PRIMARY KEY (rate_limit_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
