ALTER TABLE family_member
    ADD COLUMN relation VARCHAR(50) NULL,
    ADD COLUMN custom_relation VARCHAR(255) NULL;