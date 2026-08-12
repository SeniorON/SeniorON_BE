CREATE TABLE IF NOT EXISTS signup_email_verification_code (
    signup_email_verification_code_id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    verified BIT NOT NULL DEFAULT 0,
    verified_at DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,

    PRIMARY KEY (signup_email_verification_code_id),

    CONSTRAINT uk_signup_email_verification_code_email
        UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
