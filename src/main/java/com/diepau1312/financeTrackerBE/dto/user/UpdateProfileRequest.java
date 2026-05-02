package com.diepau1312.financeTrackerBE.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileRequest {
  @NotBlank(message = "Tên không được để trống")
  private String firstName;
  private String lastName;
}