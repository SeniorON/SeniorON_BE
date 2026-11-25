package com.example.senioron.global.apiPayload.exception;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 *  시스템 전역 예외 발생기 (설정 오류)
 */
@Getter
public class GlobalException extends RuntimeException {

  private final ErrorCode code;

  public GlobalException(ErrorCode code) {
    super(code.getMessage());
    this.code = code;
  }

  public ErrorCode getCode() {
    return code;
  }

  public HttpStatus getStatus() {
    return this.code.getStatus();
  }

  public ErrorCode getReason() {
    return this.code;
  }
}
