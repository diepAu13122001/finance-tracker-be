package com.diepau1312.financeTrackerBE.repository;

import com.diepau1312.financeTrackerBE.entity.Wallet;
import com.diepau1312.financeTrackerBE.entity.Wallet.WalletStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    List<Wallet> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Wallet> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, WalletStatus status);

    Optional<Wallet> findByIdAndUserId(UUID id, UUID userId);

    /** Đếm ví đang ACTIVE của user */
    @Query("SELECT COUNT(w) FROM Wallet w WHERE w.user.id = :userId AND w.status = 'ACTIVE'")
    long countActiveByUserId(@Param("userId") UUID userId);

    /**
     * Đếm TẤT CẢ ví của user (bao gồm cả CANCELLED) — dùng để giới hạn Free user
     */
    @Query("SELECT COUNT(w) FROM Wallet w WHERE w.user.id = :userId")
    long countAllByUserId(@Param("userId") UUID userId);
}