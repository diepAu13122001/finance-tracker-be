package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.category.CategoryResponse;
import com.diepau1312.financeTrackerBE.dto.transaction.*;
import com.diepau1312.financeTrackerBE.entity.Transaction;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.exception.AuthException;
import com.diepau1312.financeTrackerBE.exception.ForbiddenException;
import com.diepau1312.financeTrackerBE.exception.NotFoundException;
import com.diepau1312.financeTrackerBE.exception.PlanUpgradeRequiredException;
import com.diepau1312.financeTrackerBE.repository.TransactionRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.repository.UserSubscriptionRepository;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.diepau1312.financeTrackerBE.repository.CategoryRepository;
import com.diepau1312.financeTrackerBE.repository.GoalRepository;
import com.diepau1312.financeTrackerBE.entity.Category;
import com.diepau1312.financeTrackerBE.entity.Goal;

@Service
@RequiredArgsConstructor
public class TransactionService {

  private static final int FREE_PLAN_LIMIT = 50;

  private final TransactionRepository transactionRepository;
  private final UserRepository userRepository;
  private final UserSubscriptionRepository subscriptionRepository;
  private final CategoryRepository categoryRepository;
  private final GoalRepository goalRepository;
  private final GoalService goalService;

  // ─── Lấy user hiện tại từ Security Context ────────────────────────────────
  private User getCurrentUser() {
    return userRepository.findByEmail(SecurityUtil.getCurrentUserEmail()).orElseThrow(() -> new NotFoundException("Không tìm thấy user"));
  }

  // ─── Lấy plan của user hiện tại ───────────────────────────────────────────
  private String getCurrentUserPlan(UUID userId) {
    return subscriptionRepository.findByUserId(userId).map(sub -> sub.getPlanId()).orElse("FREE");
  }

  // ─── Kiểm tra giới hạn giao dịch cho Free user ────────────────────────────
  private void checkTransactionLimit(UUID userId, String planId) {
    if (!"FREE".equals(planId)) return; // Plus/Premium không giới hạn

    LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
    LocalDate endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

    long count = transactionRepository.countByUserIdAndDateBetween(userId, startOfMonth, endOfMonth);

    if (count >= FREE_PLAN_LIMIT) {
      throw new PlanUpgradeRequiredException("PLUS", null);
    }
  }

  // ─── Mapper: Entity → Response DTO ────────────────────────────────────────
  private TransactionResponse toResponse(Transaction t) {
    return TransactionResponse.builder().id(t.getId()).type(t.getType()).amount(t.getAmount()).currency(t.getCurrency()).note(t.getNote()).transactionDate(t.getTransactionDate()).source(t.getSource()).createdAt(t.getCreatedAt())
        // 👇 THÊM MỚI
        .category(t.getCategory() != null ? CategoryResponse.from(t.getCategory()) : null).build();
  }

  // ─── CRUD Operations ──────────────────────────────────────────────────────

  @Transactional
  @CacheEvict(value = "transaction-summary", allEntries = true)
  public TransactionResponse create(TransactionRequest request) {
    User user = getCurrentUser();
    String planId = getCurrentUserPlan(user.getId());

    // Kiểm tra giới hạn trước khi tạo
    checkTransactionLimit(user.getId(), planId);

    Category category = null;
    if (request.getCategoryId() != null) {
      category = categoryRepository.findByIdAndUserId(request.getCategoryId(), user.getId()).orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));

      // Validate type khớp với transaction type
      if (category.getType() != request.getType()) {
        throw new AuthException("Danh mục " + category.getName() + " không khớp với loại giao dịch");
      }
    }

    Goal goal = null;
    if (request.getGoalId() != null) {
      goal = goalRepository.findByIdAndUserId(request.getGoalId(), user.getId()).orElseThrow(() -> new NotFoundException("Không tìm thấy mục tiêu"));
    }

    if (goal != null && goal.getType() == Goal.GoalType.DEBT && request.getType() == TransactionType.EXPENSE) {
      goalService.validateDebtExpense(goal.getId(), request.getAmount());
    }

    Transaction transaction = Transaction.builder().user(user).type(request.getType()).amount(request.getAmount()).currency(request.getCurrency()).note(request.getNote()).transactionDate(request.getTransactionDate()).source("manual").category(category).goal(goal).build();

    TransactionResponse result = toResponse(transactionRepository.save(transaction));
    if (goal != null) recalculateGoalIfNeeded(goal.getId());

    return result;
  }

  // thêm categoryId parameter
  @Transactional(readOnly = true)
  public Page<TransactionResponse> getAll(int page, int size, String type, UUID categoryId) {
    User user = getCurrentUser();
    Pageable pageable = PageRequest.of(page, size);

    Page<Transaction> result;

    if (categoryId != null) {
      // Filter theo category cụ thể
      result = transactionRepository.findByUser_IdAndCategory_IdOrderByTransactionDateDesc(user.getId(), categoryId, pageable);
    } else if (type != null && !type.isBlank()) {
      TransactionType transactionType = TransactionType.valueOf(type.toUpperCase());
      result = transactionRepository.findByUserIdAndTypeOrderByTransactionDateDesc(user.getId(), transactionType, pageable);
    } else {
      result = transactionRepository.findByUserIdOrderByTransactionDateDesc(user.getId(), pageable);
    }

    return result.map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public TransactionResponse getById(UUID id) {
    User user = getCurrentUser();
    Transaction transaction = transactionRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch"));

    // Kiểm tra ownership — user chỉ xem được giao dịch của mình
    if (!transaction.getUser().getId().equals(user.getId())) {
      throw new ForbiddenException("Không có quyền truy cập giao dịch này");
    }

    return toResponse(transaction);
  }

  @Transactional
  @CacheEvict(value = "transaction-summary", allEntries = true)
  public TransactionResponse update(UUID id, TransactionRequest request) {
    User user = getCurrentUser();
    Transaction transaction = transactionRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch"));

    if (!transaction.getUser().getId().equals(user.getId())) {
      throw new ForbiddenException("Không có quyền sửa giao dịch này");
    }

    Category category = null;
    if (request.getCategoryId() != null) {
      category = categoryRepository.findByIdAndUserId(request.getCategoryId(), user.getId()).orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));

      if (category.getType() != request.getType()) {
        throw new AuthException("Danh mục " + category.getName() + " không khớp với loại giao dịch");
      }
    }

    UUID oldGoalId = transaction.getGoal() != null ? transaction.getGoal().getId() : null;
    Goal newGoal = null;
    if (request.getGoalId() != null) {
      newGoal = goalRepository.findByIdAndUserId(request.getGoalId(), user.getId()).orElseThrow(() -> new NotFoundException("Không tìm thấy mục tiêu"));
    }

    transaction.setType(request.getType());
    transaction.setAmount(request.getAmount());
    transaction.setNote(request.getNote());
    transaction.setTransactionDate(request.getTransactionDate());
    transaction.setCurrency(request.getCurrency());
    transaction.setCategory(category);
    transaction.setGoal(newGoal);

    TransactionResponse result = toResponse(transactionRepository.save(transaction));

    UUID newGoalId = newGoal != null ? newGoal.getId() : null;
    if (oldGoalId != null) recalculateGoalIfNeeded(oldGoalId);
    if (newGoalId != null && !newGoalId.equals(oldGoalId)) recalculateGoalIfNeeded(newGoalId);

    return result;
  }

  @Transactional
  @CacheEvict(value = "transaction-summary", allEntries = true)
  public void delete(UUID id) {
    User user = getCurrentUser();

    Transaction transaction = transactionRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch"));
    UUID goalId = transaction.getGoal() != null ? transaction.getGoal().getId() : null;

    if (!transaction.getUser().getId().equals(user.getId())) {
      throw new ForbiddenException("Không có quyền xóa giao dịch này");
    }

    transactionRepository.delete(transaction);
    recalculateGoalIfNeeded(goalId);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "transaction-summary", key = "#userId + '-' + #year + '-' + #month")
  public TransactionSummaryResponse getSummary(Integer year, Integer month, Integer quarter) {
    User user = getCurrentUser();
    String planId = getCurrentUserPlan(user.getId());

    // ── Tính startDate và endDate theo tham số đầu vào ───────────────────────
    LocalDate startDate;
    LocalDate endDate;
    LocalDate today = LocalDate.now();

    // Mặc định: không truyền gì → tháng hiện tại
    int targetYear = (year != null) ? year : today.getYear();

    if (quarter != null) {
      // Theo quý: Q1=1-3, Q2=4-6, Q3=7-9, Q4=10-12
      int startMonth = (quarter - 1) * 3 + 1;
      int endMonth = startMonth + 2;
      startDate = LocalDate.of(targetYear, startMonth, 1);
      endDate = LocalDate.of(targetYear, endMonth, 1).withDayOfMonth(LocalDate.of(targetYear, endMonth, 1).lengthOfMonth());

    } else if (month != null) {
      // Theo tháng cụ thể
      startDate = LocalDate.of(targetYear, month, 1);
      endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

    } else if (year != null) {
      // Theo năm — không truyền month
      startDate = LocalDate.of(targetYear, 1, 1);
      endDate = LocalDate.of(targetYear, 12, 31);

    } else {
      // Mặc định: tháng hiện tại
      startDate = today.withDayOfMonth(1);
      endDate = today.withDayOfMonth(today.lengthOfMonth());
    }

    // ── Query ─────────────────────────────────────────────────────────────────
    Long totalIncome = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.INCOME, startDate, endDate);

    Long totalExpense = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(user.getId(), TransactionType.EXPENSE, startDate, endDate);

    long count = transactionRepository.countByUserIdAndDateBetween(user.getId(), startDate, endDate);

    int limit = "FREE".equals(planId) ? FREE_PLAN_LIMIT : -1;

    return TransactionSummaryResponse.builder().totalIncome(totalIncome).totalExpense(totalExpense).balance(totalIncome - totalExpense).transactionCount(count).transactionLimit(limit).limitReached("FREE".equals(planId) && count >= FREE_PLAN_LIMIT)
        // Thêm 2 field mới để frontend biết đang xem kỳ nào
        .startDate(startDate).endDate(endDate).build();
  }

  public List<DailyChartResponse> getDailyChart(Integer year, Integer month, Integer startMonth, Integer endMonth) {
    User user = getCurrentUser();
    LocalDate today = LocalDate.now();
    int targetYear = year != null ? year : today.getYear();

    LocalDate startDate;
    LocalDate endDate;

    if (startMonth != null && endMonth != null) {
      // Theo quý
      startDate = LocalDate.of(targetYear, startMonth, 1);
      endDate = LocalDate.of(targetYear, endMonth, 1).withDayOfMonth(LocalDate.of(targetYear, endMonth, 1).lengthOfMonth());
    } else {
      // Theo tháng đơn
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

    // Tạo map để fill các tháng không có data = 0
    Map<Integer, Object[]> rowMap = rows.stream().collect(Collectors.toMap(row -> ((Number) row[0]).intValue(), row -> row));

    String[] labels = {"Th1", "Th2", "Th3", "Th4", "Th5", "Th6", "Th7", "Th8", "Th9", "Th10", "Th11", "Th12"};

    return IntStream.rangeClosed(1, 12).mapToObj(m -> {
      Object[] row = rowMap.get(m);

      long income = row != null && row[1] != null ? ((Number) row[1]).longValue() : 0L;

      long expense = row != null && row[2] != null ? ((Number) row[2]).longValue() : 0L;

      return MonthlyChartResponse.builder().month(m).label(labels[m - 1]).income(income).expense(expense).balance(income - expense).build();
    }).toList();
  }

  // 🔄 SỬA: thêm startMonth/endMonth cho quarter support
  @Transactional(readOnly = true)
  public List<CategoryChartItem> getCategoryChart(TransactionType type, int year, Integer month, Integer startMonth, Integer endMonth) {

    User user = getCurrentUser();

    List<Object[]> rows;

    if (startMonth != null && endMonth != null) {
      // Theo quý / khoảng tháng
      rows = transactionRepository.findCategoryBreakdownByRange(user.getId(), type.name(), year, startMonth, endMonth);
    } else if (month != null) {
      // Theo tháng cụ thể
      rows = transactionRepository.findCategoryBreakdown(user.getId(), type.name(), year, month);
    } else {
      // Theo cả năm
      rows = transactionRepository.findCategoryBreakdownByYear(user.getId(), type.name(), year);
    }

    long totalAmount = rows.stream().mapToLong(r -> r[3] != null ? ((Number) r[3]).longValue() : 0L).sum();

    return rows.stream().map(r -> {
      UUID catId = r[0] != null ? (UUID) r[0] : null;
      String catName = (String) r[1];
      String catColor = (String) r[2];
      long amount = r[3] != null ? ((Number) r[3]).longValue() : 0L;
      long count = r[4] != null ? ((Number) r[4]).longValue() : 0L;

      double percentage = totalAmount > 0 ? (amount * 100.0) / totalAmount : 0.0;

      return CategoryChartItem.builder().categoryId(catId).categoryName(catName != null ? catName : "Chưa phân loại").categoryColor(catColor != null ? catColor : "#888888").totalAmount(amount).transactionCount(count).percentage(Math.round(percentage * 10.0) / 10.0).build();
    }).toList();
  }

  /**
   * Tính lại current_amount của goal từ tất cả transactions.
   * Gọi sau mỗi create / update / delete transaction có goalId.
   */
  private void recalculateGoalIfNeeded(UUID goalId) {
    if (goalId == null) return;
    goalService.recalculateProgress(goalId);  // không cần truyền amount nữa
  }


}