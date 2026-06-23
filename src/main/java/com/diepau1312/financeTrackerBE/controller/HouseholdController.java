package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.annotation.RequiresPlan;
import com.diepau1312.financeTrackerBE.dto.common.PageResponse;
import com.diepau1312.financeTrackerBE.dto.household.*;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemCategory;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemStatus;
import com.diepau1312.financeTrackerBE.service.HouseholdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Household", description = "Premium feature — theo dõi đồ dùng gia đình")
@RestController
@RequestMapping("/api/household")
@RequiredArgsConstructor
public class HouseholdController {

    private final HouseholdService householdService;

    @GetMapping
    @RequiresPlan("PREMIUM")
    @Operation(summary = "Danh sách đồ dùng — filter theo status/category")
    public ResponseEntity<PageResponse<HouseholdItemResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) ItemCategory category) {

        return ResponseEntity.ok(
                PageResponse.from(householdService.getAll(page, size, status, category)));
    }

    @GetMapping("/{id}")
    @RequiresPlan("PREMIUM")
    public ResponseEntity<HouseholdItemResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(householdService.getById(id));
    }

    @PostMapping
    @RequiresPlan("PREMIUM")
    @Operation(summary = "Thêm đồ dùng mới")
    public ResponseEntity<HouseholdItemResponse> create(
            @Valid @RequestBody HouseholdItemRequest request) {
        return ResponseEntity.ok(householdService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPlan("PREMIUM")
    public ResponseEntity<HouseholdItemResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody HouseholdItemRequest request) {
        return ResponseEntity.ok(householdService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @RequiresPlan("PREMIUM")
    @Operation(summary = "Cập nhật trạng thái: IN_USE, FINISHED, NEED_RESTOCK")
    public ResponseEntity<HouseholdItemResponse> updateStatus(
            @PathVariable UUID id,
            @RequestParam ItemStatus status) {
        return ResponseEntity.ok(householdService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @RequiresPlan("PREMIUM")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        householdService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/review")
    @RequiresPlan("PREMIUM")
    @Operation(summary = "Đánh giá sản phẩm sau khi dùng xong")
    public ResponseEntity<Void> addReview(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewRequest request) {
        householdService.addReview(id, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/top-rated")
    @RequiresPlan("PREMIUM")
    @Operation(summary = "Top sản phẩm được đánh giá cao nhất")
    public ResponseEntity<List<Map<String, Object>>> getTopRated(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(householdService.getTopRated(category, limit));
    }
}