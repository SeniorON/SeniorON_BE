ALTER TABLE device
    ADD COLUMN network_connected BOOLEAN NULL,
    ADD COLUMN default_home_enabled BOOLEAN NULL,
    ADD COLUMN location_permission_granted BOOLEAN NULL,
    ADD COLUMN gps_enabled BOOLEAN NULL,
    ADD COLUMN notification_permission_granted BOOLEAN NULL,
    ADD COLUMN app_execution_maintained BOOLEAN NULL,
    ADD COLUMN charging BOOLEAN NULL,
    ADD COLUMN device_status_sharing_enabled BOOLEAN NULL;