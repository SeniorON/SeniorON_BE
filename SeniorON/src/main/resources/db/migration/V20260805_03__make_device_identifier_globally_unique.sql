-- 같은 기기(device_identifier)에 다른 계정으로 로그인하면 새 row가 계속 쌓여
-- 이전 계정의 FCM 토큰이 남아있는 문제를 막기 위해, 유니크 제약을
-- (users_id, device_identifier) 조합에서 device_identifier 단독으로 변경한다.

-- 1) 같은 device_identifier로 중복된 row 정리: 가장 최근에 생성된(device_id가 큰) row만 남긴다.
DELETE d1 FROM device d1
INNER JOIN device d2
    ON d1.device_identifier = d2.device_identifier
    AND d1.device_id < d2.device_id
WHERE d1.device_identifier IS NOT NULL;

-- 2) 기존 (users_id, device_identifier) 유니크 제약 제거.
-- baseline 스키마라 실제 제약명을 모르므로, TABLE_CONSTRAINTS에서 임의로 하나를 집는 대신
-- STATISTICS에서 (users_id, device_identifier) 순서로 구성된 unique index를 정확히 찾는다.
-- 매칭되는 인덱스가 없으면 아무 것도 지우지 않고 3번 단계로 넘어간다.
SET @old_index_name = (
    SELECT s1.INDEX_NAME
    FROM information_schema.STATISTICS s1
    JOIN information_schema.STATISTICS s2
        ON s2.TABLE_SCHEMA = s1.TABLE_SCHEMA
        AND s2.TABLE_NAME = s1.TABLE_NAME
        AND s2.INDEX_NAME = s1.INDEX_NAME
    WHERE s1.TABLE_SCHEMA = DATABASE()
      AND s1.TABLE_NAME = 'device'
      AND s1.NON_UNIQUE = 0
      AND s1.SEQ_IN_INDEX = 1
      AND s1.COLUMN_NAME = 'users_id'
      AND s2.SEQ_IN_INDEX = 2
      AND s2.COLUMN_NAME = 'device_identifier'
    LIMIT 1
);

SET @drop_old_constraint_sql = IF(
    @old_index_name IS NOT NULL,
    CONCAT('ALTER TABLE device DROP INDEX `', @old_index_name, '`'),
    'DO 0'
);
PREPARE drop_old_constraint_stmt FROM @drop_old_constraint_sql;
EXECUTE drop_old_constraint_stmt;
DEALLOCATE PREPARE drop_old_constraint_stmt;

-- 3) device_identifier 단독 유니크 제약 추가
ALTER TABLE device
    ADD CONSTRAINT uk_device_identifier UNIQUE (device_identifier);
