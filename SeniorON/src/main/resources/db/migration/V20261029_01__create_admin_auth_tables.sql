CREATE TABLE admin (
    admin_id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NULL,
    name VARCHAR(50) NOT NULL,
    admin_role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_login_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (admin_id),
    CONSTRAINT uk_admin_email UNIQUE (email)
);

CREATE TABLE admin_refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_admin_refresh_token_admin FOREIGN KEY (admin_id) REFERENCES admin (admin_id),
    INDEX idx_admin_refresh_token_expires_at (expires_at)
);

CREATE TABLE admin_activity_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_id BIGINT NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT NOT NULL,
    target_name VARCHAR(100) NULL,
    message VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_admin_activity_log_admin FOREIGN KEY (admin_id) REFERENCES admin (admin_id),
    INDEX idx_admin_activity_log_created_at (created_at),
    INDEX idx_admin_activity_log_admin_created_at (admin_id, created_at)
);
