package com.tradepulse.auth.exception;

import com.tradepulse.common.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(AuthException ex,
                                                              HttpServletRequest req) {
        log.warn("Auth error on {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResponse.of(ex.getStatus().value(), ex.getStatus().getReasonPhrase(),
                        ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest req) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors().stream()
                .map(f -> new ErrorResponse.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();

        log.warn("Validation failed on {}: {}", req.getRequestURI(), fieldErrors);

        ErrorResponse body = ErrorResponse.withFieldErrors(
                HttpStatus.BAD_REQUEST.value(), "Validation Failed",
                "Request validation failed", req.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles malformed JSON bodies (e.g. trailing comma, missing quotes, unexpected token).
     * JsonParseException is wrapped inside HttpMessageNotReadableException by Spring.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex,
                                                              HttpServletRequest req) {
        log.warn("Malformed request body on {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad Request",
                        "Request body is malformed or contains invalid JSON", req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex,
                                                           HttpServletRequest req) {
        log.error("Unexpected error on {}", req.getRequestURI(), ex);
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of(500, "Internal Server Error",
                        "An unexpected error occurred", req.getRequestURI()));
    }
}
