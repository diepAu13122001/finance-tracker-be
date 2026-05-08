package com.diepau1312.financeTrackerBE.repository;

import com.diepau1312.financeTrackerBE.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

  Page<Transaction> findByUserIdOrderByTransactionDateDesc(
      UUID userId, Pageable pageable);

  Page<Transaction> findByUserIdAndTypeOrderByTransactionDateDesc(
      UUID userId, Transaction.TransactionType type, Pageable pageable);

  @Query("""
          SELECT COUNT(t) FROM Transaction t
          WHERE t.user.id = :userId
            AND t.transactionDate >= :startDate
            AND t.transactionDate <= :endDate
      """)
  long countByUserIdAndDateBetween(
      @Param("userId") UUID userId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate
  );

  @Query("""
          SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
          WHERE t.user.id = :userId
            AND t.type = :type
            AND t.transactionDate >= :startDate
            AND t.transactionDate <= :endDate
      """)
  Long sumAmountByUserIdAndTypeAndDateBetween(
      @Param("userId") UUID userId,
      @Param("type") Transaction.TransactionType type,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate
  );

  // ✅ Chuyển sang nativeQuery=true để dùng date_trunc PostgreSQL
  // ✅ Cast transaction_date::TIMESTAMP — khớp với expression index ở V3
  @Query(value = """
          SELECT
              EXTRACT(MONTH FROM transaction_date)                                    AS month,
              SUM(CASE WHEN type = 'INCOME'  THEN amount ELSE 0 END)                 AS income,
              SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END)                 AS expense
          FROM transactions
          WHERE user_id = :userId
            AND EXTRACT(YEAR FROM transaction_date) = :year
          GROUP BY date_trunc('month', transaction_date::TIMESTAMP)
          ORDER BY date_trunc('month', transaction_date::TIMESTAMP) ASC
      """, nativeQuery = true)
  List<Object[]> findMonthlyChartData(
      @Param("userId") UUID userId,
      @Param("year") int year
  );

  // ✅ Query này dùng JPQL thuần — không cần sửa vì không có MONTH()/YEAR()
  @Query("""
          SELECT t.transactionDate                                                     AS date,
                 SUM(CASE WHEN t.type = 'INCOME'  THEN t.amount ELSE 0 END)           AS income,
                 SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END)           AS expense
          FROM Transaction t
          WHERE t.user.id = :userId
            AND t.transactionDate >= :startDate
            AND t.transactionDate <= :endDate
          GROUP BY t.transactionDate
          ORDER BY t.transactionDate ASC
      """)
  List<Object[]> findDailyChartData(
      @Param("userId") UUID userId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate
  );
}