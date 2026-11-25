ALTER TABLE users
    ADD COLUMN health_info_agreed BOOLEAN NULL,
    ADD COLUMN location_info_agreed BOOLEAN NULL,
    ADD COLUMN family_info_provision_agreed BOOLEAN NULL;
