CREATE INDEX IF NOT EXISTS idx_device_users_id
    ON device (users_id);

CREATE INDEX IF NOT EXISTS idx_event_triggered_user_type
    ON event (triggered_users_id, event_type);

CREATE INDEX IF NOT EXISTS idx_family_photo_family_created
    ON family_photo (family_id, created_at, family_photo_id);

CREATE INDEX IF NOT EXISTS idx_family_photo_family_user
    ON family_photo (family_id, users_id);

CREATE INDEX IF NOT EXISTS idx_hospital_user_schedule
    ON hospital (users_id, schedule_date);

CREATE INDEX IF NOT EXISTS idx_medication_user_effective
    ON medication (users_id, effective_from, effective_to);

CREATE INDEX IF NOT EXISTS idx_med_log_user_date
    ON medication_log (users_id, planned_date);

CREATE INDEX IF NOT EXISTS idx_med_log_date_time_taken
    ON medication_log (planned_date, planned_time, is_taken);

CREATE INDEX IF NOT EXISTS idx_notification_receiver_type_read_created
    ON notification (receiver_users_id, notification_type, is_read, created_at);

CREATE INDEX IF NOT EXISTS idx_users_family_role
    ON users (family_id, role);
