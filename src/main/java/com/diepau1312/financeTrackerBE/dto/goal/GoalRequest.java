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

  private GoalSubtype subtype;

  @Min(value = 0)
  private Long targetAmount = 0L;

  private LocalDate deadline;

  // ── DEBT CREDIT_CARD ───────────────────────────────────────────────────
  private Long creditLimit;

  @Min(1)
  @Max(28)
  private Integer billingDate;

  // interestRate vẫn giữ để tính sau (không hiển thị trong form tạo mới)
  private BigDecimal interestRate;

  // ── DEBT INSTALLMENT ───────────────────────────────────────────────────
  @Min(1)
  private Integer numberOfPeriods; // tổng số kỳ

  @Min(0)
  private Long monthlyPayment; // số tiền trả mỗi kỳ (đã gồm lãi)

  @Min(0)
  private Long initialAmount; // số tiền vay ban đầu
}