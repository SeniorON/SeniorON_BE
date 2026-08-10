package com.example.senioron.domain.companion.controller;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.response.Response;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
        assignableTypes =
                CompanionVoiceController.class
)
public class CompanionVoiceExceptionHandler {

    @ExceptionHandler(
            MaxUploadSizeExceededException.class
    )
    public ResponseEntity<Response<Void>>
    handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception
    ) {
        return ResponseEntity
                .status(
                        ErrorCode
                                .COMPANION_AUDIO_SIZE_EXCEEDED
                                .getStatus()
                )
                .body(
                        Response.fail(
                                ErrorCode
                                        .COMPANION_AUDIO_SIZE_EXCEEDED
                        )
                );
    }
}