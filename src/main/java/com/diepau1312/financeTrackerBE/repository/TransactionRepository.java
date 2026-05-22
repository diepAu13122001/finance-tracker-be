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
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

  // ── Existing queries (giữ nguyên) ─────────────────────────────────────────

  Page<Transaction> findByUserIdOrderByTransactionDateDesc(UUID userId, Pageable pageable);

  Page<Transaction> findByUserIdAndTypeOrderByTransactionDateDesc(UUID userId, TransactionType type, Pageable pageable);

  @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.source != 'transfer_in' ORDER BY t.transactionDate DESC, t.createdAt DESC")
  Page<Transaction> findByUserIdExcludeTransferIn(@Param("userId") UUID userId, Pageable pageable);

  @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.type = :type AND t.source != 'transfer_in' ORDER BY t.transactionDate DESC, t.createdAt DESC")
  Page<Transaction> findByUserIdAndTypeExcludeTransferIn(@Param("userId") UUID userId,
                                                         @Param("type") TransactionType type, Pageable pageable);

  @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.source = 'transfer_out' ORDER BY t.transactionDate DESC, t.createdAt DESC")
  Page<Transaction> findTransfersByUserId(@Param("userId") UUID userId, Pageable pageable);

  Page<Transaction> findByUser_IdAndCategory_IdOrderByTransactionDateDescCreatedAtDesc(UUID userId, UUID categoryId, Pageable pageable);

  Page<Transaction> findByUser_IdAndWallet_IdOrderByTransactionDateDescCreatedAtDesc(UUID userId, UUID walletId, Pageable pageable);

  // ── THÊM MỚI: Text search ─────────────────────────────────────────────────

  /**
   * Tìm kiếm theo text trong note hoặc tên ví.
   * <p>
   * Giải thích JPQL:
   * - LOWER() cả 2 vế → case-insensitive search
   * - CONCAT('%', :search, '%') → SQL LIKE '%từ_tìm%'
   * - source != 'transfer_in' → ẩn bản sao transfer, chỉ hiện transfer_out
   * - Tìm cả wallet.name vì user hay nhớ "mua sắm bằng thẻ Visa"
   */
  @Query("""
      SELECT t FROM Transaction t
      WHERE t.user.id = :userId
        AND t.source != 'transfer_in'
        AND (LOWER(t.note) LIKE LOWER(CONCAT('%', :search, '%'))
             OR (t.wallet IS NOT NULL
                 AND LOWER(t.wallet.name) LIKE LOWER(CONCAT('%', :search, '%'))))
      ORDER BY t.transactionDate DESC, t.createdAt DESC
      """)
  Page<Transaction> searchByText(
      @Param("userId") UUID userId,
      @Param("search") String search,
      Pageable pageable);

  /**
   * Search + filter type kết hợp.
   * Dùng khi user đang ở tab INCOME/EXPENSE rồi gõ tìm kiếm.
   */
  @Query("""
      SELECT t FROM Transaction t
      WHERE t.user.id = :userId
        AND t.type = :type
        AND t.source != 'transfer_in'
        AND (LOWER(t.note) LIKE LOWER(CONCAT('%', :search, '%'))
             OR (t.wallet IS NOT NULL
                 AND LOWER(t.wallet.name) LIKE LOWER(CONCAT('%', :search, '%'))))
      ORDER BY t.transactionDate DESC, t.createdAt DESC
      """)
  Page<Transaction> searchByTextAndType(
      @Param("userId") UUID userId,
      @Param("search") String search,
      @Param("type") TransactionType type,
      Pageable pageable);

  // ── Count & Sum queries ────────────────────────────────────────────────────

  @Query("""
      SELECT COUNT(t) FROM Transaction t
      WHERE t.user.id = :userId
        AND t.transactionDate >= :startDate
        AND t.transactionDate <= :endDate
        AND t.source != 'transfer_in'
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

  @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.wallet.id = :walletId AND t.type = :type")
  Long sumAmountByWalletIdAndType(@Param("walletId") UUID walletId, @Param("type") TransactionType type);

  @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.wallet.id = :walletId AND t.source = :source")
  Long sumTransferByWalletIdAndSource(@Param("walletId") UUID walletId, @Param("source") String source);

  // ── Transfer pair ─────────────────────────────────────────────────────────

  Optional<Transaction> findByTransferPairIdAndIdNot(UUID transferPairId, UUID id);

  // ── Chart queries ─────────────────────────────────────────────────────────

  @Query("""
      SELECT t.transactionDate AS date,
             SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END) AS income,
             SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END) AS expense
      FROM Transaction t
      WHERE t.user.id = :userId
        AND t.transactionDate >= :startDate
        AND t.transactionDate <= :endDate
        AND t.source NOT IN ('transfer_out', 'transfer_in')
      GROUP BY t.transactionDate
      ORDER BY t.transactionDate ASC
      """)
  List<Object[]> findDailyChartData(@Param("userId") UUID userId,
                                    @Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate);

  @Query(value = """
      SELECT EXTRACT(MONTH FROM transaction_date)::INT AS month,
             SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) AS income,
             SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) AS expense
      FROM transactions
      WHERE user_id = :userId
        AND EXTRACT(YEAR FROM transaction_date) = :year
        AND source NOT IN ('transfer_out', 'transfer_in')
      GROUP BY EXTRACT(MONTH FROM transaction_date)
      ORDER BY EXTRACT(MONTH FROM transaction_date)
      """, nativeQuery = true)
  List<Object[]> findMonthlyChartData(@Param("userId") UUID userId, @Param("year") int year);

  @Query(value = """
      SELECT c.id, c.name, c.color,
             SUM(t.amount) AS total_amount, COUNT(t.id) AS transaction_count
      FROM transactions t
      LEFT JOIN categories c ON t.category_id = c.id
      WHERE t.user_id = CAST(:userId AS uuid)
        AND t.type = :type
        AND EXTRACT(YEAR FROM t.transaction_date) = :year
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
        AND EXTRACT(YEAR FROM t.transaction_date) = :year
        AND EXTRACT(MONTH FROM t.transaction_date) BETWEEN :startMonth AND :endMonth
      GROUP BY c.id, c.name, c.color
      ORDER BY total_amount DESC
      """, nativeQuery = true)
  List<Object[]> findCategoryBreakdownByRange(@Param("userId") UUID userId, @Param("type") String type,
                                              @Param("year") int year, @Param("startMonth") int startMonth, @Param("endMonth") int endMonth);

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

  // ── Export query — filter theo date range ─────────────────────────────────

  /**
   * Lấy transactions trong khoảng ngày cho export Excel/PDF.
   * <p>
   * Tại sao cần query riêng thay vì dùng findByUserIdOrderByTransactionDateDesc?
   * → Query cũ không có filter ngày → luôn trả về toàn bộ bất kể year/month truyền vào
   * → Query này filter đúng [startDate, endDate] → export đúng kỳ user chọn
   * <p>
   * source != 'transfer_in': ẩn bản sao transfer, tránh double-count số tiền
   */
  @Query("""
      SELECT t FROM Transaction t
      WHERE t.user.id = :userId
        AND t.transactionDate >= :startDate
        AND t.transactionDate <= :endDate
        AND t.source != 'transfer_in'
      ORDER BY t.transactionDate DESC, t.createdAt DESC
      """)
  Page<Transaction> findByUserIdAndDateBetween(
      @Param("userId") UUID userId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      Pageable pageable);
}