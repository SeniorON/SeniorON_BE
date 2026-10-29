-- 사진 공유가 시니어 단위(family_photo_senior)로 전환되어 포토그룹 구조를 제거한다.

-- 공유 대상 시니어가 없는 기존 사진은 더 이상 조회되지 않으므로 삭제한다.
-- family_photo_view, family_photo_group 은 ON DELETE CASCADE 로 함께 삭제된다.
DELETE FROM family_photo
WHERE NOT EXISTS (
    SELECT 1
    FROM family_photo_senior fps
    WHERE fps.family_photo_id = family_photo.family_photo_id
);

DROP TABLE IF EXISTS family_photo_group;

ALTER TABLE family_photo
    DROP FOREIGN KEY fk_family_photo_photo_group;

ALTER TABLE family_photo
    DROP INDEX idx_family_photo_group_created,
    DROP INDEX idx_family_photo_group_user,
    DROP COLUMN photo_group_id;

DROP TABLE IF EXISTS photo_group_family;

DROP TABLE IF EXISTS photo_group;
