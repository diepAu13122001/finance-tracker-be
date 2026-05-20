package com.diepau1312.financeTrackerBE.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreatePaymentRequest {

  // Chỉ cho phép upgrade PLUS hoặc PREMIUM, không phải FREE
  @NotBlank
  @Pattern(regexp = "^(PLUS|PREMIUM)$", message = "Plan phải là PLUS hoặc PREMIUM")
  private String planId;
}