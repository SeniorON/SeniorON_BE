ALTER TABLE seniors
    ADD COLUMN parent_user_id BIGINT NULL,
    ADD CONSTRAINT uk_seniors_parent_user
        UNIQUE (parent_user_id),
    ADD CONSTRAINT fk_seniors_parent_user
        FOREIGN KEY (parent_user_id)
            REFERENCES users (users_id);
