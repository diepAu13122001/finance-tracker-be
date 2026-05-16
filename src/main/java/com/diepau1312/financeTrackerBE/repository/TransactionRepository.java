package com.diepau1312.financeTrackerBE.repository;

import com.diepau1312.financeTrackerBE.entity.Transaction;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
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

  Page<Transaction> findByUserIdOrderByTransactionDateDesc(UUID userId, Pageable pageable);

  Page<Transaction> findByUserIdAndTypeOrderByTransactionDateDesc(UUID userId, TransactionType type, Pageable pageable);

  Page<Transaction> findByUser_IdAndCategory_IdOrderByTransactionDateDesc(UUID userId, UUID categoryId, Pageable pageable);

  // 👇 ĐỔI: goal → wallet
  Page<Transaction> findByUser_IdAndWallet_IdOrderByTransactionDateDesc(UUID userId, UUID walletId, Pageable pageable);

  @Query("""
          SELECT COUNT(t) FROM Transaction t
          WHERE t.user.id = :userId
            AND t.transactionDate >= :startDate
            AND t.transactionDate <= :endDate
      """)
  long countByUserIdAndDateBetween(@Param("userId") UUID userId,
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate);

  @Query("""
          SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
          WHERE t.user.id = :userId
            AND t.type = :type
            AND t.transactionDate >= :startDate
            AND t.transactionDate <= :endDate
      """)
  Long sumAmountByUserIdAndTypeAndDateBetween(@Param("userId") UUID userId,
                                              @Param("type") TransactionType type,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);

  @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
      "WHERE t.wallet.id = :walletId AND t.type = :type")
  Long sumAmountByWalletIdAndType(@Param("walletId") UUID walletId,
                                  @Param("type") TransactionType type);

  // ── Daily chart ───────────────────────────────────────────────────────────

  @Query("""
          SELECT t.transactionDate AS date,
                 SUM(CASE WHEN t.type = 'INCOME'  THEN t.amount ELSE 0 END) AS income,
                 SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END) AS expense
          FROM Transaction t
          WHERE t.user.id = :userId
            AND t.transactionDate >= :startDate
            AND t.transactionDate <= :endDate
          GROUP BY t.transactionDate
          ORDER BY t.transactionDate ASC
      """)
  List<Object[]> findDailyChartData(@Param("userId") UUID userId,
                                    @Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate);

  // ── Monthly chart ─────────────────────────────────────────────────────────

  @Query(value = """
          SELECT EXTRACT(MONTH FROM transaction_date)::INT AS month,
                 SUM(CASE WHEN type = 'INCOME'  THEN amount ELSE 0 END) AS income,
                 SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) AS expense
          FROM transactions
          WHERE user_id = :userId
            AND EXTRACT(YEAR FROM transaction_date) = :year
          GROUP BY EXTRACT(MONTH FROM transaction_date)
          ORDER BY EXTRACT(MONTH FROM transaction_date)
      """, nativeQuery = true)
  List<Object[]> findMonthlyChartData(@Param("userId") UUID userId, @Param("year") int year);

  // ── Category breakdown ────────────────────────────────────────────────────

  @Query(value = """
          SELECT c.id, c.name, c.color,
                 SUM(t.amount) AS total_amount, COUNT(t.id) AS transaction_count
          FROM transactions t
          LEFT JOIN categories c ON t.category_id = c.id
          WHERE t.user_id = CAST(:userId AS uuid)
            AND t.type = :type
            AND EXTRACT(YEAR  FROM t.transaction_date) = :year
            AND EXTRACT(MONTH FROM t.transaction_date) = :month
          GROUP BY c.id, c.name, c.color
          ORDER BY total_amount DESC
      """, nativeQuery = true)
  List<Object[]> findCategoryBreakdown(@Param("userId") UUID userId, @Param("type") String type,
                                       @Param("year") int year, @Param("month") int month);

  @Query(value = """
          SELECT c.id, c.name, c.color,
                 SUM(t.amount) AS total_amount, COUNT(t.id) AS transaction_count
          FROM transactions t
          LEFT JOIN categories c ON t.category_id = c.id
          WHERE t.user_id = CAST(:userId AS uuid)
            AND t.type = :type
            AND EXTRACT(YEAR  FROM t.transaction_date) = :year
            AND EXTRACT(MONTH FROM t.transaction_date) BETWEEN :startMonth AND :endMonth
          GROUP BY c.id, c.name, c.color
          ORDER BY total_amount DESC
      """, nativeQuery = true)
  List<Object[]> findCategoryBreakdownByRange(@Param("userId") UUID userId, @Param("type") String type,
                                              @Param("year") int year, @Param("startMonth") int startMonth,
                                              @Param("endMonth") int endMonth);

  @Query(value = """
          SELECT c.id, c.name, c.color,
                 SUM(t.amount) AS total_amount, COUNT(t.id) AS transaction_count
          FROM transactions t
          LEFT JOIN categories c ON t.category_id = c.id
          WHERE t.user_id = CAST(:userId AS uuid)
            AND t.type = :type
            AND EXTRACT(YEAR FROM t.transaction_date) = :year
          GROUP BY c.id, c.name, c.color
          ORDER BY total_amount DESC
      """, nativeQuery = true)
  List<Object[]> findCategoryBreakdownByYear(@Param("userId") UUID userId, @Param("type") String type,
                                             @Param("year") int year);
}