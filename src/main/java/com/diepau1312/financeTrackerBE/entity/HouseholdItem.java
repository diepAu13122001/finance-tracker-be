package com.diepau1312.financeTrackerBE.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "household_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseholdItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ItemCategory category = ItemCategory.OTHER;

    /** AI-suggested category — user có thể override bằng category thật */
    @Column(name = "ai_category", length = 50)
    private String aiCategory;

    private Long price;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(length = 20)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ItemStatus status = ItemStatus.IN_USE;

    @Column(name = "notify_before_days")
    @Builder.Default
    private Integer notifyBeforeDays = 7;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ─── Enums ───────────────────────────────────────────────────────────────
    public enum ItemCategory {
        SKINCARE, HOUSECARE, FOOD, CLOTHES, OTHER
    }

    public enum ItemStatus {
        IN_USE, FINISHED, NEED_RESTOCK
    }
}