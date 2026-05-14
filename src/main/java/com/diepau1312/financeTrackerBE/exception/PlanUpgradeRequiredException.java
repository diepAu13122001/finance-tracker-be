package com.diepau1312.financeTrackerBE.exception;

import lombok.Getter;

@Getter
public class PlanUpgradeRequiredException extends RuntimeException {

  private final String requiredPlan;

  public PlanUpgradeRequiredException(String requiredPlan, String message) {
    super(message == null ? "Tính năng này yêu cầu gói " + requiredPlan : message);
    this.requiredPlan = requiredPlan;
  }
}