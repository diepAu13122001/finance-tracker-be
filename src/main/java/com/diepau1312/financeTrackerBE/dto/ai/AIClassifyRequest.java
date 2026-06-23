package com.diepau1312.financeTrackerBE.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AIClassifyRequest {

    /** Tên sản phẩm — bắt buộc */
    @NotBlank
    private String name;

    /** Thương hiệu — không bắt buộc nhưng giúp AI chính xác hơn */
    private String brand;

    /** Gemini API key từ frontend (localStorage) */
    @NotBlank
    private String geminiApiKey;
}