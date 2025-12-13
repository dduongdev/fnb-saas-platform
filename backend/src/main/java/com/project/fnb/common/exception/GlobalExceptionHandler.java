package com.project.fnb.common.exception;

import com.project.fnb.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Xử lý lỗi Custom (Logic nghiệp vụ)
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException e) {
        return ResponseEntity.status(e.getErrorCode() >= 400 && e.getErrorCode() < 500 ? e.getErrorCode() : 400)
                .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    // 2. Xử lý lỗi Validation (Ví dụ: @NotNull, @NotBlank)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, message));
    }

    // 3. Xử lý lỗi không mong muốn (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnwantedException(Exception e) {
        e.printStackTrace();
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(500, "Lỗi hệ thống: " + e.getMessage()));
    }
}