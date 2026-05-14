package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.goal.*;
import com.diepau1312.financeTrackerBE.entity.Goal;
import com.diepau1312.financeTrackerBE.entity.Goal.*;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.exception.*;
import com.diepau1312.financeTrackerBE.repository.*;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoalService {

  private final GoalRepository goalRepository;
  private final UserRepository userRepository;
  private final TransactionRepository transactionRepository; // 👈 THÊM: cho balance query

  private static final int FREE_WALLET_LIMIT = 5;

  private UUID getCurrentUserId() {
    return userRepository.findByEmail(SecurityUtil.getCurrentUserEmail()).orElseThrow(() -> new NotFoundException("Không tìm thấy user")).getId();
  }

  @Transactional(readOnly = true)
  public List<GoalResponse> getAll() {
    return goalRepository.findByUserIdOrderByCreatedAtDesc(getCurrentUserId()).stream().map(GoalResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<GoalResponse> getActive() {
    return goalRepository.findByUserIdAndStatusOrderByCreatedAtDesc(getCurrentUserId(), GoalStatus.ACTIVE).stream().map(GoalResponse::from).toList();
  }

  @Transactional
  public GoalResponse create(GoalRequest request, String planId) {
    UUID userId = getCurrentUserId();
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy user"));

    if ("FREE".equals(planId)) {
      long count = goalRepository.countActiveByUserId(userId);
      if (count >= FREE_WALLET_LIMIT) {
        throw new PlanUpgradeRequiredException("PLUS",
            "Gói Miễn phí chỉ được tạo tối đa " + FREE_WALLET_LIMIT + " nguồn tiền");
      }
    }

    // 🔄 SỬA: DEBT dùng creditLimit làm "limit", không cần targetAmount
    long targetAmount = 0L;
    if (request.getTargetAmount() != null && request.getTargetAmount() > 0) {
      targetAmount = request.getTargetAmount();
    }

    Goal goal = Goal.builder()
        .user(user)
        .name(request.getName().trim())
        .icon(request.getIcon() != null ? request.getIcon() : "wallet")
        .color(request.getColor() != null ? request.getColor() : "#82b01e")
        .type(request.getType())
        .subtype(request.getSubtype())
        .targetAmount(targetAmount)  // 🔄 SỬA: dùng giá trị đã validate
        .currentAmount(0L)
        .deadline(request.getDeadline())
        .creditLimit(request.getCreditLimit())
        .billingDate(request.getBillingDate())
        .interestRate(request.getInterestRate())
        .status(GoalStatus.ACTIVE)
        .build();

    return GoalResponse.from(goalRepository.save(goal));
  }

  @Transactional
  public GoalResponse update(UUID id, GoalRequest request) {
    UUID userId = getCurrentUserId();
    Goal goal = goalRepository.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Không tìm thấy nguồn tiền"));

    goal.setName(request.getName().trim());
    if (request.getIcon() != null) goal.setIcon(request.getIcon());
    if (request.getColor() != null) goal.setColor(request.getColor());
    goal.setType(request.getType());
    goal.setSubtype(request.getSubtype());
    goal.setDeadline(request.getDeadline());
    goal.setCreditLimit(request.getCreditLimit());
    goal.setBillingDate(request.getBillingDate());
    goal.setInterestRate(request.getInterestRate());

    if (request.getTargetAmount() != null) {
      goal.setTargetAmount(request.getTargetAmount());
    }

    // Revert ACTIVE nếu target tăng lên
    if (goal.getStatus() == GoalStatus.COMPLETED && goal.getCurrentAmount() < goal.getTargetAmount()) {
      goal.setStatus(GoalStatus.ACTIVE);
    }

    return GoalResponse.from(goal);
  }

  @Transactional
  public GoalResponse cancel(UUID id) {
    UUID userId = getCurrentUserId();
    Goal goal = goalRepository.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Không tìm thấy nguồn tiền"));

    if (goal.getStatus() == GoalStatus.CANCELLED) throw new AuthException("Đã huỷ rồi");

    goal.setStatus(GoalStatus.CANCELLED);
    return GoalResponse.from(goal);
  }

  @Transactional
  public void delete(UUID id) {
    UUID userId = getCurrentUserId();
    Goal goal = goalRepository.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Không tìm thấy nguồn tiền"));
    goalRepository.delete(goal);
  }

  /**
   * Recalculate current_amount sau mỗi transaction mutation.
   * Logic khác nhau theo wallet type:
   * NORMAL:           current = SUM(INCOME) - SUM(EXPENSE)
   * DEBT:             current = SUM(EXPENSE) - SUM(INCOME)   ← trả nợ = giảm nợ
   * SAVINGS/INVESTMENT: current = SUM(tất cả amount)
   */
  @Transactional
  public void recalculateProgress(UUID goalId) {
    goalRepository.findById(goalId).ifPresent(goal -> {

      long newAmount;

      switch (goal.getType()) {
        case NORMAL -> {
          Long income = transactionRepository.sumAmountByGoalIdAndType(goalId, "INCOME");
          Long expense = transactionRepository.sumAmountByGoalIdAndType(goalId, "EXPENSE");
          newAmount = (income != null ? income : 0L) - (expense != null ? expense : 0L);
        }
        case DEBT -> {
          // INCOME vào debt wallet = trả nợ → giảm current
          // EXPENSE vào debt wallet = mua chịu → tăng current
          Long income = transactionRepository.sumAmountByGoalIdAndType(goalId, "INCOME");
          Long expense = transactionRepository.sumAmountByGoalIdAndType(goalId, "EXPENSE");
          newAmount = (expense != null ? expense : 0L) - (income != null ? income : 0L);
        }
        default -> { // SAVINGS, INVESTMENT
          Long total = transactionRepository.sumAmountByGoalId(goalId);
          newAmount = total != null ? total : 0L;
        }
      }

      goal.setCurrentAmount(Math.max(newAmount, 0L)); // không cho âm ở đây

      // SAVINGS/INVESTMENT: auto-complete
      if ((goal.getType() == Goal.GoalType.SAVINGS || goal.getType() == Goal.GoalType.INVESTMENT) && goal.getTargetAmount() > 0) {
        if (goal.getCurrentAmount() >= goal.getTargetAmount() && goal.getStatus() == GoalStatus.ACTIVE) {
          goal.setStatus(GoalStatus.COMPLETED);
        } else if (goal.getCurrentAmount() < goal.getTargetAmount() && goal.getStatus() == GoalStatus.COMPLETED) {
          goal.setStatus(GoalStatus.ACTIVE);
        }
      }

      goalRepository.save(goal);
    });
  }

  /**
   * Kiểm tra DEBT wallet: EXPENSE có vượt hạn mức không?
   */
  @Transactional(readOnly = true)
  public void validateDebtExpense(UUID goalId, long expenseAmount) {
    goalRepository.findById(goalId).ifPresent(goal -> {
      if (goal.getType() != Goal.GoalType.DEBT) return;

      long limit = goal.getCreditLimit() != null ? goal.getCreditLimit() : goal.getTargetAmount();
      long currentDebt = goal.getCurrentAmount();
      long afterExpense = currentDebt + expenseAmount;

      if (afterExpense > limit) {
        throw new AuthException("Vượt hạn mức! Hạn mức còn lại: " + (limit - currentDebt) + " VND");
      }
    });
  }
}