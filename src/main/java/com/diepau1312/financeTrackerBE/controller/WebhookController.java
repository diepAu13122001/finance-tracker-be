package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.payos.PayOS;
// ✅ Import mới — bỏ vn.payos.type.*
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

  private final PayOS payOS;
  private final PaymentService paymentService;

  @PostMapping("/payos")
  public ResponseEntity<?> handlePayOSWebhook(@RequestBody Webhook webhook) {
    log.info("=== WEBHOOK RECEIVED ===");  // ← thêm
    log.info("Raw webhook: {}", webhook);   // ← thêm
    try {
      WebhookData data = payOS.webhooks().verify(webhook);
      log.info("Verified: code={}, desc={}, orderCode={}", data.getCode(), data.getDesc(), data.getOrderCode());

      String orderCode = String.valueOf(data.getOrderCode());
      log.info("PayOS webhook: orderCode={}, code={}, desc={}", orderCode, data.getCode(), data.getDesc());

      if ("00".equals(data.getCode())) {
        paymentService.activateSubscription(orderCode);
      } else {
        paymentService.markPaymentFailed(orderCode, data.getDesc());
      }

      return ResponseEntity.ok(Map.of("success", true));

    } catch (Exception e) {
      log.error("Webhook error: {}", e.getMessage(), e);
      return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
    }
  }
}