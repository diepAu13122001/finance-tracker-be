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
class AuthServiceTest {

    // ─── Mocks — các dependency của AuthService ───────────────────────────────
    @Mock private UserRepository             userRepository;
    @Mock private UserSubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private PasswordEncoder            passwordEncoder;
    @Mock private JwtUtil                    jwtUtil;

    // ─── Subject — class đang được test ──────────────────────────────────────
    @InjectMocks
    private AuthService authService;

    // ─── Test data ────────────────────────────────────────────────────────────
    private RegisterRequest registerRequest;
    private LoginRequest    loginRequest;
    private SubscriptionPlan freePlan;
    private User             mockUser;

    @BeforeEach
    void setUp() {
        // Chuẩn bị data dùng chung cho các test
        registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@gmail.com");
        registerRequest.setPassword("12345678");
        registerRequest.setFirstName("Diep");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@gmail.com");
        loginRequest.setPassword("12345678");

        freePlan = SubscriptionPlan.builder()
                .id("FREE")
                .name("Miễn phí")
                .priceVnd(0L)
                .build();

        mockUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@gmail.com")
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Diep")
                .build();
    }

    // ─── Register tests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Đăng ký thành công với email mới")
    void register_success() {
        // Arrange — chuẩn bị mock behavior
        when(userRepository.existsByEmail("test@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode("12345678")).thenReturn("$2a$12$hashedpassword");
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        when(planRepository.findById("FREE")).thenReturn(Optional.of(freePlan));
        when(subscriptionRepository.save(any(UserSubscription.class)))
                .thenReturn(new UserSubscription());
        when(jwtUtil.generateToken("test@gmail.com", "FREE")).thenReturn("mock.jwt.token");

        // Act — gọi method cần test
        var response = authService.register(registerRequest);

        // Assert — kiểm tra kết quả
        assertThat(response.getToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getEmail()).isEqualTo("test@gmail.com");
        assertThat(response.getPlanId()).isEqualTo("FREE");
        assertThat(response.getFirstName()).isEqualTo("Diep");

        // Verify — đảm bảo các method được gọi đúng số lần
        verify(userRepository).save(any(User.class));
        verify(subscriptionRepository).save(any(UserSubscription.class));
    }

    @Test
    @DisplayName("Đăng ký thất bại khi email đã tồn tại")
    void register_emailAlreadyExists_throwsAuthException() {
        // Arrange
        when(userRepository.existsByEmail("test@gmail.com")).thenReturn(true);

        // Act & Assert — kỳ vọng exception được throw
        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("đã được đăng ký");

        // Verify — đảm bảo KHÔNG gọi save khi email trùng
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("User mới luôn được tạo với gói FREE")
    void register_newUser_getsFreePlan() {
        // Arrange
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(mockUser);
        when(planRepository.findById("FREE")).thenReturn(Optional.of(freePlan));
        when(subscriptionRepository.save(any())).thenReturn(new UserSubscription());
        when(jwtUtil.generateToken(any(), eq("FREE"))).thenReturn("token");

        // Act
        var response = authService.register(registerRequest);

        // Assert — planId phải là FREE
        assertThat(response.getPlanId()).isEqualTo("FREE");

        // Verify — subscription được tạo với FREE plan
        verify(planRepository).findById("FREE");
    }

    // ─── Login tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Đăng nhập thành công với đúng thông tin")
    void login_success() {
        // Arrange
        UserSubscription subscription = UserSubscription.builder()
                .plan(freePlan)
                .status("ACTIVE")
                .build();

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("12345678", "$2a$12$hashedpassword"))
                .thenReturn(true);
        when(subscriptionRepository.findByUserId(mockUser.getId()))
                .thenReturn(Optional.of(subscription));
        when(jwtUtil.generateToken("test@gmail.com", "FREE"))
                .thenReturn("mock.jwt.token");

        // Act
        var response = authService.login(loginRequest);

        // Assert
        assertThat(response.getToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getPlanId()).isEqualTo("FREE");
    }

    @Test
    @DisplayName("Đăng nhập thất bại khi sai mật khẩu")
    void login_wrongPassword_throwsAuthException() {
        // Arrange
        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("12345678", "$2a$12$hashedpassword"))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Email hoặc mật khẩu không đúng");
    }

    @Test
    @DisplayName("Đăng nhập thất bại khi email không tồn tại")
    void login_emailNotFound_throwsAuthException() {
        // Arrange
        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Email hoặc mật khẩu không đúng");

        // Verify — không gọi passwordEncoder khi không tìm thấy user
        verify(passwordEncoder, never()).matches(any(), any());
    }
}