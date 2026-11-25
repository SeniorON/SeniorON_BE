SET @user_senior_unique_index_exists := (
    SELECT COUNT(*)
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'user_seniors'
          AND non_unique = 0
          AND index_name <> 'PRIMARY'
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'users_id,senior_id'
        LIMIT 1
    ) user_senior_unique_indexes
);

SET @add_user_senior_unique_index_sql := IF(
    @user_senior_unique_index_exists > 0,
    'SELECT 1',
    'ALTER TABLE user_seniors ADD CONSTRAINT uk_user_senior UNIQUE (users_id, senior_id)'
);

PREPARE add_user_senior_unique_index_stmt
    FROM @add_user_senior_unique_index_sql;
EXECUTE add_user_senior_unique_index_stmt;
DEALLOCATE PREPARE add_user_senior_unique_index_stmt;
