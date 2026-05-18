package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.category.CategoryResponse;
import com.diepau1312.financeTrackerBE.dto.transaction.*;
import com.diepau1312.financeTrackerBE.dto.wallet.WalletResponse;
import com.diepau1312.financeTrackerBE.entity.*;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import com.diepau1312.financeTrackerBE.entity.Wallet.WalletSubtype;
import com.diepau1312.financeTrackerBE.entity.Wallet.WalletType;
import com.diepau1312.financeTrackerBE.exception.*;
import com.diepau1312.financeTrackerBE.repository.*;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class TransactionService {

  private static final int FREE_PLAN_LIMIT = 50;

  private final TransactionRepository transactionRepository;
  private final UserRepository userRepository;
  private final UserSubscriptionRepository subscriptionRepository;
  private final CategoryRepository categoryRepository;
  private final WalletRepository walletRepository;
  private final WalletService walletService;

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private User getCurrentUser() {
    return userRepository.findByEmail(SecurityUtil.getCurrentUserEmail()).orElseThrow(() -> new NotFoundException("Không tìm thấy user"));
  }

  private String getCurrentUserPlan(UUID userId) {
    return subscriptionRepository.findByUserId(userId).map(sub -> sub.getPlanId()).orElse("FREE");
  }

  private void checkTransactionLimit(UUID userId, String planId) {
    if (!"FREE".equals(planId)) return;
    LocalDate start = LocalDate.now().withDayOfMonth(1);
    LocalDate end = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
    long count = transactionRepository.countByUserIdAndDateBetween(userId, start, end);
    if (count >= FREE_PLAN_LIMIT) throw new PlanUpgradeRequiredException("PLUS", null);
  }

  /**
   * Build TransactionResponse kèm thông tin transfer source/target wallet names.
   * Cần tra cứu linkedWallet name nếu là transfer.
   */
  private TransactionResponse toResponse(Transaction t) {
    String transferSourceWalletName = null;
    String transferTargetWalletName = null;

    if (t.getTransferPairId() != null && t.getLinkedWalletId() != null) {
      String linkedName = walletRepository.findById(t.getLinkedWalletId()).map(Wallet::getName).orElse("Không xác định");
      String thisWalletName = t.getWallet() != null ? t.getWallet().getName() : "Không xác định";

      if ("transfer_out".equals(t.getSource())) {
        // Đây là bên nguồn
        transferSourceWalletName = thisWalletName;
        transferTargetWalletName = linkedName;
      } else {
        // Đây là bên đích (transfer_in)
        transferSourceWalletName = linkedName;
        transferTargetWalletName = thisWalletName;
      }
    }

    return TransactionResponse.builder().id(t.getId()).type(t.getType()).amount(t.getAmount()).currency(t.getCurrency()).note(t.getNote()).transactionDate(t.getTransactionDate()).source(t.getSource()).createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt()).category(t.getCategory() != null ? CategoryResponse.from(t.getCategory()) : null).wallet(t.getWallet() != null ? WalletResponse.from(t.getWallet()) : null).transferPairId(t.getTransferPairId()).transferSourceWalletName(transferSourceWalletName).transferTargetWalletName(transferTargetWalletName).build();
  }

  /**
   * Validate các ràng buộc business của wallet:
   * 1. NORMAL wallet: EXPENSE không vượt quá số dư
   * 2. INSTALLMENT wallet: không cho phép EXPENSE (chỉ INCOME = trả góp)
   */
  private void validateWalletForTransaction(Wallet wallet, TransactionType type, Long amount) {
    if (wallet == null) return;

    // Rule 3: CREDIT wallet không thể vượt hạn mức
    if (wallet.getType() == WalletType.DEBT && wallet.getSubtype() == WalletSubtype.CREDIT_CARD && type == TransactionType.EXPENSE) {
      if (wallet.getCurrentAmount() + amount > wallet.getCreditLimit()) {
        throw new AuthException("Số dư ví '" + wallet.getName() + "' không đủ. " + "Số dư hiện tại: " + (wallet.getCreditLimit() - wallet.getCurrentAmount()) + " VND.");
      }
    }


    // Rule 2: INSTALLMENT chỉ nhận INCOME
    if (wallet.getType() == WalletType.DEBT && wallet.getSubtype() == WalletSubtype.INSTALLMENT && type == TransactionType.EXPENSE) {
      throw new AuthException("Ví trả góp '" + wallet.getName() + "' chỉ nhận giao dịch thu (thanh toán kỳ). Không thể thêm chi tiêu.");
    }

    // Rule 1: NORMAL wallet không chi tiêu vượt số dư
    if (wallet.getType() == WalletType.NORMAL && type == TransactionType.EXPENSE) {
      if (wallet.getCurrentAmount() < amount) {
        throw new AuthException("Số dư ví '" + wallet.getName() + "' không đủ. " + "Số dư hiện tại: " + wallet.getCurrentAmount() + " VND.");
      }
    }
  }

  // ─── CRUD ─────────────────────────────────────────────────────────────────

  @Transactional
  @CacheEvict(value = "transaction-summary", allEntries = true)
  public TransactionResponse create(TransactionRequest request) {
    User user = getCurrentUser();
    String planId = getCurrentUserPlan(user.getId());
    checkTransactionLimit(user.getId(), planId);

    // ── TRANSFER: special handling ─────────────────────────────────────────
    if (request.getType() == TransactionType.TRANSFER) {
      return createTransfer(request, user);
    }

    // ── INCOME / EXPENSE ───────────────────────────────────────────────────

    Category category = null;
    if (request.getCategoryId() != null) {
      category = categoryRepository.findByIdAndUserId(request.getCategoryId(), user.getId()).orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));
      if (category.getType() != request.getType()) {
        throw new AuthException("Danh mục '" + category.getName() + "' không khớp với loại giao dịch");
      }
    }

    Wallet wallet = null;
    if (request.getWalletId() != null) {
      wallet = walletRepository.findByIdAndUserId(request.getWalletId(), user.getId()).orElseThrow(() -> new NotFoundException("Không tìm thấy ví"));
    }

    // Validate wallet rules
    validateWalletForTransaction(wallet, request.getType(), request.getAmount());

    Transaction transaction = Transaction.builder().user(user).type(request.getType()).amount(request.getAmount()).currency(request.getCurrency() != null ? request.getCurrency() : "VND").note(request.getNote()).transactionDate(request.getTransactionDate()).source("manual").category(category).wallet(wallet).build();

    TransactionResponse result = toResponse(transactionRepository.save(transaction));
    if (wallet != null) walletService.recalculateBalance(wallet.getId());
    return result;
  }

  /**
   * Tạo 2 transactions cho 1 lần transfer:
   * - transfer_out trên sourceWallet (giảm số dư)
   * - transfer_in trên targetWallet (tăng số dư)
   * Trả về transaction phía source (canonical representation).
   */
  private TransactionResponse createTransfer(TransactionRequest request, User user) {
    if (request.getWalletId() == null || request.getTargetWalletId() == null) {
      throw new AuthException("Chuyển đổi cần có cả ví nguồn và ví đích");
    }
    if (request.getWalletId().equals(request.getTargetWalletId())) {
      throw new AuthException("Ví nguồn và ví đích không thể giống nhau");
    }

    Wallet sourceWallet = walletRepository.findByIdAndUserId(request.getWalletId(), user.getId()).orElseThrow(() -> new NotFoundException("Không tìm thấy ví nguồn"));
    Wallet targetWallet = walletRepository.findByIdAndUserId(request.getTargetWalletId(), user.getId()).orElseThrow(() -> new NotFoundException("Không tìm thấy ví đích"));

    // Validate đủ số dư cho ví nguồn NORMAL
    if (sourceWallet.getType() == WalletType.NORMAL) {
      if (sourceWallet.getCurrentAmount() < request.getAmount()) {
        throw new AuthException("Số dư ví '" + sourceWallet.getName() + "' không đủ để chuyển. " + "Số dư: " + sourceWallet.getCurrentAmount() + " VND.");
      }
    }

    // INSTALLMENT không thể làm nguồn chuyển (vì nó không có tiền để chuyển)
    if (sourceWallet.getType() == WalletType.DEBT && sourceWallet.getSubtype() == WalletSubtype.INSTALLMENT) {
      throw new AuthException("Ví trả góp không thể là ví nguồn khi chuyển tiền");
    }

    UUID pairId = UUID.randomUUID();

    // Transaction phía nguồn (tiền ra)
    Transaction sourceTransaction = Transaction.builder().user(user).type(TransactionType.TRANSFER).amount(request.getAmount()).currency(request.getCurrency() != null ? request.getCurrency() : "VND").note(request.getNote()).transactionDate(request.getTransactionDate()).source("transfer_out").wallet(sourceWallet).transferPairId(pairId).linkedWalletId(targetWallet.getId()).build();

    // Transaction phía đích (tiền vào)
    Transaction targetTransaction = Transaction.builder().user(user).type(TransactionType.TRANSFER).amount(request.getAmount()).currency(request.getCurrency() != null ? request.getCurrency() : "VND").note(request.getNote()).transactionDate(request.getTransactionDate()).source("transfer_in").wallet(targetWallet).transferPairId(pairId).linkedWalletId(sourceWallet.getId()).build();

    transactionRepository.save(sourceTransaction);
    transactionRepository.save(targetTransaction);

    walletService.recalculateBalance(sourceWallet.getId());
    walletService.recalculateBalance(targetWallet.getId());

    return toResponse(sourceTransaction);
  }

  @Transactional(readOnly = true)
  public Page<TransactionResponse> getAll(int page, int size, String type, UUID categoryId, UUID walletId) {
    User user = getCurrentUser();
    Pageable pageable = PageRequest.of(page, size);
    Page<Transaction> result;

    // walletId filter: dùng cho WalletTransactionsDrawer — bao gồm cả transfer
    if (walletId != null) {
      result = transactionRepository.findByUser_IdAndWallet_IdOrderByTransactionDateDescCreatedAtDesc(user.getId(), walletId, pageable);
    } else if (categoryId != null) {
      result = transactionRepository.findByUser_IdAndCategory_IdOrderByTransactionDateDescCreatedAtDesc(user.getId(), categoryId, pageable);
    } else if (type != null && !type.isBlank()) {
      TransactionType txType = TransactionType.valueOf(type.toUpperCase());
      if (txType == TransactionType.TRANSFER) {
        // TRANSFER: chỉ show transfer_out (1 item per transfer)
        result = transactionRepository.findTransfersByUserId(user.getId(), pageable);
      } else {
        result = transactionRepository.findByUserIdAndTypeExcludeTransferIn(user.getId(), txType, pageable);
      }
    } else {
      // ALL: loại bỏ transfer_in, giữ transfer_out
      result = transactionRepository.findByUserIdExcludeTransferIn(user.getId(), pageable);
    }

    return result.map(this::toResponse);
  }

  @Transactional
  @CacheEvict(value = "transaction-summary", allEntries = true)
  public TransactionResponse update(UUID id, TransactionRequest request) {
    User user = getCurrentUser();
    Transaction transaction = transactionRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch"));

    if (!transaction.getUser().getId().equals(user.getId())) {
      throw new ForbiddenException("Không có quyền sửa giao dịch này");
    }

    // Transfer không hỗ trợ edit (quá phức tạp) — phải xóa và tạo mới
    if (transaction.getTransferPairId() != null) {
      throw new AuthException("Không thể sửa giao dịch chuyển đổi. Hãy xóa và tạo mới.");
    }

    Category category = null;
    if (request.getCategoryId() != null) {
      category = categoryRepository.findByIdAndUserId(request.getCategoryId(), user.getId()).orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));
      if (category.getType() != request.getType()) {
        throw new AuthException("Danh mục không khớp với loại giao dịch");
      }
    }

    UUID oldWalletId = transaction.getWallet() != null ? transaction.getWallet().getId() : null;
    Wallet newWallet = null;
    if (request.getWalletId() != null) {
      newWallet = walletRepository.findByIdAndUserId(request.getWalletId(), user.getId()).orElseThrow(() -> new NotFoundException("Không tìm thấy ví"));
    }

    // Validate wallet cho type mới
    validateWalletForTransaction(newWallet, request.getType(), request.getAmount());

    transaction.setType(request.getType());
    transaction.setAmount(request.getAmount());
    transaction.setNote(request.getNote());
    transaction.setTransactionDate(request.getTransactionDate());
    transaction.setCurrency(request.getCurrency() != null ? request.getCurrency() : "VND");
    transaction.setCategory(category);
    transaction.setWallet(newWallet);

    TransactionResponse result = toResponse(transactionRepository.save(transaction));

    UUID newWalletId = newWallet != null ? newWallet.getId() : null;
    if (oldWalletId != null) walletService.recalculateBalance(oldWalletId);
    if (newWalletId != null && !newWalletId.equals(oldWalletId)) {
      walletService.recalculateBalance(newWalletId);
    }

    return result;
  }

  @Transactional
  @CacheEvict(value = "transaction-summary", allEntries = true)
  public void delete(UUID id) {
    User user = getCurrentUser();
    Transaction transaction = transactionRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch"));

    if (!transaction.getUser().getId().equals(user.getId())) {
      throw new ForbiddenException("Không có quyền xóa giao dịch này");
    }

    UUID walletId = transaction.getWallet() != null ? transaction.getWallet().getId() : null;

    // Nếu là transfer: xóa cả 2 transaction trong cặp
    if (transaction.getTransferPairId() != null) {
      Optional<Transaction> paired = transactionRepository.findByTransferPairIdAndIdNot(transaction.getTransferPairId(), transaction.getId());

      paired.ifPresent(pairedTx -> {
        UUID pairedWalletId = pairedTx.getWallet() != null ? pairedTx.getWallet().getId() : null;
        transactionRepository.delete(pairedTx);
        if (pairedWalletId != null) walletService.recalculateBalance(pairedWalletId);
      });
    }

    transactionRepository.delete(transaction);
    if (walletId != null) walletService.recalculateBalance(walletId);
  }

  // ─── Summary & Charts ─────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  @Cacheable(value = "transaction-summary", key = "T(org.springframework.security.core.context.SecurityContextHolder)" + ".getContext().getAuthentication().getName()" + " + '-' + #year + '-' + #month")
  public TransactionSummaryResponse getSummary(Integer year, Integer month, Integer quarter) {
    User user = getCurrentUser();
    String planId = getCurrentUserPlan(user.getId());

    LocalDate startDate, endDate;
    LocalDate today = LocalDate.now();
    int targetYear = year != null ? year : today.getYear();

    if (quarter != null) {
      int startMonth = (quarter - 1) * 3 + 1;
      int endMonth = startMonth + 2;
      startDate = LocalDate.of(targetYear, startMonth, 1);
      endDate = LocalDate.of(targetYear, endMonth, 1).withDayOfMonth(LocalDate.of(targetYear, endMonth, 1).lengthOfMonth());
    } else if (month != null) {
      startDate = LocalDate.of(targetYear, month, 1);
      endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
    } else if (year != null) {
      startDate = LocalDate.of(targetYear, 1, 1);
      endDate = LocalDate.of(targetYear, 12, 31);
    } else {
      startDate = today.withDayOfMonth(1);
      endDate = today.withDayOfMonth(today.lengthOfMonth());
    }

    Long totalIncome = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INCOME, startDate, endDate);
    Long totalExpense = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, startDate, endDate);
    long count = transactionRepository.countByUserIdAndDateBetween(user.getId(), startDate, endDate);
    int limit = "FREE".equals(planId) ? FREE_PLAN_LIMIT : -1;

    return TransactionSummaryResponse.builder().totalIncome(totalIncome != null ? totalIncome : 0L).totalExpense(totalExpense != null ? totalExpense : 0L).balance((totalIncome != null ? totalIncome : 0L) - (totalExpense != null ? totalExpense : 0L)).transactionCount(count).transactionLimit(limit).limitReached("FREE".equals(planId) && count >= FREE_PLAN_LIMIT).startDate(startDate).endDate(endDate).build();
  }

  public List<DailyChartResponse> getDailyChart(Integer year, Integer month, Integer startMonth, Integer endMonth) {
    User user = getCurrentUser();
    LocalDate today = LocalDate.now();
    int targetYear = year != null ? year : today.getYear();
    LocalDate startDate, endDate;

    if (startMonth != null && endMonth != null) {
      startDate = LocalDate.of(targetYear, startMonth, 1);
      endDate = LocalDate.of(targetYear, endMonth, 1).withDayOfMonth(LocalDate.of(targetYear, endMonth, 1).lengthOfMonth());
    } else {
      int targetMonth = month != null ? month : today.getMonthValue();
      startDate = LocalDate.of(targetYear, targetMonth, 1);
      endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
    }

    return transactionRepository.findDailyChartData(user.getId(), startDate, endDate).stream().map(row -> DailyChartResponse.builder().date(row[0].toString()).income(row[1] != null ? ((Number) row[1]).longValue() : 0L).expense(row[2] != null ? ((Number) row[2]).longValue() : 0L).build()).toList();
  }

  public List<MonthlyChartResponse> getMonthlyChart(Integer year) {
    User user = getCurrentUser();
    int targetYear = year != null ? year : LocalDate.now().getYear();
    List<Object[]> rows = transactionRepository.findMonthlyChartData(user.getId(), targetYear);
    Map<Integer, Object[]> rowMap = rows.stream().collect(Collectors.toMap(row -> ((Number) row[0]).intValue(), row -> row));
    String[] labels = {"Th1", "Th2", "Th3", "Th4", "Th5", "Th6", "Th7", "Th8", "Th9", "Th10", "Th11", "Th12"};

    return IntStream.rangeClosed(1, 12).mapToObj(m -> {
      Object[] row = rowMap.get(m);
      long income = row != null && row[1] != null ? ((Number) row[1]).longValue() : 0L;
      long expense = row != null && row[2] != null ? ((Number) row[2]).longValue() : 0L;
      return MonthlyChartResponse.builder().month(m).label(labels[m - 1]).income(income).expense(expense).balance(income - expense).build();
    }).toList();
  }

  @Transactional(readOnly = true)
  public List<CategoryChartItem> getCategoryChart(TransactionType type, int year, Integer month, Integer startMonth, Integer endMonth) {
    User user = getCurrentUser();
    List<Object[]> rows;

    if (startMonth != null && endMonth != null) {
      rows = transactionRepository.findCategoryBreakdownByRange(user.getId(), type.name(), year, startMonth, endMonth);
    } else if (month != null) {
      rows = transactionRepository.findCategoryBreakdown(user.getId(), type.name(), year, month);
    } else {
      rows = transactionRepository.findCategoryBreakdownByYear(user.getId(), type.name(), year);
    }

    long totalAmount = rows.stream().mapToLong(r -> r[3] != null ? ((Number) r[3]).longValue() : 0L).sum();

    return rows.stream().map(r -> {
      UUID catId = r[0] != null ? (UUID) r[0] : null;
      long amount = r[3] != null ? ((Number) r[3]).longValue() : 0L;
      long count = r[4] != null ? ((Number) r[4]).longValue() : 0L;
      double pct = totalAmount > 0 ? (amount * 100.0) / totalAmount : 0.0;

      return CategoryChartItem.builder().categoryId(catId).categoryName(r[1] != null ? (String) r[1] : "Chưa phân loại").categoryColor(r[2] != null ? (String) r[2] : "#888888").totalAmount(amount).transactionCount(count).percentage(Math.round(pct * 10.0) / 10.0).build();
    }).toList();
  }
}