-- 시니어 프로필이 등록되기 전 탈퇴한 PARENT도 탈퇴 공지를 남길 수 있도록 한다.
-- senior_id는 프로필이 존재한 경우에만 식별자 스냅샷으로 사용한다.
ALTER TABLE senior_withdrawal_notice
    MODIFY COLUMN senior_id BIGINT NULL;
