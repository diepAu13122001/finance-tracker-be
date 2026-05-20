package com.diepau1312.financeTrackerBE.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

@Configuration
public class PayOSConfig {

  @Value("${payos.client-id}")
  private String clientId;

  @Value("${payos.api-key}")
  private String apiKey;

  @Value("${payos.checksum-key}")
  private String checksumKey;

  // Tạo PayOS bean dùng chung — Spring tự inject vào PaymentService
  @Bean
  public PayOS payOS() throws Exception {
    return new PayOS(clientId.trim(), apiKey.trim(), checksumKey.trim());
  }
}