package com.diepau1312.financeTrackerBE.repository;

import com.diepau1312.financeTrackerBE.entity.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, UUID> {

  // Webhook PayOS gửi orderCode → tra ngược lại record
  Optional<PaymentHistory> findByPayosOrderId(String payosOrderId);
}