package com.diepau1312.financeTrackerBE.dto.household;

import com.diepau1312.financeTrackerBE.entity.HouseholdItem;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemCategory;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class HouseholdItemResponse {
    private UUID id;
    private String name;
    private String brand;
    private ItemCategory category;
    private String aiCategory;
    private Long price;
    private LocalDate purchaseDate;
    private LocalDate expiryDate;
    private BigDecimal quantity;
    private String unit;
    private ItemStatus status;
    private Integer notifyBeforeDays;
    private String notes;
    private LocalDateTime createdAt;

    // ── Computed fields ──────────────────────────────────────────────────────
    /** Số ngày còn lại đến hạn (âm = đã quá hạn) */
    private Long daysUntilExpiry;
    /** true nếu hết hạn trong vòng notifyBeforeDays */
    private boolean expiringSoon;
    /** true nếu đã quá hạn */
    private boolean expired;

    public static HouseholdItemResponse from(HouseholdItem item) {
        Long daysUntil = null;
        boolean expiringSoon = false;
        boolean expired = false;

        if (item.getExpiryDate() != null) {
            daysUntil = (long) LocalDate.now().until(item.getExpiryDate()).getDays();
            // Note: Period.getDays() chỉ lấy days component, dùng ChronoUnit cho chính xác
            long daysUntilLong = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.now(), item.getExpiryDate());
            daysUntil = daysUntilLong;
            expired = daysUntilLong < 0;
            expiringSoon = !expired && daysUntilLong <= item.getNotifyBeforeDays();
        }

        return HouseholdItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .brand(item.getBrand())
                .category(item.getCategory())
                .aiCategory(item.getAiCategory())
                .price(item.getPrice())
                .purchaseDate(item.getPurchaseDate())
                .expiryDate(item.getExpiryDate())
                .quantity(item.getQuantity())
                .unit(item.getUnit())
                .status(item.getStatus())
                .notifyBeforeDays(item.getNotifyBeforeDays())
                .notes(item.getNotes())
                .createdAt(item.getCreatedAt())
                .daysUntilExpiry(daysUntil)
                .expiringSoon(expiringSoon)
                .expired(expired)
                .build();
    }
}