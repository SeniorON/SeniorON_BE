package com.example.senioron.global.apiPayload.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ResultCode implements BaseCode {

    OK(HttpStatus.OK, "COMMON_200", "성공적으로 처리되었습니다."),
    CREATED(HttpStatus.CREATED, "COMMON_201", "성공적으로 생성되었습니다."),
    NO_CONTENT(HttpStatus.NO_CONTENT, "COMMON_204", "성공적으로 삭제되었습니다."),
    FAMILY_PHOTO_ALREADY_REPORTED(HttpStatus.OK, "FAMILY_PHOTO200_1", "이미 신고한 사진이에요."),
    FAMILY_PHOTO_REPORTED_AND_HIDDEN(HttpStatus.OK, "FAMILY_PHOTO200_2", "이미 신고한 사진이에요. 사진은 숨겼어요."),
    FAMILY_UPLOADER_ALREADY_REPORTED(HttpStatus.OK, "FAMILY_PHOTO200_3", "이미 신고한 게시자예요."),
    FAMILY_UPLOADER_REPORTED_AND_HIDDEN(HttpStatus.OK, "FAMILY_PHOTO200_4", "이미 신고한 게시자예요. 이 게시자의 사진은 숨겼어요.");

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
