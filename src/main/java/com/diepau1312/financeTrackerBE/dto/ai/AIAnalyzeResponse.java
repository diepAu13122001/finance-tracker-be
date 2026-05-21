package com.diepau1312.financeTrackerBE.dto.ai;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AIAnalyzeResponse {
    private boolean success;
    private String overview;      // Nhận xét tổng quan 1-2 câu
    private String topInsight;    // Insight nổi bật nhất
    private String suggestion;    // Gợi ý cải thiện cụ thể
    private List<String> warnings; // Cảnh báo: chi > thu, chi > 80% thu...
    private String errorMessage;
}
