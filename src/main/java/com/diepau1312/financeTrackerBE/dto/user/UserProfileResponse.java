package com.diepau1312.financeTrackerBE.dto.user;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileResponse {
  private String email;
  private String firstName;
  private String lastName;
  private Integer monthStartDay;   // ── THÊM MỚI ──
  private String planId;
  private String planStatus;
  private String expiresAt;
}