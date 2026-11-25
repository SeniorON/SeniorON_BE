CREATE TABLE admin_invitation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    invalidated_at DATETIME(6) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_invitation_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_admin_invitation_admin FOREIGN KEY (admin_id) REFERENCES admin (admin_id),
    CONSTRAINT fk_admin_invitation_created_by FOREIGN KEY (created_by) REFERENCES admin (admin_id),
    INDEX idx_admin_invitation_admin_type (admin_id, type)
);
