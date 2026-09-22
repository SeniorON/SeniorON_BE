CREATE TABLE senior_relogin_requests (
    senior_relogin_request_id BIGINT NOT NULL AUTO_INCREMENT,
    senior_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),

    PRIMARY KEY (senior_relogin_request_id),

    CONSTRAINT fk_senior_relogin_requests_senior
        FOREIGN KEY (senior_id)
            REFERENCES seniors (senior_id),

    CONSTRAINT fk_senior_relogin_requests_device
        FOREIGN KEY (device_id)
            REFERENCES device (device_id)
);

CREATE INDEX idx_senior_relogin_requests_senior_status
    ON senior_relogin_requests (senior_id, status);

CREATE INDEX idx_senior_relogin_requests_device_status
    ON senior_relogin_requests (device_id, status);

CREATE INDEX idx_senior_relogin_requests_expires_at
    ON senior_relogin_requests (expires_at);
