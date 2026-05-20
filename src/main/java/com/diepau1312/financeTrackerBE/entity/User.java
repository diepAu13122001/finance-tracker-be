package com.diepau1312.financeTrackerBE.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(unique = true, nullable = false, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "first_name", length = 100)
  private String firstName;

  @Column(name = "last_name", length = 100)
  private String lastName;

  @Column(name = "default_currency", length = 3)
  private String defaultCurrency = "VND";

  @Column(length = 10)
  private String language = "vi";

  // ── THÊM MỚI: Ngày bắt đầu chu kỳ tháng (1-28) ──
  // VD: monthStartDay = 5 → "tháng 5/2026" = từ 5/5/2026 đến 4/6/2026
  // Default 1 = lịch dương lịch bình thường
  @Column(name = "month_start_day", nullable = false)
  @Builder.Default
  private Integer monthStartDay = 1;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}