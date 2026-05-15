package com.diepau1312.financeTrackerBE.dto.goal;

import com.diepau1312.financeTrackerBE.entity.Goal.GoalSubtype;
import com.diepau1312.financeTrackerBE.entity.Goal.GoalType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class GoalRequest {

  @NotBlank(message = "Tên mục tiêu không được để trống")
  @Size(max = 100, message = "Tên tối đa 100 ký tự")
  private String name;

  private String icon;

  @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Màu phải là mã hex 6 ký tự")
  private String color;

  @NotNull(message = "Loại mục tiêu không được để trống")
  private GoalType type;

  // Chỉ dùng cho DEBT: CREDIT_CARD hoặc INSTALLMENT (optional)
  private GoalSubtype subtype;

  // NORMAL wallet: targetAmount = 0 (không có mục tiêu)
  // SAVINGS/DEBT/INVESTMENT: targetAmount > 0 (validate ở service layer)
  @NotNull(message = "Số tiền mục tiêu không được để trống")
  @Min(value = 0, message = "Số tiền phải >= 0")
  private Long targetAmount;

  private LocalDate deadline;

  // ── DEBT / CREDIT_CARD fields (optional) ─────────────────────────────────

  @Min(value = 0, message = "Hạn mức phải >= 0")
  private Long creditLimit;

  @Min(value = 1, message = "Ngày đáo hạn từ 1-28")
  @Max(value = 28, message = "Ngày đáo hạn từ 1-28")
  private Integer billingDate;

  @DecimalMin(value = "0.0", message = "Lãi suất phải >= 0")
  private BigDecimal interestRate;

  // ── INSTALLMENT fields (optional) ─────────────────────────────────────────

  @Min(value = 1, message = "Số kỳ phải >= 1")
  private Integer numberOfPeriods;

  @Min(value = 0, message = "Tiền góp phải >= 0")
  private Long monthlyPayment;

  @Min(value = 0, message = "Số tiền ban đầu phải >= 0")
  private Long initialAmount;
}