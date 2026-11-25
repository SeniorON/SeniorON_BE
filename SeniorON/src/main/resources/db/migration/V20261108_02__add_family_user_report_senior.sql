-- 관리자 게시자 신고 상세에서 게시자·신고자 역할과 시니어 코드를 보여 주기 위해
-- 신고 시점의 시니어를 저장한다. (사용자는 여러 가족에 속할 수 있다)
ALTER TABLE family_user_report
    ADD COLUMN senior_id BIGINT NULL,
    ADD CONSTRAINT fk_family_user_report_senior
        FOREIGN KEY (senior_id)
            REFERENCES seniors (senior_id)
            ON DELETE SET NULL;

-- 기존 신고: 신고자와 사진이 함께 연결된 시니어를 채운다. (신고자가 주담당자인 시니어 우선)
-- 사진이 삭제돼 확인할 수 없으면 null로 둔다.
UPDATE family_user_report fur
SET fur.senior_id = (
    SELECT fps.senior_id
    FROM family_photo_senior fps
    JOIN seniors s ON s.senior_id = fps.senior_id
    JOIN family_member fm ON fm.family_id = s.family_id
    WHERE fps.family_photo_id = fur.family_photo_id
      AND fm.users_id = fur.reporter_user_id
    ORDER BY (fm.manager_type = 'PRIMARY') DESC, fps.family_photo_senior_id
    LIMIT 1
)
WHERE fur.family_photo_id IS NOT NULL;
