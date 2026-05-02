package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.user.*;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.exception.AuthException;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.repository.UserSubscriptionRepository;
import com.diepau1312.financeTrackerBE.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final UserSubscriptionRepository subscriptionRepository;
  private final PasswordEncoder passwordEncoder;

  private User getCurrentUser() {
    return userRepository.findByEmail(SecurityUtil.getCurrentUserEmail()).orElseThrow(() -> new RuntimeException("Không tìm thấy user"));
  }

  @Transactional(readOnly = true)
  public UserProfileResponse getProfile() {
    User user = getCurrentUser();
    var sub = subscriptionRepository.findByUserId(user.getId()).orElse(null);

    return UserProfileResponse.builder().email(user.getEmail()).firstName(user.getFirstName()).lastName(user.getLastName()).planId(sub != null ? sub.getPlanId() : "FREE").planStatus(sub != null ? sub.getStatus() : "ACTIVE").expiresAt(sub != null && sub.getExpiresAt() != null ? sub.getExpiresAt().toString() : null).build();
  }

  @Transactional
  public UserProfileResponse updateProfile(UpdateProfileRequest request) {
    User user = getCurrentUser();
    user.setFirstName(request.getFirstName());
    user.setLastName(request.getLastName());
    userRepository.save(user);
    return getProfile();
  }

  @Transactional
  public void changePassword(ChangePasswordRequest request) {
    User user = getCurrentUser();

    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
      throw new AuthException("Mật khẩu hiện tại không đúng");
    }

    user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
    userRepository.save(user);
  }
}