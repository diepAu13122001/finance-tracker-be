package com.diepau1312.financeTrackerBE.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  // Plan đang mua: PLUS hoặc PREMIUM
  @Column(name = "plan_id", nullable = false, length = 20)
  private String planId;

  @Column(name = "amount_vnd", nullable = false)
  private Long amountVnd;

  // PENDING / PAID / CANCELLED / FAILED
  @Column(nullable = false, length = 20)
  @Builder.Default
  private String status = "PENDING";

  // orderCode PayOS — số nguyên unique, dùng để query webhook
  @Column(name = "payos_order_id", unique = true)
  private String payosOrderId;

  @Column(name = "payos_payment_link_id")
  private String payosPaymentLinkId;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;
}