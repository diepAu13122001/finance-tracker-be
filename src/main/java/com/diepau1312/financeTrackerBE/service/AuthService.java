package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.auth.AuthResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository              userRepository;
    private final UserSubscriptionRepository  subscriptionRepository;
    private final SubscriptionPlanRepository  planRepository;
    private final PasswordEncoder             passwordEncoder;
    private final JwtUtil                     jwtUtil;

    // ─── Register ─────────────────────────────────────────────────────────────
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        // 1. Kiểm tra email đã tồn tại chưa
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AuthException("Email này đã được đăng ký");
        }

        // 2. Tạo User mới
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .build();

        userRepository.save(user);

        // 3. Lấy FREE plan từ DB (đã được seed sẵn trong V1 migration)
        SubscriptionPlan freePlan = planRepository.findById("FREE")
                .orElseThrow(() -> new RuntimeException("FREE plan không tìm thấy — kiểm tra seed data"));

        // 4. Tạo subscription FREE cho user mới
        UserSubscription subscription = UserSubscription.builder()
                .user(user)
                .plan(freePlan)
                .status("ACTIVE")
                .startedAt(LocalDateTime.now())
                .expiresAt(null)  // FREE không bao giờ hết hạn
                .build();

        subscriptionRepository.save(subscription);

        // 5. Tạo JWT chứa email + planId
        String token = jwtUtil.generateToken(user.getEmail(), freePlan.getId());

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .planId(freePlan.getId())
                .message("Đăng ký thành công")
                .build();
    }

    // ─── Login ────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        // 1. Tìm user theo email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthException("Email hoặc mật khẩu không đúng"));

        // 2. So sánh password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthException("Email hoặc mật khẩu không đúng");
        }

        // 3. Lấy subscription hiện tại
        UserSubscription subscription = subscriptionRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy subscription"));

        String planId = subscription.getPlanId();

        // 4. Tạo JWT
        String token = jwtUtil.generateToken(user.getEmail(), planId);

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .planId(planId)
                .message("Đăng nhập thành công")
                .build();
    }
}