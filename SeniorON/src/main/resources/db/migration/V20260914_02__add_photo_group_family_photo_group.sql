CREATE TABLE IF NOT EXISTS photo_group (
    photo_group_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (photo_group_id)
);

CREATE TABLE IF NOT EXISTS photo_group_family (
    photo_group_family_id BIGINT NOT NULL AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    photo_group_id BIGINT NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (photo_group_family_id),
    CONSTRAINT uk_photo_group_family_family_group
        UNIQUE (family_id, photo_group_id),
    CONSTRAINT fk_photo_group_family_family
        FOREIGN KEY (family_id)
            REFERENCES family (family_id),
    CONSTRAINT fk_photo_group_family_photo_group
        FOREIGN KEY (photo_group_id)
            REFERENCES photo_group (photo_group_id)
);

CREATE TEMPORARY TABLE tmp_abort_family_photo_null_family_id (
    id TINYINT NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO tmp_abort_family_photo_null_family_id (id)
VALUES (1);

INSERT INTO tmp_abort_family_photo_null_family_id (id)
SELECT 1
FROM family_photo
WHERE family_id IS NULL
LIMIT 1;

CREATE TEMPORARY TABLE tmp_abort_family_photo_orphan_family_id (
    id TINYINT NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO tmp_abort_family_photo_orphan_family_id (id)
VALUES (1);

INSERT INTO tmp_abort_family_photo_orphan_family_id (id)
SELECT 1
FROM family_photo fp
LEFT JOIN family f
    ON f.family_id = fp.family_id
WHERE fp.family_id IS NOT NULL
  AND f.family_id IS NULL
LIMIT 1;

CREATE TEMPORARY TABLE tmp_family_default_photo_group_map (
    family_id BIGINT NOT NULL,
    photo_group_id BIGINT NOT NULL,
    PRIMARY KEY (family_id)
);

INSERT INTO tmp_family_default_photo_group_map (family_id, photo_group_id)
SELECT
    f.family_id,
    MIN(pgf.photo_group_id)
FROM family f
JOIN photo_group_family pgf
    ON pgf.family_id = f.family_id
GROUP BY f.family_id;

CREATE TEMPORARY TABLE tmp_family_without_photo_group (
    seq BIGINT NOT NULL AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    photo_group_id BIGINT NULL,
    PRIMARY KEY (seq),
    UNIQUE KEY uk_tmp_family_without_photo_group_family (family_id)
);

INSERT INTO tmp_family_without_photo_group (family_id)
SELECT f.family_id
FROM family f
LEFT JOIN tmp_family_default_photo_group_map map
    ON map.family_id = f.family_id
WHERE map.family_id IS NULL
ORDER BY f.family_id;

INSERT INTO photo_group (name, created_at, updated_at)
SELECT
    CONCAT('Family ', family_id),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM tmp_family_without_photo_group
ORDER BY seq;

SET @first_default_photo_group_id := LAST_INSERT_ID();

UPDATE tmp_family_without_photo_group
SET photo_group_id =
        @first_default_photo_group_id
        + ((seq - 1) * @@auto_increment_increment);

CREATE TEMPORARY TABLE tmp_abort_photo_group_mapping_missing (
    id TINYINT NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO tmp_abort_photo_group_mapping_missing (id)
VALUES (1);

INSERT INTO tmp_abort_photo_group_mapping_missing (id)
SELECT 1
FROM tmp_family_without_photo_group tmp
LEFT JOIN photo_group pg
    ON pg.photo_group_id = tmp.photo_group_id
WHERE tmp.photo_group_id IS NULL
   OR pg.photo_group_id IS NULL
LIMIT 1;

INSERT INTO tmp_family_default_photo_group_map (family_id, photo_group_id)
SELECT
    family_id,
    photo_group_id
FROM tmp_family_without_photo_group;

INSERT INTO photo_group_family (
    family_id,
    photo_group_id,
    created_at,
    updated_at
)
SELECT
    map.family_id,
    map.photo_group_id,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM tmp_family_default_photo_group_map map
WHERE NOT EXISTS (
    SELECT 1
    FROM photo_group_family pgf
    WHERE pgf.family_id = map.family_id
      AND pgf.photo_group_id = map.photo_group_id
);

SET @family_photo_photo_group_id_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'family_photo'
      AND column_name = 'photo_group_id'
);

SET @add_family_photo_photo_group_id_sql := IF(
    @family_photo_photo_group_id_exists > 0,
    'SELECT 1',
    'ALTER TABLE family_photo ADD COLUMN photo_group_id BIGINT NULL'
);

PREPARE add_family_photo_photo_group_id_stmt
    FROM @add_family_photo_photo_group_id_sql;
EXECUTE add_family_photo_photo_group_id_stmt;
DEALLOCATE PREPARE add_family_photo_photo_group_id_stmt;

UPDATE family_photo fp
JOIN tmp_family_default_photo_group_map map
    ON map.family_id = fp.family_id
SET fp.photo_group_id = map.photo_group_id
WHERE fp.photo_group_id IS NULL;

CREATE TEMPORARY TABLE tmp_abort_family_photo_unmapped_photo_group (
    id TINYINT NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO tmp_abort_family_photo_unmapped_photo_group (id)
VALUES (1);

INSERT INTO tmp_abort_family_photo_unmapped_photo_group (id)
SELECT 1
FROM family_photo
WHERE photo_group_id IS NULL
LIMIT 1;

ALTER TABLE family_photo
    MODIFY photo_group_id BIGINT NOT NULL;

SET @family_photo_group_fk_exists := (
    SELECT COUNT(*)
    FROM information_schema.key_column_usage
    WHERE table_schema = DATABASE()
      AND table_name = 'family_photo'
      AND column_name = 'photo_group_id'
      AND referenced_table_name = 'photo_group'
);

SET @add_family_photo_group_fk_sql := IF(
    @family_photo_group_fk_exists > 0,
    'SELECT 1',
    'ALTER TABLE family_photo ADD CONSTRAINT fk_family_photo_photo_group FOREIGN KEY (photo_group_id) REFERENCES photo_group (photo_group_id)'
);

PREPARE add_family_photo_group_fk_stmt
    FROM @add_family_photo_group_fk_sql;
EXECUTE add_family_photo_group_fk_stmt;
DEALLOCATE PREPARE add_family_photo_group_fk_stmt;

SET @idx_family_photo_group_created_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'family_photo'
      AND index_name = 'idx_family_photo_group_created'
);

SET @add_idx_family_photo_group_created_sql := IF(
    @idx_family_photo_group_created_exists > 0,
    'SELECT 1',
    'CREATE INDEX idx_family_photo_group_created ON family_photo (photo_group_id, created_at, family_photo_id)'
);

PREPARE add_idx_family_photo_group_created_stmt
    FROM @add_idx_family_photo_group_created_sql;
EXECUTE add_idx_family_photo_group_created_stmt;
DEALLOCATE PREPARE add_idx_family_photo_group_created_stmt;

SET @idx_family_photo_group_user_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'family_photo'
      AND index_name = 'idx_family_photo_group_user'
);

SET @add_idx_family_photo_group_user_sql := IF(
    @idx_family_photo_group_user_exists > 0,
    'SELECT 1',
    'CREATE INDEX idx_family_photo_group_user ON family_photo (photo_group_id, users_id)'
);

PREPARE add_idx_family_photo_group_user_stmt
    FROM @add_idx_family_photo_group_user_sql;
EXECUTE add_idx_family_photo_group_user_stmt;
DEALLOCATE PREPARE add_idx_family_photo_group_user_stmt;

SET @drop_idx_family_photo_family_created_sql := IF(
    (
        SELECT COUNT(*)
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'family_photo'
          AND index_name = 'idx_family_photo_family_created'
    ) = 0,
    'SELECT 1',
    'ALTER TABLE family_photo DROP INDEX idx_family_photo_family_created'
);

PREPARE drop_idx_family_photo_family_created_stmt
    FROM @drop_idx_family_photo_family_created_sql;
EXECUTE drop_idx_family_photo_family_created_stmt;
DEALLOCATE PREPARE drop_idx_family_photo_family_created_stmt;

SET @drop_idx_family_photo_family_user_sql := IF(
    (
        SELECT COUNT(*)
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'family_photo'
          AND index_name = 'idx_family_photo_family_user'
    ) = 0,
    'SELECT 1',
    'ALTER TABLE family_photo DROP INDEX idx_family_photo_family_user'
);

PREPARE drop_idx_family_photo_family_user_stmt
    FROM @drop_idx_family_photo_family_user_sql;
EXECUTE drop_idx_family_photo_family_user_stmt;
DEALLOCATE PREPARE drop_idx_family_photo_family_user_stmt;

SET @family_photo_family_fk_name := (
    SELECT constraint_name
    FROM information_schema.key_column_usage
    WHERE table_schema = DATABASE()
      AND table_name = 'family_photo'
      AND column_name = 'family_id'
      AND referenced_table_name = 'family'
    LIMIT 1
);

SET @drop_family_photo_family_fk_sql := IF(
    @family_photo_family_fk_name IS NULL,
    'SELECT 1',
    CONCAT(
        'ALTER TABLE family_photo DROP FOREIGN KEY `',
        REPLACE(@family_photo_family_fk_name, '`', '``'),
        '`'
    )
);

PREPARE drop_family_photo_family_fk_stmt
    FROM @drop_family_photo_family_fk_sql;
EXECUTE drop_family_photo_family_fk_stmt;
DEALLOCATE PREPARE drop_family_photo_family_fk_stmt;

SET @family_photo_family_id_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'family_photo'
      AND column_name = 'family_id'
);

SET @drop_family_photo_family_id_sql := IF(
    @family_photo_family_id_exists = 0,
    'SELECT 1',
    'ALTER TABLE family_photo DROP COLUMN family_id'
);

PREPARE drop_family_photo_family_id_stmt
    FROM @drop_family_photo_family_id_sql;
EXECUTE drop_family_photo_family_id_stmt;
DEALLOCATE PREPARE drop_family_photo_family_id_stmt;
