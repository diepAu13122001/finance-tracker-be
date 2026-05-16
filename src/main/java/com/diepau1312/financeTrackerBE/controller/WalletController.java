package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.annotation.RequiresPlan;
import com.diepau1312.financeTrackerBE.dto.wallet.WalletRequest;
import com.diepau1312.financeTrackerBE.dto.wallet.WalletResponse;
import com.diepau1312.financeTrackerBE.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Wallets", description = "Plus feature — Quản lý ví và nguồn tiền")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    @RequiresPlan("PLUS")
    @Operation(summary = "Lấy tất cả ví của user")
    public ResponseEntity<List<WalletResponse>> getAll() {
        return ResponseEntity.ok(walletService.getAll());
    }

    @GetMapping("/active")
    @RequiresPlan("PLUS")
    @Operation(summary = "Lấy ví đang ACTIVE — dùng cho WalletSelector")
    public ResponseEntity<List<WalletResponse>> getActive() {
        return ResponseEntity.ok(walletService.getActive());
    }

    @PostMapping
    @RequiresPlan("PLUS")
    @Operation(summary = "Tạo ví mới")
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
    @RequiresPlan("PLUS")
    @Operation(summary = "Huỷ ví")
    public ResponseEntity<WalletResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(walletService.cancel(id));
    }

    @DeleteMapping("/{id}")
    @RequiresPlan("PLUS")
    @Operation(summary = "Xóa ví — transactions liên quan sẽ unset wallet_id")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        walletService.delete(id);
        return ResponseEntity.noContent().build();
    }
}