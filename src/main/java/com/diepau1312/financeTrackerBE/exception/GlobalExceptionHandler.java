package com.diepau1312.financeTrackerBE.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}