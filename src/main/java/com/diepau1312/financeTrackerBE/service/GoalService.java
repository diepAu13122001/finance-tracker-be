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
  private final TransactionRepository transactionRepository;

  private static final int FREE_WALLET_LIMIT = 5;

  private UUID getCurrentUserId() {
    return userRepository.findByEmail(SecurityUtil.getCurrentUserEmail())
        .orElseThrow(() -> new NotFoundException("Không tìm thấy user"))
        .getId();
  }

  @Transactional(readOnly = true)
  public List<GoalResponse> getAll() {
    return goalRepository.findByUserIdOrderByCreatedAtDesc(getCurrentUserId())
        .stream().map(GoalResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<GoalResponse> getActive() {
    return goalRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
        getCurrentUserId(), GoalStatus.ACTIVE)
        .stream().map(GoalResponse::from).toList();
  }

  @Transactional
  public GoalResponse create(GoalRequest request, String planId) {
    UUID userId = getCurrentUserId();
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy user"));

    if ("FREE".equals(planId)) {
      long count = goalRepository.countActiveByUserId(userId);
      if (count >= FREE_WALLET_LIMIT)
        throw new PlanUpgradeRequiredException("PLUS",
            "Gói Miễn phí chỉ được tạo tối đa " + FREE_WALLET_LIMIT + " nguồn tiền");
    }

    long targetAmount = request.getTargetAmount() != null && request.getTargetAmount() > 0
        ? request.getTargetAmount()
        : 0L;

    // INSTALLMENT: target = totalAmount = numberOfPeriods * monthlyPayment
    if (request.getSubtype() == GoalSubtype.INSTALLMENT
        && request.getNumberOfPeriods() != null
        && request.getMonthlyPayment() != null) {
      targetAmount = (long) request.getNumberOfPeriods() * request.getMonthlyPayment();
    }

    Goal goal = Goal.builder()
        .user(user)
        .name(request.getName().trim())
        .icon(request.getIcon() != null ? request.getIcon() : "wallet")
        .color(request.getColor() != null ? request.getColor() : "#82b01e")
        .type(request.getType())
        .subtype(request.getSubtype())
        .targetAmount(targetAmount)
        .currentAmount(0L)
        .deadline(request.getDeadline())
        .creditLimit(request.getCreditLimit())
        .billingDate(request.getBillingDate())
        .interestRate(request.getInterestRate())
        .numberOfPeriods(request.getNumberOfPeriods())
        .monthlyPayment(request.getMonthlyPayment())
        .initialAmount(request.getInitialAmount())
        .status(GoalStatus.ACTIVE)
        .build();

    return GoalResponse.from(goalRepository.save(goal));
  }

  @Transactional
  public GoalResponse update(UUID id, GoalRequest request) {
    UUID userId = getCurrentUserId();
    Goal goal = goalRepository.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy nguồn tiền"));

    goal.setName(request.getName().trim());
    if (request.getIcon() != null)
      goal.setIcon(request.getIcon());
    if (request.getColor() != null)
      goal.setColor(request.getColor());
    goal.setType(request.getType());
    goal.setSubtype(request.getSubtype());
    goal.setDeadline(request.getDeadline());
    goal.setCreditLimit(request.getCreditLimit());
    goal.setBillingDate(request.getBillingDate());
    goal.setInterestRate(request.getInterestRate());
    goal.setNumberOfPeriods(request.getNumberOfPeriods());
    goal.setMonthlyPayment(request.getMonthlyPayment());
    goal.setInitialAmount(request.getInitialAmount());

    if (request.getTargetAmount() != null)
      goal.setTargetAmount(request.getTargetAmount());

    if (goal.getStatus() == GoalStatus.COMPLETED
        && goal.getCurrentAmount() < goal.getTargetAmount())
      goal.setStatus(GoalStatus.ACTIVE);

    return GoalResponse.from(goal);
  }

  @Transactional
  public GoalResponse cancel(UUID id) {
    UUID userId = getCurrentUserId();
    Goal goal = goalRepository.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy nguồn tiền"));
    if (goal.getStatus() == GoalStatus.CANCELLED)
      throw new AuthException("Đã huỷ rồi");
    goal.setStatus(GoalStatus.CANCELLED);
    return GoalResponse.from(goal);
  }

  @Transactional
  public void delete(UUID id) {
    UUID userId = getCurrentUserId();
    Goal goal = goalRepository.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy nguồn tiền"));
    goalRepository.delete(goal);
  }

  /**
   * Recalculate current_amount từ DB sau mỗi transaction mutation.
   *
   * NORMAL: current = SUM(INCOME) - SUM(EXPENSE) có thể âm
   * DEBT CC: current = SUM(EXPENSE) - SUM(INCOME) số nợ
   * INSTALLMENT: current = SUM(INCOME) tổng đã trả
   * SAVINGS/INV: current = SUM(INCOME) - SUM(EXPENSE) có thể âm (được rút khẩn
   * cấp)
   */
  @Transactional
  public void recalculateProgress(UUID goalId) {
    goalRepository.findById(goalId).ifPresent(goal -> {

      long newAmount;

      switch (goal.getType()) {
        case NORMAL, SAVINGS, INVESTMENT -> {
          Long income = transactionRepository.sumAmountByGoalIdAndType(goalId, "INCOME");
          Long expense = transactionRepository.sumAmountByGoalIdAndType(goalId, "EXPENSE");
          newAmount = (income != null ? income : 0L) - (expense != null ? expense : 0L);
          // Cho phép âm — báo lỗi ở validate trước khi tạo transaction
          goal.setCurrentAmount(newAmount);
        }
        case DEBT -> {
          if (goal.getSubtype() == GoalSubtype.INSTALLMENT) {
            // INSTALLMENT: chỉ cộng INCOME (số tiền đã trả)
            Long income = transactionRepository.sumAmountByGoalIdAndType(goalId, "INCOME");
            newAmount = income != null ? income : 0L;
          } else {
            // CREDIT_CARD: nợ = EXPENSE - INCOME
            Long income = transactionRepository.sumAmountByGoalIdAndType(goalId, "INCOME");
            Long expense = transactionRepository.sumAmountByGoalIdAndType(goalId, "EXPENSE");
            newAmount = (expense != null ? expense : 0L) - (income != null ? income : 0L);
          }
          goal.setCurrentAmount(Math.max(newAmount, 0L));
        }
        default -> {
          Long total = transactionRepository.sumAmountByGoalId(goalId);
          goal.setCurrentAmount(total != null ? total : 0L);
        }
      }

      // Auto-complete cho SAVINGS/INVESTMENT
      if (goal.getType() == GoalType.SAVINGS || goal.getType() == GoalType.INVESTMENT) {
        if (goal.getTargetAmount() > 0) {
          if (goal.getCurrentAmount() >= goal.getTargetAmount()
              && goal.getStatus() == GoalStatus.ACTIVE)
            goal.setStatus(GoalStatus.COMPLETED);
          else if (goal.getCurrentAmount() < goal.getTargetAmount()
              && goal.getStatus() == GoalStatus.COMPLETED)
            goal.setStatus(GoalStatus.ACTIVE);
        }
      }
      // Auto-complete cho INSTALLMENT
      if (goal.getType() == GoalType.DEBT
          && goal.getSubtype() == GoalSubtype.INSTALLMENT
          && goal.getTargetAmount() > 0
          && goal.getCurrentAmount() >= goal.getTargetAmount()
          && goal.getStatus() == GoalStatus.ACTIVE) {
        goal.setStatus(GoalStatus.COMPLETED);
      }

      goalRepository.save(goal);
    });
  }

  /**
   * Validate trước khi tạo/update transaction link vào wallet.
   * Ném exception nếu không hợp lệ.
   */
  @Transactional(readOnly = true)
  public void validateWalletTransaction(UUID goalId, String transactionType, long amount) {
    goalRepository.findById(goalId).ifPresent(goal -> {
      switch (goal.getType()) {
        case NORMAL -> {
          if ("EXPENSE".equals(transactionType)) {
            long balance = goal.getCurrentAmount();
            if (balance < amount)
              throw new AuthException(
                  "Số dư không đủ! Hiện có: " + balance + " VND, cần: " + amount + " VND");
          }
        }
        case SAVINGS, INVESTMENT -> {
          if ("EXPENSE".equals(transactionType)) {
            long current = goal.getCurrentAmount();
            if (current < amount)
              throw new AuthException(
                  "Số dư tích lũy không đủ! Hiện có: " + current + " VND");
          }
        }
        case DEBT -> {
          if (goal.getSubtype() == GoalSubtype.INSTALLMENT) {
            // INSTALLMENT không cho EXPENSE
            if ("EXPENSE".equals(transactionType))
              throw new AuthException(
                  "Khoản trả góp chỉ nhận giao dịch Thu nhập (thanh toán kỳ)");
          } else {
            // CREDIT_CARD: check hạn mức khi EXPENSE
            if ("EXPENSE".equals(transactionType)) {
              long limit = goal.getCreditLimit() != null ? goal.getCreditLimit() : 0L;
              long currentDebt = goal.getCurrentAmount();
              if (currentDebt + amount > limit)
                throw new AuthException(
                    "Vượt hạn mức thẻ! Còn: " + (limit - currentDebt) + " VND");
            }
          }
        }
      }
    });
  }
}