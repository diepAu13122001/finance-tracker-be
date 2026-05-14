package com.diepau1312.financeTrackerBE.dto.goal;

import com.diepau1312.financeTrackerBE.entity.Goal.GoalSubtype;
import com.diepau1312.financeTrackerBE.entity.Goal.GoalType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class GoalRequest {

  @NotBlank(message = "Tên không được để trống")
  @Size(max = 100)
  private String name;

  private String icon;

  @Pattern(regexp = "^#[0-9a-fA-F]{6}$")
  private String color;

  @NotNull(message = "Loại không được để trống")
  private GoalType type;

  private GoalSubtype subtype;  // 👈 THÊM: chỉ cần khi type = DEBT

  // SAVINGS/INVESTMENT: mục tiêu tích lũy
  // DEBT: hạn mức nợ (creditLimit nếu là CREDIT_CARD, hoặc tổng trả góp)
  // NORMAL: để 0 (không cần)
  @Min(value = 0, message = "Số tiền không âm")
  private Long targetAmount = 0L;

  private LocalDate deadline;

  // ── DEBT CREDIT_CARD ──────────────────────────────────────────────────────
  private Long creditLimit;

  @Min(1)
  @Max(28)
  private Integer billingDate;

  @DecimalMin("0.0")
  @DecimalMax("100.0")
  private BigDecimal interestRate;
}