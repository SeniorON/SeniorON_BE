-- 같은 원본 키를 재등록할 수 있으므로 사진 ID가 남아 있는 신고만 묶는다.
-- 사진이 삭제되어 ID가 NULL인 신고는 검토 이력으로 보존한다.
UPDATE family_photo_report AS first_report
JOIN (
    SELECT MIN(family_photo_report_id) AS first_id,
           MAX(hidden) AS should_hide
    FROM family_photo_report
    WHERE family_photo_id IS NOT NULL
    GROUP BY family_photo_id, reporter_user_id
    HAVING COUNT(*) > 1
) AS duplicates
    ON duplicates.first_id = first_report.family_photo_report_id
SET first_report.hidden = duplicates.should_hide;

DELETE later_report
FROM family_photo_report AS later_report
JOIN family_photo_report AS earlier_report
    ON earlier_report.family_photo_id = later_report.family_photo_id
    AND earlier_report.reporter_user_id = later_report.reporter_user_id
    AND earlier_report.family_photo_report_id < later_report.family_photo_report_id
WHERE later_report.family_photo_id IS NOT NULL;

-- 게시자 신고는 신고 화면을 연 사진과 관계없이 같은 게시자를 기준으로 묶는다.
UPDATE family_user_report AS first_report
JOIN (
    SELECT MIN(family_user_report_id) AS first_id,
           MAX(hidden) AS should_hide
    FROM family_user_report
    GROUP BY reported_user_id, reporter_user_id
    HAVING COUNT(*) > 1
) AS duplicates
    ON duplicates.first_id = first_report.family_user_report_id
SET first_report.hidden = duplicates.should_hide;

DELETE later_report
FROM family_user_report AS later_report
JOIN family_user_report AS earlier_report
    ON earlier_report.reported_user_id = later_report.reported_user_id
    AND earlier_report.reporter_user_id = later_report.reporter_user_id
    AND earlier_report.family_user_report_id < later_report.family_user_report_id;

-- 외래키에 필요한 컬럼 순서를 유지하면서 기존 인덱스를 교체한다.
ALTER TABLE family_photo_report
    ADD UNIQUE INDEX uk_family_photo_report_photo_reporter (
        family_photo_id,
        reporter_user_id
    ),
    DROP INDEX idx_family_photo_report_photo_reporter;

ALTER TABLE family_user_report
    ADD UNIQUE INDEX uk_family_user_report_reported_reporter (
        reported_user_id,
        reporter_user_id
    ),
    DROP INDEX idx_family_user_report_reported_reporter;
