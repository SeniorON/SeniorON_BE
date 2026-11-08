-- 관리자 신고 상세에서 사진이 삭제된 뒤에도 시니어 코드·역할·업로드일을 보여 주기 위해
-- 신고 시점의 시니어와 사진 업로드일을 저장한다.
ALTER TABLE family_photo_report
    ADD COLUMN senior_id BIGINT NULL,
    ADD COLUMN photo_uploaded_at DATETIME(6) NULL,
    ADD CONSTRAINT fk_family_photo_report_senior
        FOREIGN KEY (senior_id)
            REFERENCES seniors (senior_id)
            ON DELETE SET NULL;

-- 기존 신고: 사진이 남아 있으면 업로드일을 채운다.
UPDATE family_photo_report fpr
JOIN family_photo fp ON fp.family_photo_id = fpr.family_photo_id
SET fpr.photo_uploaded_at = fp.created_at;

-- 기존 신고: 신고자와 사진이 함께 연결된 시니어를 채운다. (신고자가 주담당자인 시니어 우선)
UPDATE family_photo_report fpr
SET fpr.senior_id = (
    SELECT fps.senior_id
    FROM family_photo_senior fps
    JOIN seniors s ON s.senior_id = fps.senior_id
    JOIN family_member fm ON fm.family_id = s.family_id
    WHERE fps.family_photo_id = fpr.family_photo_id
      AND fm.users_id = fpr.reporter_user_id
    ORDER BY (fm.manager_type = 'PRIMARY') DESC, fps.family_photo_senior_id
    LIMIT 1
)
WHERE fpr.family_photo_id IS NOT NULL;
