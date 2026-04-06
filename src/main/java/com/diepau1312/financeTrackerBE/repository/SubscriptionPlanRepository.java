package com.diepau1312.financeTrackerBE.repository;

import com.diepau1312.financeTrackerBE.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, String> {
    // String vì primary key của subscription_plans là VARCHAR ('FREE', 'PLUS', 'PREMIUM')
    // findById("FREE") → SELECT * FROM subscription_plans WHERE id = 'FREE'
}