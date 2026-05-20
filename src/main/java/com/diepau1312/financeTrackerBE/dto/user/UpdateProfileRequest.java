package com.diepau1312.financeTrackerBE.dto.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileRequest {
  @NotBlank(message = "Tên không được để trống")
  private String firstName;

  private String lastName;

  // ── THÊM MỚI: optional — chỉ update khi không null ──
  @Min(value = 1, message = "Ngày bắt đầu tháng phải >= 1")
  @Max(value = 28, message = "Ngày bắt đầu tháng phải <= 28")
  private Integer monthStartDay;
}