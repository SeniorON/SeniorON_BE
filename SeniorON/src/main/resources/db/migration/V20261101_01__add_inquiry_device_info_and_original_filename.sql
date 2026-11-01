
ALTER TABLE inquiry
    ADD COLUMN device_model VARCHAR(255) NULL,
    ADD COLUMN os_version VARCHAR(255) NULL,
    ADD COLUMN app_version VARCHAR(255) NULL;

ALTER TABLE inquiry_image
    ADD COLUMN original_file_name VARCHAR(255) NULL;
