package com.diepau1312.financeTrackerBE.dto.goal;

import com.diepau1312.financeTrackerBE.entity.Goal;
import com.diepau1312.financeTrackerBE.entity.Goal.GoalStatus;
import com.diepau1312.financeTrackerBE.entity.Goal.GoalType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class GoalResponse {

    private UUID id;
    private String name;
    private String icon;
    private String color;
    private GoalType type;
    private Long targetAmount;
    private Long currentAmount;
    private LocalDate deadline;
    private GoalStatus status;
    private LocalDateTime createdAt;

    // Computed fields
    private Double progressPercent; // 0-100
    private Long remainingAmount; // max(target - current, 0)
    private boolean overLimit; // DEBT: current > target (cảnh báo nguy hiểm)

    public static GoalResponse from(Goal g) {
        long current = g.getCurrentAmount();
        long target = g.getTargetAmount();
        double pct = target > 0
                ? Math.min((current * 100.0) / target, 100.0)
                : 0.0;

        return GoalResponse.builder()
                .id(g.getId())
                .name(g.getName())
                .icon(g.getIcon())
                .color(g.getColor())
                .type(g.getType())
                .targetAmount(target)
                .currentAmount(current)
                .deadline(g.getDeadline())
                .status(g.getStatus())
                .createdAt(g.getCreatedAt())
                .progressPercent(Math.round(pct * 10.0) / 10.0)
                .remainingAmount(Math.max(target - current, 0L))
                .overLimit(g.getType() == GoalType.DEBT && current > target)
                .build();
    }
}