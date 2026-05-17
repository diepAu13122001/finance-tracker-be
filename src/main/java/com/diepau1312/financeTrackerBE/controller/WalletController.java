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

    // ── Không có @RequiresPlan ──────────────────────────────────────────────
    // Free user cũng được xem ví "Tiền mặt" mặc định của họ.
    // Trước đây có @RequiresPlan("PLUS") → Free user bị chặn ngay tại API,
    // React nhận 403, api.ts redirect sang /pricing, không quay lại được.
    @GetMapping
    @Operation(summary = "Lấy tất cả ví (kể cả đã đóng). Mọi plan đều truy cập được.")
    public ResponseEntity<List<WalletResponse>> getAll() {
        return ResponseEntity.ok(walletService.getAll());
    }

    @GetMapping("/active")
    @Operation(summary = "Lấy ví đang ACTIVE — dùng cho WalletSelector trong form tạo transaction")
    public ResponseEntity<List<WalletResponse>> getActive() {
        return ResponseEntity.ok(walletService.getActive());
    }

    /**
     * Trả về số ví ACTIVE hiện tại (không tính CANCELLED).
     * Free user giới hạn 5 ví ACTIVE — ví đã đóng không tính vào giới hạn.
     * Endpoint này không cần plan gate.
     */
    @GetMapping("/count")
    @Operation(summary = "Số ví ACTIVE — hiển thị giới hạn cho Free user")
    public ResponseEntity<Map<String, Long>> getActiveCount() {
        String email = SecurityUtil.getCurrentUserEmail();
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        long total = walletService.getActiveWalletCount(userId);
        return ResponseEntity.ok(Map.of("total", total, "limit", 5L));
    }

    @PostMapping
    @Operation(summary = "Tạo ví mới (Free: tối đa 5 ví ACTIVE cùng lúc)")
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
    @Operation(summary = "Đóng ví — soft close, giải phóng 1 slot cho Free user")
    public ResponseEntity<WalletResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(walletService.cancel(id));
    }

    /**
     * Mở lại ví đã đóng.
     * Free user: chỉ được reopen nếu số ví ACTIVE hiện tại < 5.
     */
    @PatchMapping("/{id}/reopen")
    @Operation(summary = "Mở lại ví đã đóng")
    public ResponseEntity<WalletResponse> reopen(@PathVariable UUID id) {
        return ResponseEntity.ok(walletService.reopen(id));
    }

    @DeleteMapping("/{id}")
    @RequiresPlan("PLUS")
    @Operation(summary = "Xóa ví hoàn toàn")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        walletService.delete(id);
        return ResponseEntity.noContent().build();
    }
}