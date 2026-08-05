-- 같은 기기(device_identifier)에 다른 계정으로 로그인하면 새 row가 계속 쌓여
-- 이전 계정의 FCM 토큰이 남아있는 문제를 막기 위해, 유니크 제약을
-- (users_id, device_identifier) 조합에서 device_identifier 단독으로 변경한다.

-- 1) 같은 device_identifier로 중복된 row 정리: 가장 최근에 생성된(device_id가 큰) row만 남긴다.
DELETE d1 FROM device d1
INNER JOIN device d2
    ON d1.device_identifier = d2.device_identifier
    AND d1.device_id < d2.device_id
WHERE d1.device_identifier IS NOT NULL;

-- 2) 기존 (users_id, device_identifier) 유니크 제약 제거 (baseline 스키마라 실제 제약명을 모르므로 동적으로 조회)
SET @old_constraint_name = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'device'
      AND CONSTRAINT_TYPE = 'UNIQUE'
    LIMIT 1
);

SET @drop_old_constraint_sql = CONCAT('ALTER TABLE device DROP INDEX `', @old_constraint_name, '`');
PREPARE drop_old_constraint_stmt FROM @drop_old_constraint_sql;
EXECUTE drop_old_constraint_stmt;
DEALLOCATE PREPARE drop_old_constraint_stmt;

-- 3) device_identifier 단독 유니크 제약 추가
ALTER TABLE device
    ADD CONSTRAINT uk_device_identifier UNIQUE (device_identifier);
