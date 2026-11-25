-- 기존 client_event_id 중복이 있으면 이 migration은 실패한다. 데이터를 자동 삭제/변경하지 않는다.
ALTER TABLE event
    ADD COLUMN preparation_status VARCHAR(32) NULL,
    ADD COLUMN preparation_reason VARCHAR(128) NULL,
    ADD COLUMN preparation_receiver_count INT NULL,
    ADD CONSTRAINT uk_event_user_client_event UNIQUE (triggered_users_id, client_event_id);

-- 이전 버전에 저장된 키는 당시 준비 결과를 복원할 수 없다. 재발송하지 않고 불명확한 결과임을 명시한다.
UPDATE event
SET preparation_status = 'NOT_DISPATCHED',
    preparation_reason = 'LEGACY_RESULT_UNAVAILABLE',
    preparation_receiver_count = 0
WHERE event_type = 'OUTING_RETURN' AND client_event_id IS NOT NULL;
