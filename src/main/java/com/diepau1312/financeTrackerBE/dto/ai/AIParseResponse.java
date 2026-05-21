package com.diepau1312.financeTrackerBE.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AIParseResponse {
  private String type;          // "INCOME" | "EXPENSE"
  private Long amount;          // số tiền đã parse
  private String note;          // ghi chú
  private String suggestedCategory; // tên category gợi ý (có thể null)
  private String rawText;       // text gốc người dùng nhập
  private boolean success;
  private String errorMessage;  // nếu parse thất bại

}