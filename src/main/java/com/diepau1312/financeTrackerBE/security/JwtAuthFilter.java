package com.diepau1312.financeTrackerBE.security;

import com.diepau1312.financeTrackerBE.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Đọc header Authorization
        final String authHeader = request.getHeader("Authorization");

        // Không có token hoặc không đúng format → bỏ qua, tiếp tục filter chain
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Cắt bỏ "Bearer " lấy token thật
        final String token = authHeader.substring(7);

        try {
            final String email  = jwtUtil.extractEmail(token);
            final String planId = jwtUtil.extractPlanId(token);

            // Chỉ set authentication nếu chưa có trong context
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Dùng planId làm authority — @RequiresPlan sẽ đọc từ đây
                var authorities = List.of(new SimpleGrantedAuthority("PLAN_" + planId));
                // PLAN_FREE, PLAN_PLUS, hoặc PLAN_PREMIUM

                var authentication = new UsernamePasswordAuthenticationToken(
                        email,       // principal — ai đang đăng nhập
                        null,        // credentials — không cần password nữa
                        authorities  // quyền hạn
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException e) {
            // Token không hợp lệ hoặc đã hết hạn — không set authentication
            // Request sẽ bị từ chối bởi SecurityConfig
        }

        filterChain.doFilter(request, response);
    }
}