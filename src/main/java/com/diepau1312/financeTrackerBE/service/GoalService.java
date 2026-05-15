package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.goal.GoalRequest;
import com.diepau1312.financeTrackerBE.dto.goal.GoalResponse;
import com.diepau1312.financeTrackerBE.entity.Goal;
import com.diepau1312.financeTrackerBE.entity.Goal.GoalStatus;
import com.diepau1312.financeTrackerBE.entity.Goal.GoalType;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.exception.AuthException;
import com.diepau1312.financeTrackerBE.exception.NotFoundException;
import com.diepau1312.financeTrackerBE.repository.GoalRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
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

  private UUID getCurrentUserId() {
    return userRepository.findByEmail(SecurityUtil.getCurrentUserEmail())
        .orElseThrow(() -> new NotFoundException("Không tìm thấy user"))
        .getId();
  }

  @Transactional(readOnly = true)
  public List<GoalResponse> getAll() {
    UUID userId = getCurrentUserId();
    return goalRepository.findByUserIdOrderByCreatedAtDesc(userId)
        .stream().map(GoalResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<GoalResponse> getActive() {
    UUID userId = getCurrentUserId();
    return goalRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, GoalStatus.ACTIVE)
        .stream().map(GoalResponse::from).toList();
  }

  @Transactional
  public GoalResponse create(GoalRequest request) {
    UUID userId = getCurrentUserId();
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy user"));

    // Validate: SAVINGS/DEBT/INVESTMENT phải có targetAmount > 0
    // NORMAL wallet: targetAmount = 0 là hợp lệ
    if (request.getType() != GoalType.NORMAL
        && (request.getTargetAmount() == null || request.getTargetAmount() <= 0)) {
      throw new AuthException("Số tiền mục tiêu phải lớn hơn 0");
    }

    Goal goal = Goal.builder()
        .user(user)
        .name(request.getName().trim())
        .icon(request.getIcon() != null ? request.getIcon() : "wallet")
        .color(request.getColor() != null ? request.getColor() : "#82b01e")
        .type(request.getType())
        .subtype(request.getSubtype())
        .targetAmount(request.getTargetAmount() != null ? request.getTargetAmount() : 0L)
        .currentAmount(0L)
        .deadline(request.getDeadline())
        .status(GoalStatus.ACTIVE)
        // DEBT / CREDIT_CARD fields
        .creditLimit(request.getCreditLimit())
        .billingDate(request.getBillingDate())
        .interestRate(request.getInterestRate())
        // INSTALLMENT fields
        .numberOfPeriods(request.getNumberOfPeriods())
        .monthlyPayment(request.getMonthlyPayment())
        .initialAmount(request.getInitialAmount())
        .build();

    return GoalResponse.from(goalRepository.save(goal));
  }

  @Transactional
  public GoalResponse update(UUID id, GoalRequest request) {
    UUID userId = getCurrentUserId();

    Goal goal = goalRepository.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy mục tiêu"));

    // Validate targetAmount cho non-NORMAL types
    if (request.getType() != GoalType.NORMAL
        && (request.getTargetAmount() == null || request.getTargetAmount() <= 0)) {
      throw new AuthException("Số tiền mục tiêu phải lớn hơn 0");
    }

    goal.setName(request.getName().trim());
    if (request.getIcon() != null) goal.setIcon(request.getIcon());
    if (request.getColor() != null) goal.setColor(request.getColor());
    goal.setType(request.getType());
    goal.setSubtype(request.getSubtype());
    goal.setTargetAmount(request.getTargetAmount() != null ? request.getTargetAmount() : 0L);
    goal.setDeadline(request.getDeadline());

    // DEBT / CREDIT_CARD
    goal.setCreditLimit(request.getCreditLimit());
    goal.setBillingDate(request.getBillingDate());
    goal.setInterestRate(request.getInterestRate());

    // INSTALLMENT
    goal.setNumberOfPeriods(request.getNumberOfPeriods());
    goal.setMonthlyPayment(request.getMonthlyPayment());
    goal.setInitialAmount(request.getInitialAmount());

    // Nếu đã COMPLETED nhưng target tăng lên → revert ACTIVE
    // Chỉ áp dụng cho goals (SAVINGS/DEBT/INVESTMENT), không áp dụng NORMAL wallet
    if (goal.getType() != GoalType.NORMAL
        && goal.getStatus() == GoalStatus.COMPLETED
        && goal.getCurrentAmount() < goal.getTargetAmount()) {
      goal.setStatus(GoalStatus.ACTIVE);
    }

    return GoalResponse.from(goal);
  }

  @Transactional
  public GoalResponse cancel(UUID id) {
    UUID userId = getCurrentUserId();

    Goal goal = goalRepository.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy mục tiêu"));

    if (goal.getStatus() == GoalStatus.CANCELLED) {
      throw new AuthException("Mục tiêu đã bị huỷ");
    }

    goal.setStatus(GoalStatus.CANCELLED);
    return GoalResponse.from(goal);
  }

  @Transactional
  public void delete(UUID id) {
    UUID userId = getCurrentUserId();
    Goal goal = goalRepository.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy mục tiêu"));
    goalRepository.delete(goal);
  }

  /**
   * Được gọi từ TransactionService sau mỗi create/update/delete.
   * <p>
   * NORMAL wallet: chỉ update balance, không auto-complete
   * → balance = SUM(INCOME) - SUM(EXPENSE), có thể âm
   * <p>
   * Goals (SAVINGS/DEBT/INVESTMENT): auto-complete khi đạt target
   */
  @Transactional
  public void recalculateProgress(UUID goalId, long newTotalAmount) {
    goalRepository.findById(goalId).ifPresent(goal -> {
      goal.setCurrentAmount(newTotalAmount);

      if (goal.getType() == GoalType.NORMAL) {
        // Wallet: chỉ update balance, không auto-complete
        goalRepository.save(goal);
        return;
      }

      // Goals: auto-complete logic
      if (goal.getCurrentAmount() >= goal.getTargetAmount()
          && goal.getStatus() == GoalStatus.ACTIVE) {
        goal.setStatus(GoalStatus.COMPLETED);
      } else if (goal.getCurrentAmount() < goal.getTargetAmount()
          && goal.getStatus() == GoalStatus.COMPLETED) {
        goal.setStatus(GoalStatus.ACTIVE);
      }

      goalRepository.save(goal);
    });
  }
}