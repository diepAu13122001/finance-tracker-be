package com.diepau1312.financeTrackerBE.exception;

import lombok.Getter;

@Getter
public class PlanUpgradeRequiredException extends RuntimeException {

    private final String requiredPlan;

    public PlanUpgradeRequiredException(String requiredPlan) {
        super("Tính năng này yêu cầu gói " + requiredPlan);
        this.requiredPlan = requiredPlan;
    }
}