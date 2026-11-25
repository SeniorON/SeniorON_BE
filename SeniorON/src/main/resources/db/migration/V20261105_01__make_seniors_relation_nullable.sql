-- 시니어와의 관계 정보는 family_member에서 관리한다.
-- 기존 seniors.relation 값과 custom_relation 값은 보존하고, 새 프로필 생성 시 NULL을 허용한다.
ALTER TABLE seniors
    MODIFY COLUMN relation VARCHAR(255) NULL;
