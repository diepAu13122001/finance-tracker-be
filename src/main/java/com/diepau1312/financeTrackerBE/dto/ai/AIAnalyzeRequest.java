package com.diepau1312.financeTrackerBE.dto.ai;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AIAnalyzeRequest {

    // API key Gemini — lấy từ frontend localStorage, KHÔNG lưu server
    @NotBlank
    private String geminiApiKey;

    @NotNull @Min(2020) @Max(2100)
    private Integer year;

    @NotNull @Min(1) @Max(12)
    private Integer month;
}
