package com.diepau1312.financeTrackerBE.util;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtil {

  // Lấy email của user đang đăng nhập từ bất kỳ đâu
  public static String getCurrentUserEmail() {
    return (String) SecurityContextHolder
        .getContext()
        .getAuthentication()
        .getPrincipal();
  }
}