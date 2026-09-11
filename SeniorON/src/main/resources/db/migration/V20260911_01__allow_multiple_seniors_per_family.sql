CREATE INDEX IF NOT EXISTS idx_seniors_family_id_lookup
    ON seniors (family_id);

SET @senior_family_unique_index_name := (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'seniors'
      AND column_name = 'family_id'
      AND non_unique = 0
      AND index_name <> 'PRIMARY'
    GROUP BY index_name
    HAVING COUNT(*) = 1
    LIMIT 1
);

SET @drop_senior_family_unique_index_sql := IF(
    @senior_family_unique_index_name IS NULL,
    'SELECT 1',
    CONCAT(
        'ALTER TABLE seniors DROP INDEX `',
        REPLACE(@senior_family_unique_index_name, '`', '``'),
        '`'
    )
);

PREPARE drop_senior_family_unique_index_stmt
    FROM @drop_senior_family_unique_index_sql;
EXECUTE drop_senior_family_unique_index_stmt;
DEALLOCATE PREPARE drop_senior_family_unique_index_stmt;
