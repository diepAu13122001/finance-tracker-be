package com.diepau1312.financeTrackerBE.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AIClassifyResponse {
    private String category; // "SKINCARE" | "HOUSECARE" | "FOOD" | "CLOTHES" | "OTHER"
    private String subcategory; // vd: "Skincare - Làm sạch", "Vệ sinh nhà"
    private String reasoning; // giải thích ngắn gọn của AI
    private boolean success;
    private String errorMessage;
}