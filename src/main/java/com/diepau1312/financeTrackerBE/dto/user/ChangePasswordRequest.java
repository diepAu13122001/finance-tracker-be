package com.diepau1312.financeTrackerBE.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
  @NotBlank
  private String currentPassword;

  @NotBlank
  @Size(min = 8, message = "Mật khẩu mới phải có ít nhất 8 ký tự")
  private String newPassword;
}