package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.household.*;
import com.diepau1312.financeTrackerBE.entity.*;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemCategory;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemStatus;
import com.diepau1312.financeTrackerBE.exception.ForbiddenException;
import com.diepau1312.financeTrackerBE.exception.NotFoundException;
import com.diepau1312.financeTrackerBE.repository.*;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class HouseholdService {

    private final HouseholdItemRepository householdRepo;
    private final ItemReviewRepository reviewRepo;
    private final UserRepository userRepo;

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private User getCurrentUser() {
        return userRepo.findByEmail(SecurityUtil.getCurrentUserEmail())
                .orElseThrow(() -> new NotFoundException("User không tồn tại"));
    }

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<HouseholdItemResponse> getAll(
            int page, int size, ItemStatus status, ItemCategory category) {

        UUID userId = getCurrentUser().getId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<HouseholdItem> result;
        if (status != null) {
            result = householdRepo.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
        } else if (category != null) {
            result = householdRepo.findByUserIdAndCategoryOrderByCreatedAtDesc(userId, category, pageable);
        } else {
            result = householdRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        return result.map(HouseholdItemResponse::from);
    }

    @Transactional(readOnly = true)
    public HouseholdItemResponse getById(UUID id) {
        UUID userId = getCurrentUser().getId();
        HouseholdItem item = householdRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy mặt hàng"));
        return HouseholdItemResponse.from(item);
    }

    @Transactional
    public HouseholdItemResponse create(HouseholdItemRequest request) {
        User user = getCurrentUser();
        HouseholdItem item = HouseholdItem.builder()
                .user(user)
                .name(request.getName().trim())
                .brand(request.getBrand())
                .category(request.getCategory())
                .price(request.getPrice())
                .purchaseDate(request.getPurchaseDate())
                .expiryDate(request.getExpiryDate())
                .quantity(request.getQuantity())
                .unit(request.getUnit())
                .notifyBeforeDays(request.getNotifyBeforeDays() != null ? request.getNotifyBeforeDays() : 7)
                .notes(request.getNotes())
                .build();
        return HouseholdItemResponse.from(householdRepo.save(item));
    }

    @Transactional
    public HouseholdItemResponse update(UUID id, HouseholdItemRequest request) {
        UUID userId = getCurrentUser().getId();
        HouseholdItem item = householdRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy mặt hàng"));

        item.setName(request.getName().trim());
        item.setBrand(request.getBrand());
        item.setCategory(request.getCategory());
        item.setPrice(request.getPrice());
        item.setPurchaseDate(request.getPurchaseDate());
        item.setExpiryDate(request.getExpiryDate());
        item.setQuantity(request.getQuantity());
        item.setUnit(request.getUnit());
        if (request.getNotifyBeforeDays() != null)
            item.setNotifyBeforeDays(request.getNotifyBeforeDays());
        item.setNotes(request.getNotes());
        return HouseholdItemResponse.from(householdRepo.save(item));
    }

    /**
     * Cập nhật trạng thái (IN_USE → FINISHED / NEED_RESTOCK).
     * Trả về item để frontend biết có cần show review modal không.
     */
    @Transactional
    public HouseholdItemResponse updateStatus(UUID id, ItemStatus newStatus) {
        UUID userId = getCurrentUser().getId();
        HouseholdItem item = householdRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy mặt hàng"));
        item.setStatus(newStatus);
        return HouseholdItemResponse.from(householdRepo.save(item));
    }

    @Transactional
    public void delete(UUID id) {
        UUID userId = getCurrentUser().getId();
        HouseholdItem item = householdRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy mặt hàng"));
        householdRepo.delete(item);
    }

    // ─── Reviews ──────────────────────────────────────────────────────────────

    @Transactional
    public void addReview(UUID itemId, ReviewRequest request) {
        User user = getCurrentUser();
        HouseholdItem item = householdRepo.findByIdAndUserId(itemId, user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy mặt hàng"));

        // Mỗi user chỉ review 1 lần mỗi item — upsert
        ItemReview review = reviewRepo.findByItemIdAndUserId(itemId, user.getId())
                .orElse(ItemReview.builder().item(item).user(user).build());

        review.setRating(request.getRating());
        review.setReviewText(request.getReviewText());
        review.setWouldBuyAgain(request.getWouldBuyAgain());
        reviewRepo.save(review);
    }

    // ─── AI category suggestion (được gọi từ GeminiService) ──────────────────

    @Transactional
    public HouseholdItemResponse saveAiCategory(UUID itemId, String aiCategory) {
        UUID userId = getCurrentUser().getId();
        HouseholdItem item = householdRepo.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy mặt hàng"));
        item.setAiCategory(aiCategory);
        return HouseholdItemResponse.from(householdRepo.save(item));
    }

    // ─── Top Rated Products ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopRated(String category, int limit) {
        UUID userId = getCurrentUser().getId();
        List<Object[]> rows = reviewRepo.findTopRatedItems(userId, category, limit);

        return rows.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("itemId", r[0]);
            map.put("name", r[1]);
            map.put("brand", r[2]);
            map.put("category", r[3]);
            map.put("avgRating", r[4]);
            map.put("reviewCount", r[5]);
            map.put("wouldBuyPct", r[7]);
            return map;
        }).toList();
    }
}