package com.example.senioron.global.apiPayload.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.senioron.global.apiPayload.code.BaseCode;
import com.example.senioron.global.apiPayload.code.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"status", "code", "message", "data"})
public class Response<T> {

    private final HttpStatus status;
    private final String code;
    private final String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final T data;

    public Response(boolean success, String code, String message) {
        this.status = success ? HttpStatus.OK : HttpStatus.UNAUTHORIZED; // 또는 프로젝트 기준에 맞게 설정
        this.code = code;
        this.message = message;
        this.data = null;
    }

    public static Response<Void> ok() {
        return new Response<>(ResultCode.OK.getStatus(), ResultCode.OK.getCode(), ResultCode.OK.getMessage(), null);
    }

    public static <T> Response<T> ok(T data) {
        return new Response<>(ResultCode.OK.getStatus(), ResultCode.OK.getCode(), ResultCode.OK.getMessage(), data);
    }

    public static <T> Response<T> ok(ResultCode resultCode, T data) {
        return new Response<>(resultCode.getStatus(), resultCode.getCode(), resultCode.getMessage(), data);
    }

    public static <T> Response<T> of(BaseCode code, T data){
        return new Response<>(code.getStatus(), code.getCode(), code.getMessage(), data);
    }

    public static <T> Response<T> fail(BaseCode code) {
        return new Response<>(code.getStatus(), code.getCode(), code.getMessage(), null);
    }

    public static <T> Response<T> fail(BaseCode code, T data) {
        return new Response<>(code.getStatus(), code.getCode(), code.getMessage(), data);
    }

}
