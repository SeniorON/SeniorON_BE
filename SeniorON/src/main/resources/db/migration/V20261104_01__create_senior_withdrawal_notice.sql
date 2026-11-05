CREATE TABLE senior_withdrawal_notice (
    senior_withdrawal_notice_id BIGINT NOT NULL AUTO_INCREMENT,
    senior_id BIGINT NOT NULL,
    family_id BIGINT NOT NULL,
    senior_name VARCHAR(255) NOT NULL,
    withdrawn_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,

    PRIMARY KEY (senior_withdrawal_notice_id),

    CONSTRAINT uk_senior_withdrawal_notice_senior
        UNIQUE (senior_id),

    INDEX idx_senior_withdrawal_notice_family (family_id),
    INDEX idx_senior_withdrawal_notice_expires_at (expires_at)
);

CREATE TABLE senior_withdrawal_notice_recipient (
    senior_withdrawal_notice_recipient_id BIGINT NOT NULL AUTO_INCREMENT,
    senior_withdrawal_notice_id BIGINT NOT NULL,
    recipient_user_id BIGINT NOT NULL,
    relation VARCHAR(255) NULL,
    custom_relation VARCHAR(255) NULL,
    acknowledged_at DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,

    PRIMARY KEY (senior_withdrawal_notice_recipient_id),

    CONSTRAINT uk_senior_withdrawal_notice_recipient
        UNIQUE (senior_withdrawal_notice_id, recipient_user_id),

    CONSTRAINT fk_withdrawal_recipient_notice
        FOREIGN KEY (senior_withdrawal_notice_id)
            REFERENCES senior_withdrawal_notice (senior_withdrawal_notice_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_withdrawal_recipient_user
        FOREIGN KEY (recipient_user_id)
            REFERENCES users (users_id)
            ON DELETE CASCADE,

    INDEX idx_withdrawal_recipient_user_ack (
        recipient_user_id,
        acknowledged_at
    )
);
