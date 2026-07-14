package com.example.senioron.global.apiPayload.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode implements BaseCode {

    // Common Error
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러입니다. 관리자에게 문의하세요."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "금지된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404", "찾을 수 없는 요청입니다."),

    // User Error
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER409", "중복된 이메일입니다."),
    USER_NOT_AUTHENTICATED(HttpStatus.UNAUTHORIZED, "USER401", "로그인 하지 않았습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "USER409_1", "이미 사용 중인 아이디입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "USER400_1", "비밀번호가 일치하지 않습니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "USER400_2", "비밀번호가 올바르지 않습니다."),

    // Home Error
    HOME_BUTTON_NOT_FOUND(HttpStatus.NOT_FOUND, "HOME404", "홈 버튼을 찾을 수 없습니다."),
    BUTTON_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "HOME4001", "존재하지 않는 버튼 옵션입니다."),
    HOME_BUTTON_ALREADY_EXISTS(HttpStatus.CONFLICT, "HOME4002", "이미 추가된 홈 버튼입니다."),
    INVALID_HOME_BUTTON_REQUEST(HttpStatus.BAD_REQUEST, "HOME_4002", "현재 홈 버튼 전체를 요청해야 합니다."),
    DUPLICATE_HOME_BUTTON_ID(HttpStatus.BAD_REQUEST, "HOME_4003", "중복된 홈 버튼 ID가 있습니다."),
    DUPLICATE_HOME_BUTTON_ORDER(HttpStatus.BAD_REQUEST, "HOME_4004", "중복된 홈 버튼 순서가 있습니다."),
    INVALID_HOME_BUTTON_ORDER(HttpStatus.BAD_REQUEST, "HOME_4005", "홈 버튼 순서는 1부터 연속되어야 합니다."),

    // Senior Error
    CUSTOM_RELATION_REQUIRED(HttpStatus.BAD_REQUEST, "SENIOR4001", "직접 작성 관계를 입력해 주세요."),

    // Family Error
    FAMILY_NOT_FOUND(HttpStatus.NOT_FOUND, "FAMILY404", "가족 정보를 찾을 수 없습니다."),
    INVALID_FAMILY_CODE(HttpStatus.BAD_REQUEST, "FAMILY400", "유효하지 않은 가족 코드입니다."),
    FAMILY_MEMBER_REMOVE_FORBIDDEN(HttpStatus.FORBIDDEN, "FAMILY403", "가족 구성원을 제외할 권한이 없습니다."),
    FAMILY_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "FAMILY404_1", "해당 가족 구성원을 찾을 수 없습니다."),
    CANNOT_REMOVE_SELF(HttpStatus.BAD_REQUEST, "FAMILY400_1", "주 담당자는 자기 자신을 가족에서 제외할 수 없습니다."),
    CANNOT_CHANGE_PRIMARY_TO_SELF(HttpStatus.BAD_REQUEST, "FAMILY400_2", "본인은 주 담당자 변경 대상으로 선택할 수 없습니다."),

    // Medication Error
    MEDICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "MED404", "복약 정보를 찾을 수 없습니다."),

    // Health Error
    HEALTH_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "HEALTH404", "건강 기록을 찾을 수 없습니다."),

    // Hospital Error
    HOSPITAL_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "HOSPITAL404", "병원 일정을 찾을 수 없습니다."),

    // Device Error
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "DEVICE404", "기기를 찾을 수 없습니다."),
    
    // Receipt Error
    RECEIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "RECEIPT404", "영수증을 찾을 수 없습니다."),
    
    // Inactivity Error
    INACTIVITY_SETTING_NOT_FOUND(HttpStatus.NOT_FOUND, "INACTIVITY404", "비활동 감지 설정을 찾을 수 없습니다."),

    // Event Error
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT404", "이벤트를 찾을 수 없습니다."),

    // Notification Error
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTI404", "알림을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
