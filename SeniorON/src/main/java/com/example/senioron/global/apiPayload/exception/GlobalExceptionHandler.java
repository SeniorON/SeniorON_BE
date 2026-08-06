package com.example.senioron.global.apiPayload.exception;


import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.response.Response;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 *  Exception 처리기
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // ===================== 사용자 정의 예외 ======================

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response<Void>> handleBusinessException(BusinessException ex) {
        log.warn(
                "[GLOBAL_EXCEPTION] BusinessException errorCode={} httpStatus={} message={}",
                ex.getErrorCode(),
                ex.getStatus(),
                sanitizeMessage(ex.getMessage())
        );
        return ResponseEntity
                .status(ex.getStatus())
                .body(Response.fail(ex.getCode()));
    }

    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<Response<Void>> handleGlobalException(GlobalException ex) {
        log.warn("GlobalException: {}", ex.getMessage());
        return ResponseEntity
                .status(ex.getStatus())
                .body(Response.fail(ex.getCode()));
    }

    // ===================== Validation / 변환 오류 ======================

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        log.warn(
                "[GLOBAL_EXCEPTION] MethodArgumentNotValidException exceptionClass={} message={} fieldErrors={}",
                ex.getClass().getName(),
                sanitizeMessage(ex.getMessage()),
                errors
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Response.fail(ErrorCode.BAD_REQUEST, errors));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        log.warn(
                "[GLOBAL_EXCEPTION] TypeMismatchException exceptionClass={} message={} rootCauseClass={} rootCauseMessage={}",
                ex.getClass().getName(),
                sanitizeMessage(ex.getMessage()),
                rootCauseClassName(ex),
                rootCauseMessage(ex)
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Response.fail(ErrorCode.BAD_REQUEST));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        log.warn(
                "[GLOBAL_EXCEPTION] HttpMessageNotReadableException exceptionClass={} message={} rootCauseClass={} rootCauseMessage={}",
                ex.getClass().getName(),
                sanitizeMessage(ex.getMessage()),
                rootCauseClassName(ex),
                rootCauseMessage(ex)
        );

        Throwable cause = ex.getCause();

        if (cause instanceof InvalidFormatException invalidFormatException
                && invalidFormatException.getTargetType() == LocalDate.class) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Response.fail(ErrorCode.INVALID_DATE_FORMAT));
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Response.fail(ErrorCode.BAD_REQUEST));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            IncorrectResultSizeDataAccessException.class,
            InvalidDataAccessApiUsageException.class,
            NoSuchElementException.class
    })
    public ResponseEntity<Object> handleBadRequestExceptions(Exception ex) {
        log.warn(
                "[GLOBAL_EXCEPTION] BadRequestException exceptionClass={} message={} rootCauseClass={} rootCauseMessage={} sqlState={} constraintName={}",
                ex.getClass().getName(),
                sanitizeMessage(ex.getMessage()),
                rootCauseClassName(ex),
                rootCauseMessage(ex),
                sqlState(ex),
                constraintName(ex)
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Response.fail(ErrorCode.BAD_REQUEST));
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            DataIntegrityViolationException.class
    })
    public ResponseEntity<Object> handleDatabaseExceptions(Exception ex) {
        log.error(
                "[GLOBAL_EXCEPTION] DatabaseException exceptionClass={} message={} rootCauseClass={} rootCauseMessage={} sqlState={} constraintName={}",
                ex.getClass().getName(),
                sanitizeMessage(ex.getMessage()),
                rootCauseClassName(ex),
                rootCauseMessage(ex),
                sqlState(ex),
                constraintName(ex)
        );
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.fail(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    // ===================== 시스템 오류 (500) ======================

    @ExceptionHandler({
            BeanCreationException.class,
            ClassCastException.class,
            HttpMessageConversionException.class,
            JpaSystemException.class,
            NullPointerException.class,
            UnsatisfiedDependencyException.class
    })
    public ResponseEntity<Object> handleServerExceptions(Exception ex) {
        log.error("Internal server error: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.fail(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    // ===================== 예기치 못한 예외 ======================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleUnknownException(Exception ex) {
        log.error("Unhandled exception occurred", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.fail(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        log.warn(
                "[GLOBAL_EXCEPTION] ServletRequestBindingException exceptionClass={} message={} rootCauseClass={} rootCauseMessage={}",
                ex.getClass().getName(),
                sanitizeMessage(ex.getMessage()),
                rootCauseClassName(ex),
                rootCauseMessage(ex)
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Response.fail(ErrorCode.BAD_REQUEST));
    }

    private String rootCauseClassName(Throwable throwable) {
        Throwable rootCause = rootCause(throwable);
        return rootCause == null ? null : rootCause.getClass().getName();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable rootCause = rootCause(throwable);
        return rootCause == null ? null : sanitizeMessage(rootCause.getMessage());
    }

    private String sqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }

    private String constraintName(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException) {
                return constraintViolationException.getConstraintName();
            }
            current = current.getCause();
        }
        return null;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        Throwable rootCause = throwable;
        while (current != null) {
            rootCause = current;
            current = current.getCause();
        }
        return rootCause;
    }

    private String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }

        String sanitized = message
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[REDACTED_EMAIL]")
                .replaceAll("(?i)Bearer\\s+\\S+", "Bearer [REDACTED_TOKEN]")
                .replaceAll("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+", "[REDACTED_JWT]");

        int maxLength = 500;
        if (sanitized.length() > maxLength) {
            return sanitized.substring(0, maxLength) + "...";
        }
        return sanitized;
    }
}
