package com.diepau1312.financeTrackerBE.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AIParseRequest {
  // Text người dùng nhập: "ăn sáng 45k", "lương tháng 20tr"
  @NotBlank
  private String text;

  // API key Gemini — lấy từ frontend (lưu localStorage phía client)
  // KHÔNG lưu trên server → user tự quản lý key của mình
  @NotBlank
  private String geminiApiKey;
}