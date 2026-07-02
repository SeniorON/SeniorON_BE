package com.example.senioron.global.apiPayload.exception;

import com.example.senioron.global.apiPayload.code.BaseCode;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;


/**
 *  비즈니스상 예외 발생기
 */
@Getter
public class BusinessException extends RuntimeException{

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BaseCode getCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return errorCode.getStatus();
    }

    public String getErrorCode() {
        return errorCode.getCode();
    }

    public String getErrorMessage() {
        return errorCode.getMessage();
    }
}
