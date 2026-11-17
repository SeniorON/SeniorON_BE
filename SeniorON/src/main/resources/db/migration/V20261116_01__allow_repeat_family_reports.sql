-- 외래키에 필요한 인덱스를 유지하면서 같은 신고자의 반복 신고를 허용한다.
ALTER TABLE family_photo_report
    ADD INDEX idx_family_photo_report_photo_reporter (
        family_photo_id,
        reporter_user_id
    ),
    DROP INDEX uk_family_photo_report_photo_reporter;

ALTER TABLE family_user_report
    ADD INDEX idx_family_user_report_reported_reporter (
        reported_user_id,
        reporter_user_id
    ),
    DROP INDEX uk_family_user_report_reported_reporter;
