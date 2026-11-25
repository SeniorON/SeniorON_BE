SET @family_code_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'family'
      AND column_name = 'family_code'
);

SET @senior_code_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'family'
      AND column_name = 'senior_code'
);

SET @rename_family_code_sql := IF(
    @family_code_exists > 0 AND @senior_code_exists = 0,
    'ALTER TABLE family CHANGE COLUMN family_code senior_code VARCHAR(255)',
    'SELECT 1'
);

PREPARE rename_family_code_stmt
    FROM @rename_family_code_sql;
EXECUTE rename_family_code_stmt;
DEALLOCATE PREPARE rename_family_code_stmt;
