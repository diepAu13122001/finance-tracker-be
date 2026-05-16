package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.category.CategoryResponse;
import com.diepau1312.financeTrackerBE.dto.transaction.*;
import com.diepau1312.financeTrackerBE.dto.wallet.WalletResponse;
import com.diepau1312.financeTrackerBE.entity.*;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
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
    return userRepository.findByEmail(SecurityUtil.getCurrentUserEmail())
        .orElseThrow(() -> new NotFoundException("Không tìm thấy user"));
  }

  private String getCurrentUserPlan(UUID userId) {
    return subscriptionRepository.findByUserId(userId)
        .map(sub -> sub.getPlanId()).orElse("FREE");
  }

  private void checkTransactionLimit(UUID userId, String planId) {
    if (!"FREE".equals(planId)) return;
    LocalDate start = LocalDate.now().withDayOfMonth(1);
    LocalDate end = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
    long count = transactionRepository.countByUserIdAndDateBetween(userId, start, end);
    if (count >= FREE_PLAN_LIMIT) throw new PlanUpgradeRequiredException("PLUS", null);
  }

  private TransactionResponse toResponse(Transaction t) {
    return TransactionResponse.builder()
        .id(t.getId())
        .type(t.getType())
        .amount(t.getAmount())
        .currency(t.getCurrency())
        .note(t.getNote())
        .transactionDate(t.getTransactionDate())
        .source(t.getSource())
        .createdAt(t.getCreatedAt())
        .updatedAt(t.getUpdatedAt())
        .category(t.getCategory() != null ? CategoryResponse.from(t.getCategory()) : null)
        .wallet(t.getWallet() != null ? WalletResponse.from(t.getWallet()) : null)
        .build();
  }

  // ─── CRUD ─────────────────────────────────────────────────────────────────

  @Transactional
  @CacheEvict(value = "transaction-summary", allEntries = true)
  public TransactionResponse create(TransactionRequest request) {
    User user = getCurrentUser();
    String planId = getCurrentUserPlan(user.getId());
    checkTransactionLimit(user.getId(), planId);

    Category category = null;
    if (request.getCategoryId() != null) {
      category = categoryRepository.findByIdAndUserId(request.getCategoryId(), user.getId())
          .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));
      if (category.getType() != request.getType())
        throw new AuthException("Danh mục " + category.getName() + " không khớp với loại giao dịch");
    }

    Wallet wallet = null;
    if (request.getWalletId() != null) {
      wallet = walletRepository.findByIdAndUserId(request.getWalletId(), user.getId())
          .orElseThrow(() -> new NotFoundException("Không tìm thấy ví"));
    }

    Transaction transaction = Transaction.builder()
        .user(user)
        .type(request.getType())
        .amount(request.getAmount())
        .currency(request.getCurrency())
        .note(request.getNote())
        .transactionDate(request.getTransactionDate())
        .source("manual")
        .category(category)
        .wallet(wallet)
        .build();

    TransactionResponse result = toResponse(transactionRepository.save(transaction));
    if (wallet != null) walletService.recalculateBalance(wallet.getId());
    return result;
  }

  @Transactional(readOnly = true)
  public Page<TransactionResponse> getAll(int page, int size, String type, UUID categoryId) {
    User user = getCurrentUser();
    Pageable pageable = PageRequest.of(page, size);
    Page<Transaction> result;

    if (categoryId != null) {
      result = transactionRepository
          .findByUser_IdAndCategory_IdOrderByTransactionDateDesc(user.getId(), categoryId, pageable);
    } else if (type != null && !type.isBlank()) {
      TransactionType txType = TransactionType.valueOf(type.toUpperCase());
      result = transactionRepository
          .findByUserIdAndTypeOrderByTransactionDateDesc(user.getId(), txType, pageable);
    } else {
      result = transactionRepository.findByUserIdOrderByTransactionDateDesc(user.getId(), pageable);
    }

    return result.map(this::toResponse);
  }

  @Transactional
  @CacheEvict(value = "transaction-summary", allEntries = true)
  public TransactionResponse update(UUID id, TransactionRequest request) {
    User user = getCurrentUser();
    Transaction transaction = transactionRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch"));

    if (!transaction.getUser().getId().equals(user.getId()))
      throw new ForbiddenException("Không có quyền sửa giao dịch này");

    Category category = null;
    if (request.getCategoryId() != null) {
      category = categoryRepository.findByIdAndUserId(request.getCategoryId(), user.getId())
          .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));
      if (category.getType() != request.getType())
        throw new AuthException("Danh mục " + category.getName() + " không khớp với loại giao dịch");
    }

    // 👇 ĐỔI
    UUID oldWalletId = transaction.getWallet() != null ? transaction.getWallet().getId() : null;
    Wallet newWallet = null;
    if (request.getWalletId() != null) {
      newWallet = walletRepository.findByIdAndUserId(request.getWalletId(), user.getId())
          .orElseThrow(() -> new NotFoundException("Không tìm thấy ví"));
    }

    transaction.setType(request.getType());
    transaction.setAmount(request.getAmount());
    transaction.setNote(request.getNote());
    transaction.setTransactionDate(request.getTransactionDate());
    transaction.setCurrency(request.getCurrency());
    transaction.setCategory(category);
    transaction.setWallet(newWallet);

    TransactionResponse result = toResponse(transactionRepository.save(transaction));

    UUID newWalletId = newWallet != null ? newWallet.getId() : null;
    if (oldWalletId != null) walletService.recalculateBalance(oldWalletId);
    if (newWalletId != null && !newWalletId.equals(oldWalletId)) walletService.recalculateBalance(newWalletId);

    return result;
  }

  @Transactional
  @CacheEvict(value = "transaction-summary", allEntries = true)
  public void delete(UUID id) {
    User user = getCurrentUser();
    Transaction transaction = transactionRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch"));

    if (!transaction.getUser().getId().equals(user.getId()))
      throw new ForbiddenException("Không có quyền xóa giao dịch này");

    // 👇 ĐỔI
    UUID walletId = transaction.getWallet() != null ? transaction.getWallet().getId() : null;
    transactionRepository.delete(transaction);
    if (walletId != null) walletService.recalculateBalance(walletId);
  }

  // ─── Summary & Charts — giữ nguyên logic cũ ──────────────────────────────

  @Transactional(readOnly = true)
  @Cacheable(
      value = "transaction-summary",
      key = "T(org.springframework.security.core.context.SecurityContextHolder)" +
          ".getContext().getAuthentication().getName()" +
          " + '-' + #year + '-' + #month"
  )
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
      endDate = LocalDate.of(targetYear, endMonth, 1)
          .withDayOfMonth(LocalDate.of(targetYear, endMonth, 1).lengthOfMonth());
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

    Long totalIncome = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
        user.getId(), TransactionType.INCOME, startDate, endDate);
    Long totalExpense = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
        user.getId(), TransactionType.EXPENSE, startDate, endDate);
    long count = transactionRepository.countByUserIdAndDateBetween(user.getId(), startDate, endDate);
    int limit = "FREE".equals(planId) ? FREE_PLAN_LIMIT : -1;

    return TransactionSummaryResponse.builder()
        .totalIncome(totalIncome)
        .totalExpense(totalExpense)
        .balance(totalIncome - totalExpense)
        .transactionCount(count)
        .transactionLimit(limit)
        .limitReached("FREE".equals(planId) && count >= FREE_PLAN_LIMIT)
        .startDate(startDate)
        .endDate(endDate)
        .build();
  }

  public List<DailyChartResponse> getDailyChart(Integer year, Integer month,
                                                Integer startMonth, Integer endMonth) {
    User user = getCurrentUser();
    LocalDate today = LocalDate.now();
    int targetYear = year != null ? year : today.getYear();
    LocalDate startDate, endDate;

    if (startMonth != null && endMonth != null) {
      startDate = LocalDate.of(targetYear, startMonth, 1);
      endDate = LocalDate.of(targetYear, endMonth, 1)
          .withDayOfMonth(LocalDate.of(targetYear, endMonth, 1).lengthOfMonth());
    } else {
      int targetMonth = month != null ? month : today.getMonthValue();
      startDate = LocalDate.of(targetYear, targetMonth, 1);
      endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
    }

    return transactionRepository.findDailyChartData(user.getId(), startDate, endDate).stream()
        .map(row -> DailyChartResponse.builder()
            .date(row[0].toString())
            .income(row[1] != null ? ((Number) row[1]).longValue() : 0L)
            .expense(row[2] != null ? ((Number) row[2]).longValue() : 0L)
            .build())
        .toList();
  }

  public List<MonthlyChartResponse> getMonthlyChart(Integer year) {
    User user = getCurrentUser();
    int targetYear = year != null ? year : LocalDate.now().getYear();
    List<Object[]> rows = transactionRepository.findMonthlyChartData(user.getId(), targetYear);
    Map<Integer, Object[]> rowMap = rows.stream()
        .collect(Collectors.toMap(row -> ((Number) row[0]).intValue(), row -> row));
    String[] labels = {"Th1", "Th2", "Th3", "Th4", "Th5", "Th6", "Th7", "Th8", "Th9", "Th10", "Th11", "Th12"};

    return IntStream.rangeClosed(1, 12).mapToObj(m -> {
      Object[] row = rowMap.get(m);
      long income = row != null && row[1] != null ? ((Number) row[1]).longValue() : 0L;
      long expense = row != null && row[2] != null ? ((Number) row[2]).longValue() : 0L;
      return MonthlyChartResponse.builder()
          .month(m).label(labels[m - 1])
          .income(income).expense(expense).balance(income - expense)
          .build();
    }).toList();
  }

  @Transactional(readOnly = true)
  public List<CategoryChartItem> getCategoryChart(TransactionType type, int year,
                                                  Integer month, Integer startMonth, Integer endMonth) {
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

      return CategoryChartItem.builder()
          .categoryId(catId)
          .categoryName(r[1] != null ? (String) r[1] : "Chưa phân loại")
          .categoryColor(r[2] != null ? (String) r[2] : "#888888")
          .totalAmount(amount)
          .transactionCount(count)
          .percentage(Math.round(pct * 10.0) / 10.0)
          .build();
    }).toList();
  }
}