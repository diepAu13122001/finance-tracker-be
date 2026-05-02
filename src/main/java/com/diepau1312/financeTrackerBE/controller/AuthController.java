package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.dto.auth.AuthResponse;
import com.diepau1312.financeTrackerBE.dto.auth.LoginRequest;
import com.diepau1312.financeTrackerBE.dto.auth.RegisterRequest;
import com.diepau1312.financeTrackerBE.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@Tag(name = "Authentication", description = "Đăng ký và đăng nhập")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private AuthService authService;

  @Operation(
      summary = "Đăng ký tài khoản mới",
      description = "Tạo tài khoản với gói FREE mặc định. Email phải unique."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Đăng ký thành công, trả về JWT"),
      @ApiResponse(responseCode = "400", description = "Email đã tồn tại hoặc dữ liệu không hợp lệ"),
  })
  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(
      @Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(authService.register(request));
  }

  @Operation(
      summary = "Đăng nhập",
      description = "Trả về JWT chứa email và planId. Token hết hạn sau 15 phút."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Đăng nhập thành công"),
      @ApiResponse(responseCode = "400", description = "Email hoặc mật khẩu không đúng"),
  })
  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }
}