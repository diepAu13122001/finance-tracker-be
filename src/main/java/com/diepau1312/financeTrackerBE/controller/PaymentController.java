package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.dto.payment.CreatePaymentRequest;
import com.diepau1312.financeTrackerBE.dto.payment.PaymentLinkResponse;
import com.diepau1312.financeTrackerBE.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payments", description = "Nâng cấp gói qua PayOS")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

  @Operation(summary = "Tạo link thanh toán PayOS")
  @PostMapping("/create-link")
  public ResponseEntity<PaymentLinkResponse> createPaymentLink(
      @Valid @RequestBody CreatePaymentRequest request) {
    return ResponseEntity.ok(paymentService.createPaymentLink(request.getPlanId()));
  }
}