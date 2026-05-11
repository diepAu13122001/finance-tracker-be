package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.goal.GoalRequest;
import com.diepau1312.financeTrackerBE.dto.goal.GoalResponse;
import com.diepau1312.financeTrackerBE.entity.Goal;
import com.diepau1312.financeTrackerBE.entity.Goal.GoalStatus;
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

        Goal goal = Goal.builder()
                .user(user)
                .name(request.getName().trim())
                .icon(request.getIcon() != null ? request.getIcon() : "target")
                .color(request.getColor() != null ? request.getColor() : "#82b01e")
                .type(request.getType())
                .targetAmount(request.getTargetAmount())
                .currentAmount(0L)
                .deadline(request.getDeadline())
                .status(GoalStatus.ACTIVE)
                .build();

        return GoalResponse.from(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponse update(UUID id, GoalRequest request) {
        UUID userId = getCurrentUserId();

        Goal goal = goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy mục tiêu"));

        goal.setName(request.getName().trim());
        if (request.getIcon() != null)
            goal.setIcon(request.getIcon());
        if (request.getColor() != null)
            goal.setColor(request.getColor());
        goal.setType(request.getType());
        goal.setTargetAmount(request.getTargetAmount());
        goal.setDeadline(request.getDeadline());

        // Nếu đã hoàn thành nhưng target tăng lên → revert ACTIVE
        if (goal.getStatus() == GoalStatus.COMPLETED
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
        // ON DELETE SET NULL → transactions liên quan vẫn còn nhưng goal_id = NULL
        goalRepository.delete(goal);
    }

    /**
     * Được gọi từ TransactionService sau mỗi create/update/delete.
     * Tính lại current_amount từ tất cả transactions link vào goal này.
     */
    @Transactional
    public void recalculateProgress(UUID goalId, long newTotalAmount) {
        goalRepository.findById(goalId).ifPresent(goal -> {
            goal.setCurrentAmount(newTotalAmount);

            // Auto-complete khi đạt target
            if (goal.getCurrentAmount() >= goal.getTargetAmount()
                    && goal.getStatus() == GoalStatus.ACTIVE) {
                goal.setStatus(GoalStatus.COMPLETED);
            }
            // Revert nếu transaction bị xóa và chưa đạt target
            else if (goal.getCurrentAmount() < goal.getTargetAmount()
                    && goal.getStatus() == GoalStatus.COMPLETED) {
                goal.setStatus(GoalStatus.ACTIVE);
            }

            goalRepository.save(goal);
        });
    }
}