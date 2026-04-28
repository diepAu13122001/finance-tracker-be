package com.diepau1312.financeTrackerBE.repository;

import com.diepau1312.financeTrackerBE.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // Lấy danh sách giao dịch của user, sắp xếp mới nhất — có phân trang
    Page<Transaction> findByUserIdOrderByTransactionDateDesc(UUID userId, Pageable pageable);

    // Đếm giao dịch trong tháng hiện tại — dùng để check giới hạn Free plan
    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE t.user.id = :userId
          AND t.transactionDate >= :startDate
          AND t.transactionDate <= :endDate
    """)
    long countByUserIdAndDateBetween(
            @Param("userId")    UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate
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
            @Param("userId")    UUID userId,
            @Param("type")      Transaction.TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate
    );
}