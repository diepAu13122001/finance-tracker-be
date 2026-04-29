package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.transaction.TransactionRequest;
import com.diepau1312.financeTrackerBE.dto.transaction.TransactionResponse;
import com.diepau1312.financeTrackerBE.dto.transaction.TransactionSummaryResponse;
import com.diepau1312.financeTrackerBE.entity.Transaction;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.exception.PlanUpgradeRequiredException;
import com.diepau1312.financeTrackerBE.repository.TransactionRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

  private static final int FREE_PLAN_LIMIT = 50;

  private final TransactionRepository transactionRepository;
  private final UserRepository userRepository;
  private final UserSubscriptionRepository subscriptionRepository;

  // ─── Lấy user hiện tại từ Security Context ────────────────────────────────
  private User getCurrentUser() {
    String email = (String) SecurityContextHolder
        .getContext()
        .getAuthentication()
        .getPrincipal();

    return userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));
  }

  // ─── Lấy plan của user hiện tại ───────────────────────────────────────────
  private String getCurrentUserPlan(UUID userId) {
    return subscriptionRepository.findByUserId(userId)
        .map(sub -> sub.getPlanId())
        .orElse("FREE");
  }

  // ─── Kiểm tra giới hạn giao dịch cho Free user ────────────────────────────
  private void checkTransactionLimit(UUID userId, String planId) {
    if (!"FREE".equals(planId)) return; // Plus/Premium không giới hạn

    LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
    LocalDate endOfMonth = LocalDate.now().withDayOfMonth(
        LocalDate.now().lengthOfMonth()
    );

    long count = transactionRepository.countByUserIdAndDateBetween(
        userId, startOfMonth, endOfMonth
    );

    if (count >= FREE_PLAN_LIMIT) {
      throw new PlanUpgradeRequiredException("PLUS");
    }
  }

  // ─── Mapper: Entity → Response DTO ────────────────────────────────────────
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
        .build();
  }

  // ─── CRUD Operations ──────────────────────────────────────────────────────

  @Transactional
  public TransactionResponse create(TransactionRequest request) {
    User user = getCurrentUser();
    String planId = getCurrentUserPlan(user.getId());

    // Kiểm tra giới hạn trước khi tạo
    checkTransactionLimit(user.getId(), planId);

    Transaction transaction = Transaction.builder()
        .user(user)
        .type(request.getType())
        .amount(request.getAmount())
        .currency(request.getCurrency())
        .note(request.getNote())
        .transactionDate(request.getTransactionDate())
        .source("manual")
        .build();

    return toResponse(transactionRepository.save(transaction));
  }

  @Transactional(readOnly = true)
  public Page<TransactionResponse> getAll(int page, int size, String type) {
    User user = getCurrentUser();
    Pageable pageable = PageRequest.of(page, size);

    Page<Transaction> result;

    if (type != null && !type.isBlank()) {
      Transaction.TransactionType transactionType =
          Transaction.TransactionType.valueOf(type.toUpperCase());
      result = transactionRepository
          .findByUserIdAndTypeOrderByTransactionDateDesc(
              user.getId(), transactionType, pageable);
    } else {
      result = transactionRepository
          .findByUserIdOrderByTransactionDateDesc(user.getId(), pageable);
    }

    return result.map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public TransactionResponse getById(UUID id) {
    User user = getCurrentUser();
    Transaction transaction = transactionRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch"));

    // Kiểm tra ownership — user chỉ xem được giao dịch của mình
    if (!transaction.getUser().getId().equals(user.getId())) {
      throw new RuntimeException("Không có quyền truy cập giao dịch này");
    }

    return toResponse(transaction);
  }

  @Transactional
  public TransactionResponse update(UUID id, TransactionRequest request) {
    User user = getCurrentUser();
    Transaction transaction = transactionRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch"));

    if (!transaction.getUser().getId().equals(user.getId())) {
      throw new RuntimeException("Không có quyền sửa giao dịch này");
    }

    transaction.setType(request.getType());
    transaction.setAmount(request.getAmount());
    transaction.setNote(request.getNote());
    transaction.setTransactionDate(request.getTransactionDate());
    transaction.setCurrency(request.getCurrency());

    return toResponse(transactionRepository.save(transaction));
  }

  @Transactional
  public void delete(UUID id) {
    User user = getCurrentUser();
    Transaction transaction = transactionRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch"));

    if (!transaction.getUser().getId().equals(user.getId())) {
      throw new RuntimeException("Không có quyền xóa giao dịch này");
    }

    transactionRepository.delete(transaction);
  }

  @Transactional(readOnly = true)
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
      endDate = LocalDate.of(targetYear, endMonth, 1)
          .withDayOfMonth(LocalDate.of(targetYear, endMonth, 1).lengthOfMonth());

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
    Long totalIncome = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
        user.getId(), Transaction.TransactionType.INCOME, startDate, endDate);

    Long totalExpense = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
        user.getId(), Transaction.TransactionType.EXPENSE, startDate, endDate);

    long count = transactionRepository.countByUserIdAndDateBetween(
        user.getId(), startDate, endDate);

    int limit = "FREE".equals(planId) ? FREE_PLAN_LIMIT : -1;

    return TransactionSummaryResponse.builder()
        .totalIncome(totalIncome)
        .totalExpense(totalExpense)
        .balance(totalIncome - totalExpense)
        .transactionCount(count)
        .transactionLimit(limit)
        .limitReached("FREE".equals(planId) && count >= FREE_PLAN_LIMIT)
        // Thêm 2 field mới để frontend biết đang xem kỳ nào
        .startDate(startDate)
        .endDate(endDate)
        .build();
  }
}