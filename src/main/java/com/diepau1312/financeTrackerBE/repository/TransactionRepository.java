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

  // Lấy danh sách giao dịch của user, sắp xếp mới nhất — có phân trang
  Page<Transaction> findByUserIdOrderByTransactionDateDesc(
      UUID userId, Pageable pageable);

  Page<Transaction> findByUserIdAndTypeOrderByTransactionDateDesc(
      UUID userId, Transaction.TransactionType type, Pageable pageable);

  // Đếm giao dịch trong tháng hiện tại — dùng để check giới hạn Free plan
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

  // Tổng thu/chi theo type trong khoảng thời gian — dùng cho summary cards
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

  @Query("""
          SELECT t.transactionDate as date,
                 SUM(CASE WHEN t.type = 'INCOME'  THEN t.amount ELSE 0 END) as income,
                 SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END) as expense
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

  @Query("""
          SELECT MONTH(t.transactionDate) as month,
                 SUM(CASE WHEN t.type = 'INCOME'  THEN t.amount ELSE 0 END) as income,
                 SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END) as expense
          FROM Transaction t
          WHERE t.user.id = :userId
            AND YEAR(t.transactionDate) = :year
          GROUP BY MONTH(t.transactionDate)
          ORDER BY MONTH(t.transactionDate) ASC
      """)
  List<Object[]> findMonthlyChartData(
      @Param("userId") UUID userId,
      @Param("year") int year
  );
}