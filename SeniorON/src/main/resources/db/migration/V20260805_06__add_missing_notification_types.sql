-- notification.notification_type 네이티브 ENUM에 RISK_LINK, OUTING_RETURN이
-- 빠져 있어서, 위험링크(위험 확정 시)/외출·귀가 알림을 저장할 때
-- DataIntegrityViolationException이 발생해 API가 400으로 실패하던 문제를 수정한다.
-- NotificationType.java의 모든 값(SOS, INACTIVITY, RISK_LINK, OUTING_RETURN, HEALTH, SYSTEM)을 반영한다.
ALTER TABLE notification
    MODIFY COLUMN notification_type
    ENUM('SOS','INACTIVITY','RISK_LINK','OUTING_RETURN','HEALTH','SYSTEM') DEFAULT NULL;
