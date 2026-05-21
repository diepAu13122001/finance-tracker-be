package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.user.*;
import com.diepau1312.financeTrackerBE.entity.*;
import com.diepau1312.financeTrackerBE.exception.AuthException;
import com.diepau1312.financeTrackerBE.repository.*;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSubscriptionRepository subscriptionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private static final String EMAIL = "test@gmail.com";
    private static final UUID USER_ID = UUID.randomUUID();

    private User mockUser;
    private MockedStatic<SecurityUtil> securityMock;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(USER_ID).email(EMAIL)
                .firstName("Diep").lastName("Au")
                .monthStartDay(1).build();

        securityMock = mockStatic(SecurityUtil.class);
        securityMock.when(SecurityUtil::getCurrentUserEmail).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
    }

    @AfterEach
    void tearDown() {
        securityMock.close();
    }

    // ─── GET PROFILE ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProfile trả về đúng thông tin user + plan")
    void getProfile_returnsCorrectInfo() {
        var plan = SubscriptionPlan.builder().id("FREE").build();
        var sub = UserSubscription.builder().plan(plan).status("ACTIVE")
                .startedAt(LocalDateTime.now()).build();
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(sub));

        var profile = userService.getProfile();

        assertThat(profile.getEmail()).isEqualTo(EMAIL);
        assertThat(profile.getFirstName()).isEqualTo("Diep");
        assertThat(profile.getPlanId()).isEqualTo("FREE");
        assertThat(profile.getMonthStartDay()).isEqualTo(1);
    }

    @Test
    @DisplayName("getProfile với expiresAt không null → trả về expiresAt string")
    void getProfile_withExpiresAt_returnsExpiresAt() {
        LocalDateTime expires = LocalDateTime.of(2027, 1, 1, 0, 0);
        var plan = SubscriptionPlan.builder().id("PLUS").build();
        var sub = UserSubscription.builder().plan(plan).status("ACTIVE")
                .startedAt(LocalDateTime.now()).expiresAt(expires).build();
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(sub));

        var profile = userService.getProfile();

        assertThat(profile.getExpiresAt()).isNotNull();
        assertThat(profile.getExpiresAt()).contains("2027");
    }

    // ─── UPDATE PROFILE ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProfile cập nhật tên thành công")
    void updateProfile_updatesName() {
        var plan = SubscriptionPlan.builder().id("FREE").build();
        var sub = UserSubscription.builder().plan(plan).status("ACTIVE")
                .startedAt(LocalDateTime.now()).build();
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(sub));
        when(userRepository.save(any())).thenReturn(mockUser);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFirstName("Diep Moi");
        req.setLastName("Au");

        userService.updateProfile(req);

        verify(userRepository).save(argThat(u -> "Diep Moi".equals(u.getFirstName())));
    }

    @Test
    @DisplayName("updateProfile với monthStartDay hợp lệ (1-28)")
    void updateProfile_validMonthStartDay_updates() {
        var plan = SubscriptionPlan.builder().id("FREE").build();
        var sub = UserSubscription.builder().plan(plan).status("ACTIVE")
                .startedAt(LocalDateTime.now()).build();
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(sub));
        when(userRepository.save(any())).thenReturn(mockUser);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFirstName("Diep");
        req.setMonthStartDay(5); // lương ngày 5

        userService.updateProfile(req);

        verify(userRepository).save(argThat(u -> u.getMonthStartDay() == 5));
    }

    @Test
    @DisplayName("updateProfile với monthStartDay = 29 → throw AuthException")
    void updateProfile_invalidMonthStartDay_throwsAuthException() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFirstName("Diep");
        req.setMonthStartDay(29); // không hợp lệ

        assertThatThrownBy(() -> userService.updateProfile(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("28");
    }

    // ─── CHANGE PASSWORD ──────────────────────────────────────────────────────

    @Test
    @DisplayName("changePassword thành công với đúng mật khẩu hiện tại")
    void changePassword_correctCurrentPassword_success() {
        mockUser.setPasswordHash("$2a$12$hashed_old");
        when(passwordEncoder.matches("oldPass123", "$2a$12$hashed_old")).thenReturn(true);
        when(passwordEncoder.encode("newPass456")).thenReturn("$2a$12$hashed_new");
        when(userRepository.save(any())).thenReturn(mockUser);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("oldPass123");
        req.setNewPassword("newPass456");

        userService.changePassword(req);

        verify(userRepository).save(argThat(u -> u.getPasswordHash().equals("$2a$12$hashed_new")));
    }

    @Test
    @DisplayName("changePassword sai mật khẩu hiện tại → throw AuthException")
    void changePassword_wrongCurrentPassword_throwsAuthException() {
        mockUser.setPasswordHash("$2a$12$hashed_old");
        when(passwordEncoder.matches("wrongPass", "$2a$12$hashed_old")).thenReturn(false);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("wrongPass");
        req.setNewPassword("newPass456");

        assertThatThrownBy(() -> userService.changePassword(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("không đúng");

        verify(userRepository, never()).save(any());
    }
}