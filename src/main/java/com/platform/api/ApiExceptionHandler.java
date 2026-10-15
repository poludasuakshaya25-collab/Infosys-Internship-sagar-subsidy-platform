package com.platform.api;

import com.platform.api.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestControllerAdvice(basePackages = "com.platform.api")
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleStatus(
            ResponseStatusException ex, HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        String error = ex.getStatusCode().toString();
        return ResponseEntity.status(ex.getStatusCode()).body(
                new ApiErrorResponse(LocalDateTime.now(), status, error,
                        ex.getReason(), request.getRequestURI())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        return ResponseEntity.internalServerError().body(
                new ApiErrorResponse(LocalDateTime.now(), 500,
                        "INTERNAL_SERVER_ERROR",
                        "The API could not complete the request.",
                        request.getRequestURI())
        );
    }
}
