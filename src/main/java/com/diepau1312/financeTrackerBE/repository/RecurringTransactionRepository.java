package com.diepau1312.financeTrackerBE.repository;

import com.diepau1312.financeTrackerBE.entity.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {

  List<RecurringTransaction> findByUserIdOrderByNextExecutionDateAsc(UUID userId);

  Optional<RecurringTransaction> findByIdAndUserId(UUID id, UUID userId);
}
