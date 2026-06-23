package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.entity.*;
import com.diepau1312.financeTrackerBE.repository.*;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notifRepo;
    private final UserRepository userRepo;

    @Transactional(readOnly = true)
    public Map<String, Object> getNotifications(int page, int size) {
        UUID userId = userRepo.findByEmail(SecurityUtil.getCurrentUserEmail())
                .orElseThrow().getId();
        Pageable pageable = PageRequest.of(page, size);
        var notifPage = notifRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        long unreadCount = notifRepo.countByUserIdAndIsReadFalse(userId);

        return Map.of(
                "content", notifPage.getContent().stream().map(this::toMap).toList(),
                "totalElements", notifPage.getTotalElements(),
                "unreadCount", unreadCount);
    }

    @Transactional
    public void markAllRead() {
        UUID userId = userRepo.findByEmail(SecurityUtil.getCurrentUserEmail())
                .orElseThrow().getId();
        notifRepo.markAllAsRead(userId);
    }

    /**
     * Tạo notification, kiểm tra duplicate trước.
     * Idempotent: cùng user + relatedId + type → chỉ tạo 1 lần.
     */
    @Transactional
    public void createIfNotExists(User user, String type, String title,
            String message, UUID relatedId) {
        if (notifRepo.existsByUserIdAndRelatedIdAndType(user.getId(), relatedId, type)) {
            return; // đã có → không tạo duplicate
        }
        Notification notif = Notification.builder()
                .user(user).type(type).title(title).message(message).relatedId(relatedId)
                .build();
        notifRepo.save(notif);
        log.debug("Created notification: user={}, type={}", user.getEmail(), type);
    }

    private Map<String, Object> toMap(Notification n) {
        return Map.of(
                "id", n.getId(),
                "type", n.getType(),
                "title", n.getTitle() != null ? n.getTitle() : "",
                "message", n.getMessage() != null ? n.getMessage() : "",
                "relatedId", n.getRelatedId() != null ? n.getRelatedId() : "",
                "isRead", n.getIsRead(),
                "createdAt", n.getCreatedAt());
    }
}