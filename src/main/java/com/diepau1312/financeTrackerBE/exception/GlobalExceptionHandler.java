package com.diepau1312.financeTrackerBE.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Xử lý khi plan không đủ → 403
    @ExceptionHandler(PlanUpgradeRequiredException.class)
    public ResponseEntity<?> handlePlanUpgrade(PlanUpgradeRequiredException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error",       "PLAN_UPGRADE_REQUIRED",
                        "requiredPlan", ex.getRequiredPlan(),
                        "message",     ex.getMessage(),
                        "upgradeUrl",  "/pricing"
                ));
    }

    // Xử lý lỗi chung — không lộ stack trace ra ngoài
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error",   "INTERNAL_ERROR",
                        "message", "Đã có lỗi xảy ra, vui lòng thử lại"
                ));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<?> handleAuth(AuthException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "error",   "AUTH_ERROR",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        // Lấy lỗi đầu tiên trong danh sách lỗi validation
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("Dữ liệu không hợp lệ");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error",   "VALIDATION_ERROR",
                        "message", message
                ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntime(RuntimeException ex) {
        // Phân biệt lỗi "không tìm thấy" vs lỗi "không có quyền"
        String message = ex.getMessage();

        if (message != null && message.contains("Không có quyền")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORBIDDEN", "message", message));
        }

        if (message != null && message.contains("Không tìm thấy")) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", message));
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "INTERNAL_ERROR",
                        "message", "Đã có lỗi xảy ra"));
    }
}