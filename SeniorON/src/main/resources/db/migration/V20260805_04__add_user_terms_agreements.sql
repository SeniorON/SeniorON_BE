ALTER TABLE users
    ADD COLUMN service_terms_agreed BOOLEAN NULL,
    ADD COLUMN privacy_policy_agreed BOOLEAN NULL,
    ADD COLUMN age_over_14_agreed BOOLEAN NULL,
    ADD COLUMN marketing_agreed BOOLEAN NULL;
