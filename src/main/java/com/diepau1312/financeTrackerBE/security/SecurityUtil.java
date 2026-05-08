package com.diepau1312.financeTrackerBE.security;

import com.diepau1312.financeTrackerBE.exception.AuthException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**finance_tracker
 * Utility class — đọc thông tin user hiện tại từ SecurityContext.
 * Được set bởi JwtAuthFilter sau khi parse token.
 */
public final class SecurityUtil {

  // Private constructor — không cho phép instantiate
  private SecurityUtil() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Lấy email của user đang đăng nhập.
   *
   * @throws AuthException nếu chưa authenticate
   */
  public static String getCurrentUserEmail() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth == null
        || !auth.isAuthenticated()
        || "anonymousUser".equals(auth.getPrincipal())) {
      throw new AuthException("Chưa đăng nhập");
    }

    // Principal là email vì JwtAuthFilter set như vậy
    return auth.getName();
  }

  /**
   * Lấy plan của user đang đăng nhập (FREE/PLUS/PREMIUM).
   * Đọc từ authority "PLAN_*" được set bởi JwtAuthFilter.
   */
  public static String getCurrentUserPlan() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth == null || !auth.isAuthenticated()) {
      return "FREE";  // default an toàn
    }

    return auth.getAuthorities().stream()
        .map(Object::toString)
        .filter(a -> a.startsWith("PLAN_"))
        .findFirst()
        .map(a -> a.substring("PLAN_".length()))
        .orElse("FREE");
  }
}