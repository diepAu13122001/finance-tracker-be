package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.dto.user.*;
import com.diepau1312.financeTrackerBE.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public ResponseEntity<UserProfileResponse> getProfile() {
    return ResponseEntity.ok(userService.getProfile());
  }

  @PutMapping("/me")
  public ResponseEntity<UserProfileResponse> updateProfile(
      @Valid @RequestBody UpdateProfileRequest request
  ) {
    return ResponseEntity.ok(userService.updateProfile(request));
  }

  @PutMapping("/me/password")
  public ResponseEntity<?> changePassword(
      @Valid @RequestBody ChangePasswordRequest request
  ) {
    userService.changePassword(request);
    return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công"));
  }
}