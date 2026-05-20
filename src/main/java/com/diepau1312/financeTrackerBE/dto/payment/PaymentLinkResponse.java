package com.diepau1312.financeTrackerBE.dto.payment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentLinkResponse {
  private String checkoutUrl;   // Link redirect user sang trang thanh toán PayOS
  private String qrCode;        // Mã QR (mobile có thể quét)
  private String orderCode;     // Lưu lại để query status
}