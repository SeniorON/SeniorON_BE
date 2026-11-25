CREATE TABLE inquiry (
    inquiry_id BIGINT NOT NULL AUTO_INCREMENT,
    users_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (inquiry_id),

    CONSTRAINT fk_inquiry_users
        FOREIGN KEY (users_id)
            REFERENCES users (users_id)
);

CREATE TABLE inquiry_answer (
    inquiry_answer_id BIGINT NOT NULL AUTO_INCREMENT,
    inquiry_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (inquiry_answer_id),

    CONSTRAINT fk_inquiry_answer_inquiry
        FOREIGN KEY (inquiry_id)
            REFERENCES inquiry (inquiry_id)
);

CREATE TABLE inquiry_image (
    inquiry_image_id BIGINT NOT NULL AUTO_INCREMENT,
    inquiry_id BIGINT NOT NULL,
    image_url VARCHAR(1000) NOT NULL,

    PRIMARY KEY (inquiry_image_id),

    CONSTRAINT fk_inquiry_image_inquiry
        FOREIGN KEY (inquiry_id)
            REFERENCES inquiry (inquiry_id)
);

CREATE INDEX idx_inquiry_users_status_created
    ON inquiry (users_id, status, created_at);

CREATE INDEX idx_inquiry_answer_inquiry_created
    ON inquiry_answer (inquiry_id, created_at);

CREATE INDEX idx_inquiry_image_inquiry
    ON inquiry_image (inquiry_id);
