package com.diepau1312.financeTrackerBE.aspect;

import com.diepau1312.financeTrackerBE.annotation.RequiresPlan;
import com.diepau1312.financeTrackerBE.exception.PlanUpgradeRequiredException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
@Slf4j
public class PlanGateAspect {

    // Thứ tự cấp độ — giống PLAN_LEVELS bên frontend
    private static final Map<String, Integer> PLAN_LEVELS = Map.of(
            "FREE",    1,
            "PLUS",    2,
            "PREMIUM", 3
    );

    @Around("@annotation(requiresPlan)")
    public Object checkPlan(
            ProceedingJoinPoint joinPoint,
            RequiresPlan requiresPlan
    ) throws Throwable {

        String requiredPlan  = requiresPlan.value();
        String userPlan      = extractPlanFromContext();

        int userLevel     = PLAN_LEVELS.getOrDefault(userPlan, 1);
        int requiredLevel = PLAN_LEVELS.getOrDefault(requiredPlan, 1);

        if (userLevel < requiredLevel) {
            log.debug("Plan gate blocked: user={}, required={}", userPlan, requiredPlan);
            throw new PlanUpgradeRequiredException(requiredPlan);
        }

        // Đủ quyền → chạy method bình thường
        return joinPoint.proceed();
    }

    // Đọc planId từ authorities trong SecurityContext
    // JwtAuthFilter đã set "PLAN_FREE" / "PLAN_PLUS" / "PLAN_PREMIUM" vào đây
    private String extractPlanFromContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "FREE";

        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("PLAN_"))
                .map(a -> a.replace("PLAN_", ""))
                .findFirst()
                .orElse("FREE");
    }
}