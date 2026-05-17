package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.annotation.RequiresPlan;
import com.diepau1312.financeTrackerBE.dto.wallet.WalletRequest;
import com.diepau1312.financeTrackerBE.dto.wallet.WalletResponse;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import com.diepau1312.financeTrackerBE.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Wallets", description = "Quản lý ví và nguồn tiền")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    @GetMapping
    @RequiresPlan("PLUS")
    @Operation(summary = "Lấy tất cả ví của user (bao gồm đã đóng)")
    public ResponseEntity<List<WalletResponse>> getAll() {
        return ResponseEntity.ok(walletService.getAll());
    }

    @GetMapping("/active")
    @RequiresPlan("PLUS")
    @Operation(summary = "Lấy ví đang ACTIVE — dùng cho WalletSelector")
    public ResponseEntity<List<WalletResponse>> getActive() {
        return ResponseEntity.ok(walletService.getActive());
    }

    /**
     * Endpoint dành cho Free user: lấy tổng số ví (bao gồm đã đóng).
     * Free user cần biết mình đang dùng bao nhiêu / 5 ví giới hạn.
     */
    @GetMapping("/count")
    @Operation(summary = "Tổng số ví của user — dùng cho Free user hiển thị giới hạn")
    public ResponseEntity<Map<String, Long>> getTotalCount() {
        String email = SecurityUtil.getCurrentUserEmail();
        UUID userId = userRepository.findByEmail(email)
                .orElseThrow().getId();
        long total = walletService.getTotalWalletCount(userId);
        return ResponseEntity.ok(Map.of("total", total, "limit", 5L));
    }

    @PostMapping
    @Operation(summary = "Tạo ví mới (Free: tối đa 5 ví tổng cộng)")
    public ResponseEntity<WalletResponse> create(@Valid @RequestBody WalletRequest request) {
        return ResponseEntity.ok(walletService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPlan("PLUS")
    @Operation(summary = "Cập nhật ví")
    public ResponseEntity<WalletResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody WalletRequest request) {
        return ResponseEntity.ok(walletService.update(id, request));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Đóng ví — soft close, vẫn giữ transactions cũ")
    public ResponseEntity<WalletResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(walletService.cancel(id));
    }

    @DeleteMapping("/{id}")
    @RequiresPlan("PLUS")
    @Operation(summary = "Xóa ví hoàn toàn — transactions liên quan sẽ unset wallet_id")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        walletService.delete(id);
        return ResponseEntity.noContent().build();
    }
}