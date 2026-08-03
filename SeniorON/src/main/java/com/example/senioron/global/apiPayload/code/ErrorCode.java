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
    INVALID_PASSWORD_RESET_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "USER400_3", "인증번호가 올바르지 않거나 만료되었습니다."),
    NAME_NOT_CHANGED(HttpStatus.BAD_REQUEST, "USER400_4", "현재 사용 중인 이름과 동일해요"),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "USER400_5", "현재 비밀번호가 일치하지 않아요"),
    NEW_PASSWORD_CONFIRMATION_MISMATCH(HttpStatus.BAD_REQUEST, "USER400_6", "새 비밀번호가 일치하지 않아요"),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "USER400_7", "현재 사용 중인 비밀번호와 동일해요"),
    PROFILE_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "USER400_8", "프로필 이미지를 선택해 주세요"),
    UNSUPPORTED_PROFILE_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "USER400_9", "지원하지 않는 이미지 형식이에요"),
    PROFILE_IMAGE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "USER400_10", "프로필 이미지 크기가 너무 커요"),
    PROFILE_IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "USER500_1", "프로필 이미지 업로드에 실패했어요"),
    LOGIN_ID_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404_1", "일치하는 아이디를 찾지 못했어요"),
    ACCOUNT_RECOVERY_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404_2", "일치하는 계정을 찾지 못했어요"),
    SIGNUP_EMAIL_VERIFICATION_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404_3", "회원가입 이메일 인증 요청 이력이 없습니다."),
    INVALID_SIGNUP_EMAIL_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "USER400_11", "회원가입 이메일 인증 코드가 올바르지 않습니다."),
    EXPIRED_SIGNUP_EMAIL_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "USER400_12", "회원가입 이메일 인증 코드가 만료되었습니다."),
    SIGNUP_EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "USER409_2", "이미 인증이 완료된 이메일입니다."),
    INVALID_FIREBASE_ID_TOKEN(HttpStatus.UNAUTHORIZED, "USER401_1", "Firebase ID 토큰이 유효하지 않습니다."),
    FIREBASE_AUTH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "USER503_1", "Firebase 인증을 사용할 수 없습니다."),
    GOOGLE_LOGIN_CONFLICT(HttpStatus.CONFLICT, "USER409_3", "구글 로그인 처리 중 계정 충돌이 발생했습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "USER401_2", "Refresh Token이 유효하지 않습니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "USER401_3", "Refresh Token이 만료되었습니다."),
    REFRESH_TOKEN_DEVICE_MISMATCH(HttpStatus.UNAUTHORIZED, "USER401_4", "Refresh Token의 기기 정보가 일치하지 않습니다."),

    // Email Error
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EMAIL500", "이메일 발송에 실패했습니다."),

    // Device Error
    SENIOR_DEVICE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "DEVICE_403", "시니어 기기 정보 갱신 권한이 없습니다."),

    // Home Error
    HOME_BUTTON_NOT_FOUND(HttpStatus.NOT_FOUND, "HOME404", "버튼을 찾을 수 없습니다."),
    BUTTON_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "HOME4001", "존재하지 않는 버튼 옵션입니다."),
    HOME_BUTTON_ALREADY_EXISTS(HttpStatus.CONFLICT, "HOME4002", "이미 추가된 홈 버튼입니다."),
    INVALID_HOME_BUTTON_REQUEST(HttpStatus.BAD_REQUEST, "HOME_4002", "현재 홈 버튼 전체를 요청해야 합니다."),
    DUPLICATE_HOME_BUTTON_ID(HttpStatus.BAD_REQUEST, "HOME_4003", "중복된 홈 버튼이 있습니다."),
    DUPLICATE_HOME_BUTTON_ORDER(HttpStatus.BAD_REQUEST, "HOME_4004", "중복된 홈 버튼 순서가 있습니다."),
    INVALID_HOME_BUTTON_ORDER(HttpStatus.BAD_REQUEST, "HOME_4005", "홈 버튼 순서는 1부터 연속되어야 합니다."),
    HOME_SETTING_ACCESS_DENIED(HttpStatus.FORBIDDEN, "HOME403", "주 담당 자녀만 홈 설정을 수정할 수 있습니다."),
    SENIOR_HOME_ACCESS_DENIED(HttpStatus.FORBIDDEN, "HOME404", "부모 역할의 사용자만 부모님 홈을 조회할 수 있습니다."),
    CHILD_HOME_ACCESS_DENIED(HttpStatus.FORBIDDEN, "HOME405", "자녀 역할의 사용자만 자녀 홈을 조회할 수 있습니다."),
    FAMILY_NOT_CONNECTED(HttpStatus.BAD_REQUEST, "HOME406", "연결된 가족이 없습니다."),
    PRIMARY_MANAGER_NOT_FOUND(HttpStatus.NOT_FOUND, "HOME407", "가족의 주 담당자를 찾을 수 없습니다."),
    SENIOR_NOT_FOUND(HttpStatus.NOT_FOUND, "SENIOR404", "등록된 시니어 정보를 찾을 수 없습니다."),
    HOME_BUTTON_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "HOME_4008", "노래 카드 선택 여부에 따른 홈 버튼 최대 개수를 초과했습니다."),
    WEATHER_DATA_NOT_SUPPORTED(HttpStatus.INTERNAL_SERVER_ERROR, "WEATHER5001", "지원하지 않는 날씨 상태입니다."),
    WEATHER_API_CALL_FAILED(HttpStatus.BAD_GATEWAY, "WEATHER5021", "날씨 정보를 불러오는 데 실패했습니다."),
    WEATHER_DATA_NOT_FOUND(HttpStatus.BAD_GATEWAY, "WEATHER5022", "날씨 응답 데이터가 올바르지 않습니다."),
    INVALID_LOCATION_COORDINATES(HttpStatus.BAD_REQUEST, "WEATHER4001", "유효하지 않은 위치 좌표입니다."),
    HOME_BUTTON_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "HOME_4012", "버튼 이름은 6자 이하여야 합니다."),
    HOME_BUTTON_MINIMUM_NOT_MET(HttpStatus.BAD_REQUEST, "HOME4009", "홈 버튼은 최소 7개 이상이어야 합니다."),
    REQUIRED_HOME_BUTTON_MISSING(HttpStatus.BAD_REQUEST, "HOME_4011", "말벗, 복약, 사진, 긴급알림 버튼은 반드시 포함되어야 합니다."),

    // Senior Error
    CUSTOM_RELATION_REQUIRED(HttpStatus.BAD_REQUEST, "SENIOR4001", "직접 작성 관계를 입력해 주세요."),
    SENIOR_ALREADY_EXISTS(HttpStatus.CONFLICT, "SENIOR409", "이미 등록된 시니어 정보가 있습니다."),

    // Family Error
    FAMILY_NOT_FOUND(HttpStatus.NOT_FOUND, "FAMILY404", "가족 정보를 찾을 수 없습니다."),
    INVALID_FAMILY_CODE(HttpStatus.BAD_REQUEST, "FAMILY400", "유효하지 않은 가족 코드입니다."),
    FAMILY_MEMBER_REMOVE_FORBIDDEN(HttpStatus.FORBIDDEN, "FAMILY403", "가족 구성원을 제외할 권한이 없습니다."),
    FAMILY_CODE_CREATE_PARENT_FORBIDDEN(HttpStatus.FORBIDDEN, "FAMILY403_1", "부모 계정은 가족 공유코드를 생성할 수 없습니다."),
    FAMILY_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "FAMILY404_1", "해당 가족 구성원을 찾을 수 없습니다."),
    CANNOT_REMOVE_SELF(HttpStatus.BAD_REQUEST, "FAMILY400_1", "주 담당자는 자기 자신을 가족에서 제외할 수 없습니다."),
    CANNOT_CHANGE_PRIMARY_TO_SELF(HttpStatus.BAD_REQUEST, "FAMILY400_2", "본인은 주 담당자 변경 대상으로 선택할 수 없습니다."),
    FAMILY_PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "FAMILY_PHOTO404", "가족 사진을 찾을 수 없습니다."),
    FAMILY_PHOTO_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "FAMILY_PHOTO403", "해당 가족 사진을 삭제할 권한이 없습니다."),
    PRIMARY_MANAGER_MUST_BE_CHILD(HttpStatus.BAD_REQUEST, "FAMILY400_3", "주 담당자는 자녀 계정만 지정할 수 있습니다."),

    // Companion Error
    COMPANION_PARENT_ONLY(HttpStatus.FORBIDDEN, "COMPANION403_1", "부모 계정만 이용할 수 있습니다."),
    COMPANION_CONVERSATION_FORBIDDEN(HttpStatus.FORBIDDEN, "COMPANION403_2", "해당 대화에 접근할 수 없습니다."),
    COMPANION_CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "COMPANION404_1", "대화를 찾을 수 없습니다."),
    COMPANION_CONVERSATION_ENDED(HttpStatus.CONFLICT, "COMPANION409_1", "이미 종료된 대화입니다."),
    COMPANION_ENCRYPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "COMPANION500_1", "대화 내용을 안전하게 처리하지 못했습니다."),
    COMPANION_TURN_NOT_FOUND(HttpStatus.NOT_FOUND, "COMPANION404_2", "대화 턴을 찾을 수 없습니다."),
    COMPANION_AUDIO_REQUIRED(HttpStatus.BAD_REQUEST, "COMPANION400_1", "음성 파일을 첨부해 주세요."),
    COMPANION_AUDIO_FORMAT_UNSUPPORTED(HttpStatus.BAD_REQUEST, "COMPANION400_2", "지원하지 않는 음성 파일 형식입니다."),
    COMPANION_AUDIO_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "COMPANION413_1", "음성 파일 크기는 10MB를 초과할 수 없습니다."),
    COMPANION_SPEECH_NOT_RECOGNIZED(HttpStatus.UNPROCESSABLE_ENTITY, "COMPANION422_1", "음성을 인식하지 못했습니다. 다시 말씀해 주세요."),
    COMPANION_STT_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "COMPANION502_1", "음성 인식 서비스를 이용할 수 없습니다."),
    COMPANION_STT_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "COMPANION503_1", "음성 인식 서비스가 설정되지 않았습니다."),
    COMPANION_STT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "COMPANION504_1", "음성 인식 요청 시간이 초과되었습니다."),
    COMPANION_LLM_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "COMPANION503_2", "대화 생성 서비스가 설정되지 않았습니다."),
    COMPANION_LLM_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "COMPANION502_2", "대화 생성 서비스를 이용할 수 없습니다."),
    COMPANION_LLM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "COMPANION504_2", "대화 생성 요청 시간이 초과되었습니다."),
    COMPANION_LLM_EMPTY_RESPONSE(HttpStatus.BAD_GATEWAY, "COMPANION502_3", "대화 생성 결과가 비어 있습니다."),
    COMPANION_PROMPT_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "COMPANION500_2", "말벗 시스템 프롬프트를 불러오지 못했습니다."),

    // Photo Error
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST,"IMAGE400_1", "JPG, PNG, WEBP 형식의 이미지만 업로드할 수 있습니다."),

    // Medication Error
    MEDICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "MED404", "복약 정보를 찾을 수 없습니다."),

    // Health Error
    HEALTH_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "HEALTH404", "건강 기록을 찾을 수 없습니다."),

    // Hospital Error
    HOSPITAL_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "HOSPITAL404", "병원 일정을 찾을 수 없습니다."),

    // Device Error
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "DEVICE404", "기기를 찾을 수 없습니다."),
    PARENT_DEVICE_OFFLINE(HttpStatus.BAD_REQUEST, "DEVICE400", "부모님 기기가 오프라인 상태라 설정을 변경할 수 없습니다."),
    DEVICE_IDENTIFIER_REQUIRED(HttpStatus.BAD_REQUEST, "DEVICE400_1", "기기 식별자(deviceIdentifier)가 필요합니다."),
    DEVICE_NOT_CONNECTED(HttpStatus.NOT_FOUND, "DEVICE405", "연결된 기기가 없습니다."),
    DEVICE_DISCONNECT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "DEVICE403", "기기 연결을 해제할 권한이 없습니다."),
    
    // Receipt Error
    RECEIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "RECEIPT404", "영수증을 찾을 수 없습니다."),
    
    // Inactivity Error
    INACTIVITY_SETTING_NOT_FOUND(HttpStatus.NOT_FOUND, "INACTIVITY404", "비활동 감지 설정을 찾을 수 없습니다."),

    // Event Error
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT404", "이벤트를 찾을 수 없습니다."),
    RISK_LINK_CHECK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "EVENT503", "위험 링크 검사에 실패했습니다. 잠시 후 다시 시도해주세요."),

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
