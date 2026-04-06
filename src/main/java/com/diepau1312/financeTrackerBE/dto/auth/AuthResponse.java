package com.diepau1312.financeTrackerBE.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String email;
    private String firstName;
    private String planId;       // "FREE" | "PLUS" | "PREMIUM" — frontend dùng để init usePlan
    private String message;
}