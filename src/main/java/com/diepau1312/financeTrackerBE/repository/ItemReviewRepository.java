package com.diepau1312.financeTrackerBE.repository;

import com.diepau1312.financeTrackerBE.entity.ItemReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemReviewRepository extends JpaRepository<ItemReview, UUID> {

    List<ItemReview> findByItemIdOrderByReviewedAtDesc(UUID itemId);

    Optional<ItemReview> findByItemIdAndUserId(UUID itemId, UUID userId);

    /** Lấy top items theo rating trung bình của user — dùng cho TopProductsPage */
    @Query(value = """
            SELECT h.id, h.name, h.brand, h.category,
                   AVG(r.rating)::NUMERIC(3,1)    AS avg_rating,
                   COUNT(r.id)                    AS review_count,
                   BOOL_OR(r.would_buy_again)     AS any_would_buy,
                   COUNT(CASE WHEN r.would_buy_again THEN 1 END) * 100.0
                       / NULLIF(COUNT(r.id), 0)   AS would_buy_pct
            FROM household_items h
            JOIN item_reviews r ON r.item_id = h.id
            WHERE h.user_id = CAST(:userId AS uuid)
              AND (:category IS NULL OR h.category = :category)
            GROUP BY h.id, h.name, h.brand, h.category
            ORDER BY avg_rating DESC, review_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopRatedItems(
            @Param("userId") UUID userId,
            @Param("category") String category,
            @Param("limit") int limit);
}