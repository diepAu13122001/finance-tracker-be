package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.dto.user.*;
import com.diepau1312.financeTrackerBE.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@Tag(name = "Users", description = "Quản lý thông tin người dùng")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @Operation(summary = "Lấy thông tin cá nhân và gói dịch vụ hiện tại")
  @GetMapping("/me")
  public ResponseEntity<UserProfileResponse> getProfile() {
    return ResponseEntity.ok(userService.getProfile());
  }

  @Operation(summary = "Cập nhật họ tên")
  @PutMapping("/me")
  public ResponseEntity<UserProfileResponse> updateProfile(
      @Valid @RequestBody UpdateProfileRequest request) {
    return ResponseEntity.ok(userService.updateProfile(request));
  }

  @Operation(
      summary = "Đổi mật khẩu",
      description = "Cần nhập đúng mật khẩu hiện tại mới đổi được"
  )
  @PutMapping("/me/password")
  public ResponseEntity<?> changePassword(
      @Valid @RequestBody ChangePasswordRequest request) {
    userService.changePassword(request);
    return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công"));
  }
}