package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.auth.LoginRequest;
import com.diepau1312.financeTrackerBE.dto.auth.RegisterRequest;
import com.diepau1312.financeTrackerBE.entity.SubscriptionPlan;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.entity.UserSubscription;
import com.diepau1312.financeTrackerBE.exception.AuthException;
import com.diepau1312.financeTrackerBE.repository.SubscriptionPlanRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.repository.UserSubscriptionRepository;
import com.diepau1312.financeTrackerBE.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

  @Mock
  private UserRepository userRepository;
  @Mock
  private UserSubscriptionRepository subscriptionRepository;
  @Mock
  private SubscriptionPlanRepository planRepository;
  @Mock
  private PasswordEncoder passwordEncoder;
  @Mock
  private JwtUtil jwtUtil;

  @InjectMocks
  private AuthService authService;

  // ── Constants — dùng chung, tránh typo ────────────────────────────────────
  private static final String EMAIL = "test@gmail.com";
  private static final String PASSWORD = "12345678";
  private static final String WRONG_PASSWORD = "wrongpassword";
  private static final String PASSWORD_HASH = "$2a$12$hashed";  // 1 giá trị duy nhất
  private static final String JWT_TOKEN = "jwt.token.here";

  private RegisterRequest registerRequest;
  private LoginRequest loginRequest;
  private SubscriptionPlan freePlan;
  private User mockUser;

  @BeforeEach
  void setUp() {
    registerRequest = new RegisterRequest();
    registerRequest.setEmail(EMAIL);
    registerRequest.setPassword(PASSWORD);
    registerRequest.setFirstName("Diep");

    loginRequest = new LoginRequest();
    loginRequest.setEmail(EMAIL);
    loginRequest.setPassword(PASSWORD);

    freePlan = SubscriptionPlan.builder()
        .id("FREE").name("Miễn phí").priceVnd(0L).build();

    // passwordHash dùng đúng constant PASSWORD_HASH
    mockUser = User.builder()
        .id(UUID.randomUUID())
        .email(EMAIL)
        .passwordHash(PASSWORD_HASH)
        .firstName("Diep")
        .build();
  }

  // ── Register Tests ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("Register thành công với email mới")
  void register_success_returnsTokenAndFreePlan() {
    when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
    when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);
    when(userRepository.save(any())).thenReturn(mockUser);
    when(planRepository.findById("FREE")).thenReturn(Optional.of(freePlan));
    when(subscriptionRepository.save(any())).thenReturn(new UserSubscription());
    when(jwtUtil.generateToken(EMAIL, "FREE")).thenReturn(JWT_TOKEN);

    var response = authService.register(registerRequest);

    assertThat(response.getToken()).isEqualTo(JWT_TOKEN);
    assertThat(response.getPlanId()).isEqualTo("FREE");
    assertThat(response.getEmail()).isEqualTo(EMAIL);
    assertThat(response.getFirstName()).isEqualTo("Diep");
    assertThat(response.getMessage()).isNotBlank();
  }

  @Test
  @DisplayName("Register luôn tạo subscription FREE cho user mới")
  void register_newUser_alwaysGetsFreeSubscription() {
    when(userRepository.existsByEmail(any())).thenReturn(false);
    when(passwordEncoder.encode(any())).thenReturn(PASSWORD_HASH);
    when(userRepository.save(any())).thenReturn(mockUser);
    when(planRepository.findById("FREE")).thenReturn(Optional.of(freePlan));
    when(subscriptionRepository.save(any())).thenReturn(new UserSubscription());
    when(jwtUtil.generateToken(any(), any())).thenReturn(JWT_TOKEN);

    authService.register(registerRequest);

    verify(planRepository).findById("FREE");
    verify(subscriptionRepository).save(argThat(sub ->
        sub.getPlan().getId().equals("FREE")
    ));
  }

  @Test
  @DisplayName("Register hash password trước khi lưu")
  void register_passwordIsHashed_notStoredAsPlainText() {
    when(userRepository.existsByEmail(any())).thenReturn(false);
    when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);
    when(userRepository.save(any())).thenReturn(mockUser);
    when(planRepository.findById("FREE")).thenReturn(Optional.of(freePlan));
    when(subscriptionRepository.save(any())).thenReturn(new UserSubscription());
    when(jwtUtil.generateToken(any(), any())).thenReturn(JWT_TOKEN);

    authService.register(registerRequest);

    verify(userRepository).save(argThat(user ->
        user.getPasswordHash().equals(PASSWORD_HASH) &&
            !user.getPasswordHash().equals(PASSWORD)  // không lưu plain text
    ));
  }

  @Test
  @DisplayName("Register thất bại khi email đã tồn tại")
  void register_duplicateEmail_throwsAuthException() {
    when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

    assertThatThrownBy(() -> authService.register(registerRequest))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("đã được đăng ký");

    verify(userRepository, never()).save(any());
    verify(subscriptionRepository, never()).save(any());
  }

  @Test
  @DisplayName("Register thất bại khi FREE plan không có trong DB")
  void register_freePlanMissing_throwsRuntimeException() {
    when(userRepository.existsByEmail(any())).thenReturn(false);
    when(passwordEncoder.encode(any())).thenReturn(PASSWORD_HASH);
    when(userRepository.save(any())).thenReturn(mockUser);
    when(planRepository.findById("FREE")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.register(registerRequest))
        .isInstanceOf(RuntimeException.class);
  }

  // ── Login Tests ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Login thành công với đúng credentials")
  void login_success() {
    var subscription = UserSubscription.builder()
        .plan(freePlan).status("ACTIVE").build();

    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
    // PASSWORD_HASH khớp với mockUser.getPasswordHash()
    when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
    when(subscriptionRepository.findByUserId(mockUser.getId()))
        .thenReturn(Optional.of(subscription));
    when(jwtUtil.generateToken(EMAIL, "FREE")).thenReturn(JWT_TOKEN);

    var response = authService.login(loginRequest);

    assertThat(response.getToken()).isEqualTo(JWT_TOKEN);
    assertThat(response.getPlanId()).isEqualTo("FREE");
  }

  @Test
  @DisplayName("Login thất bại khi sai mật khẩu")
  void login_wrongPassword_throwsAuthException() {
    // Tạo request với password sai
    LoginRequest wrongPasswordRequest = new LoginRequest();
    wrongPasswordRequest.setEmail(EMAIL);
    wrongPasswordRequest.setPassword(WRONG_PASSWORD);

    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
    // WRONG_PASSWORD vs PASSWORD_HASH → trả false
    when(passwordEncoder.matches(WRONG_PASSWORD, PASSWORD_HASH)).thenReturn(false);

    assertThatThrownBy(() -> authService.login(wrongPasswordRequest))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("Email hoặc mật khẩu không đúng");
  }

  @Test
  @DisplayName("Login thất bại khi email không tồn tại")
  void login_emailNotFound_throwsAuthException() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(loginRequest))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("Email hoặc mật khẩu không đúng");

    // Không check password khi không tìm thấy email
    verify(passwordEncoder, never()).matches(any(), any());
  }

  @Test
  @DisplayName("Login: message lỗi phải giống nhau để tránh user enumeration")
  void login_errorMessageIdentical_preventUserEnumeration() {
    // Case 1: email không tồn tại
    when(userRepository.findByEmail("notexist@gmail.com"))
        .thenReturn(Optional.empty());

    LoginRequest wrongEmailRequest = new LoginRequest();
    wrongEmailRequest.setEmail("notexist@gmail.com");
    wrongEmailRequest.setPassword(PASSWORD);

    // Case 2: sai password
    LoginRequest wrongPassRequest = new LoginRequest();
    wrongPassRequest.setEmail(EMAIL);
    wrongPassRequest.setPassword(WRONG_PASSWORD);

    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
    when(passwordEncoder.matches(WRONG_PASSWORD, PASSWORD_HASH)).thenReturn(false);

    String msgEmailNotFound = "";
    String msgWrongPassword = "";

    try {
      authService.login(wrongEmailRequest);
    } catch (AuthException e) {
      msgEmailNotFound = e.getMessage();
    }

    try {
      authService.login(wrongPassRequest);
    } catch (AuthException e) {
      msgWrongPassword = e.getMessage();
    }

    assertThat(msgEmailNotFound)
        .isNotEmpty()
        .isEqualTo(msgWrongPassword);
  }
}