package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.entity.HouseholdItem;
import com.diepau1312.financeTrackerBE.entity.UserSubscription;
import com.diepau1312.financeTrackerBE.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiryScheduler {

    private final HouseholdItemRepository householdRepo;
    private final UserSubscriptionRepository subscriptionRepo;
    private final NotificationService notifService;

    /**
     * Chạy mỗi ngày 8:00 sáng.
     * Cron format: giây phút giờ ngày tháng thứ
     * "0 0 8 * * *" = 8:00:00 AM mỗi ngày
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void checkExpiringItems() {
        LocalDate today = LocalDate.now();

        // Lấy items hết hạn trong 7 ngày tới (max notifyBeforeDays mặc định)
        // Dùng 30 ngày làm max để cover các item có notifyBeforeDays lớn
        LocalDate cutoff = today.plusDays(30);
        List<HouseholdItem> expiringItems = householdRepo.findAllExpiringItems(today, cutoff);

        for (HouseholdItem item : expiringItems) {
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, item.getExpiryDate());

            // Chỉ notify nếu còn trong khoảng notifyBeforeDays
            if (daysLeft > item.getNotifyBeforeDays())
                continue;

            String message = daysLeft == 0
                    ? item.getName() + " hết hạn hôm nay!"
                    : item.getName() + " còn " + daysLeft + " ngày là hết hạn";

            notifService.createIfNotExists(
                    item.getUser(),
                    "ITEM_EXPIRING",
                    "⏰ Sắp hết hạn: " + item.getName(),
                    message,
                    item.getId());
        }

        log.info("Expiry check: processed {} items, notified", expiringItems.size());
    }

    /**
     * Chạy mỗi ngày lúc 0:00 AM.
     * Tìm subscription ACTIVE đã hết hạn → downgrade về FREE.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void checkExpiredSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        List<UserSubscription> expiredSubs = subscriptionRepo.findAll().stream()
                .filter(s -> "ACTIVE".equals(s.getStatus())
                        && s.getExpiresAt() != null
                        && s.getExpiresAt().isBefore(now))
                .toList();

        for (UserSubscription sub : expiredSubs) {
            sub.setStatus("EXPIRED");
            // Không downgrade plan ngay — user vẫn thấy data, nhưng features bị khoá
            // Plan check dựa trên status trong JWT → cần re-login để nhận FREE

            notifService.createIfNotExists(
                    sub.getUser(),
                    "SUBSCRIPTION_EXPIRING",
                    "⚠️ Gói Premium đã hết hạn",
                    "Gói Premium của bạn đã hết hạn. Gia hạn để tiếp tục sử dụng tất cả tính năng.",
                    sub.getId());

            log.info("Subscription expired: user={}", sub.getUser().getEmail());
        }
    }
}