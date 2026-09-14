CREATE TABLE IF NOT EXISTS family_member (
    family_member_id BIGINT NOT NULL AUTO_INCREMENT,
    users_id BIGINT NOT NULL,
    family_id BIGINT NOT NULL,
    manager_type VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (family_member_id),
    CONSTRAINT uk_family_member_user_family UNIQUE (users_id, family_id),
    CONSTRAINT fk_family_member_user
        FOREIGN KEY (users_id)
            REFERENCES users (users_id),
    CONSTRAINT fk_family_member_family
        FOREIGN KEY (family_id)
            REFERENCES family (family_id)
);

INSERT INTO family_member (
    users_id,
    family_id,
    manager_type,
    created_at,
    updated_at
)
SELECT
    u.users_id,
    u.family_id,
    COALESCE(u.manager_type, 'NONE'),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM users u
WHERE u.family_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM family_member fm
      WHERE fm.users_id = u.users_id
        AND fm.family_id = u.family_id
  );

SET @duplicate_senior_family_count := (
    SELECT COUNT(*)
    FROM (
        SELECT family_id
        FROM seniors
        WHERE family_id IS NOT NULL
        GROUP BY family_id
        HAVING COUNT(*) > 1
    ) duplicate_senior_families
);

SET @duplicate_senior_family_sql := IF(
    @duplicate_senior_family_count = 0,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''Cannot add UNIQUE constraint to seniors.family_id because at least one family has multiple seniors. Resolve duplicate senior rows manually before running this migration.'''
);

PREPARE duplicate_senior_family_stmt
    FROM @duplicate_senior_family_sql;
EXECUTE duplicate_senior_family_stmt;
DEALLOCATE PREPARE duplicate_senior_family_stmt;

SET @senior_family_unique_index_exists := (
    SELECT COUNT(*)
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'seniors'
          AND non_unique = 0
          AND index_name <> 'PRIMARY'
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'family_id'
        LIMIT 1
    ) senior_family_unique_indexes
);

SET @add_senior_family_unique_index_sql := IF(
    @senior_family_unique_index_exists > 0,
    'SELECT 1',
    'ALTER TABLE seniors ADD CONSTRAINT uk_seniors_family_id UNIQUE (family_id)'
);

PREPARE add_senior_family_unique_index_stmt
    FROM @add_senior_family_unique_index_sql;
EXECUTE add_senior_family_unique_index_stmt;
DEALLOCATE PREPARE add_senior_family_unique_index_stmt;

DROP TABLE IF EXISTS user_seniors;

SET @users_family_fk_name := (
    SELECT constraint_name
    FROM information_schema.key_column_usage
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND column_name = 'family_id'
      AND referenced_table_name = 'family'
    LIMIT 1
);

SET @drop_users_family_fk_sql := IF(
    @users_family_fk_name IS NULL,
    'SELECT 1',
    CONCAT(
        'ALTER TABLE users DROP FOREIGN KEY `',
        REPLACE(@users_family_fk_name, '`', '``'),
        '`'
    )
);

PREPARE drop_users_family_fk_stmt
    FROM @drop_users_family_fk_sql;
EXECUTE drop_users_family_fk_stmt;
DEALLOCATE PREPARE drop_users_family_fk_stmt;

SET @users_family_role_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND index_name = 'idx_users_family_role'
);

SET @drop_users_family_role_index_sql := IF(
    @users_family_role_index_exists = 0,
    'SELECT 1',
    'ALTER TABLE users DROP INDEX idx_users_family_role'
);

PREPARE drop_users_family_role_index_stmt
    FROM @drop_users_family_role_index_sql;
EXECUTE drop_users_family_role_index_stmt;
DEALLOCATE PREPARE drop_users_family_role_index_stmt;

SET @users_family_id_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND column_name = 'family_id'
);

SET @drop_users_family_id_sql := IF(
    @users_family_id_exists = 0,
    'SELECT 1',
    'ALTER TABLE users DROP COLUMN family_id'
);

PREPARE drop_users_family_id_stmt
    FROM @drop_users_family_id_sql;
EXECUTE drop_users_family_id_stmt;
DEALLOCATE PREPARE drop_users_family_id_stmt;

SET @users_manager_type_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND column_name = 'manager_type'
);

SET @drop_users_manager_type_sql := IF(
    @users_manager_type_exists = 0,
    'SELECT 1',
    'ALTER TABLE users DROP COLUMN manager_type'
);

PREPARE drop_users_manager_type_stmt
    FROM @drop_users_manager_type_sql;
EXECUTE drop_users_manager_type_stmt;
DEALLOCATE PREPARE drop_users_manager_type_stmt;
