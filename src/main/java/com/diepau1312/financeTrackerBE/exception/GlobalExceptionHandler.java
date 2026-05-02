package com.diepau1312.financeTrackerBE.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<?> handleNotFound(NotFoundException ex) {
    log.warn("Not found: {}", ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
  }

  // Xử lý lỗi chung — không lộ stack trace ra ngoài
  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> handleGeneral(Exception ex) {
    // Log đầy đủ stack trace cho lỗi không mong đợi
    log.error("Unexpected error: ", ex);
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of(
            "error", "INTERNAL_ERROR",
            "message", "Đã có lỗi xảy ra, vui lòng thử lại"
        ));
  }

  // Xử lý khi plan không đủ → 403
  @ExceptionHandler(PlanUpgradeRequiredException.class)
  public ResponseEntity<?> handlePlanUpgrade(PlanUpgradeRequiredException ex) {
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(Map.of(
            "error", "PLAN_UPGRADE_REQUIRED",
            "requiredPlan", ex.getRequiredPlan(),
            "message", ex.getMessage(),
            "upgradeUrl", "/pricing"
        ));
  }


  @ExceptionHandler(AuthException.class)
  public ResponseEntity<?> handleAuth(AuthException ex) {
    return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(Map.of(
            "error", "AUTH_ERROR",
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
            "error", "VALIDATION_ERROR",
            "message", message
        ));
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<?> handleForbidden(ForbiddenException ex) {
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(Map.of("error", "FORBIDDEN", "message", ex.getMessage()));
  }
}