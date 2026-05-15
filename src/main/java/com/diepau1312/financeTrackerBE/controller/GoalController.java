package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.annotation.RequiresPlan;
import com.diepau1312.financeTrackerBE.dto.goal.GoalRequest;
import com.diepau1312.financeTrackerBE.dto.goal.GoalResponse;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import com.diepau1312.financeTrackerBE.service.GoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Goals", description = "Plus feature — Mục tiêu tài chính")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

  private final GoalService goalService;

  @GetMapping
  @RequiresPlan("PLUS")
  @Operation(summary = "Lấy tất cả goals của user")
  public ResponseEntity<List<GoalResponse>> getAll() {
    return ResponseEntity.ok(goalService.getAll());
  }

  @GetMapping("/active")
  @RequiresPlan("PLUS")
  @Operation(summary = "Lấy goals đang ACTIVE — dùng cho GoalSelector")
  public ResponseEntity<List<GoalResponse>> getActive() {
    return ResponseEntity.ok(goalService.getActive());
  }

  @PostMapping
  @RequiresPlan("PLUS")
  @Operation(summary = "Tạo goal mới")
  public ResponseEntity<GoalResponse> create(@Valid @RequestBody GoalRequest request) {
    String planId = SecurityUtil.getCurrentUserPlan();  // đọc từ SecurityContext
    return ResponseEntity.ok(goalService.create(request));
  }

  @PutMapping("/{id}")
  @RequiresPlan("PLUS")
  @Operation(summary = "Cập nhật goal")
  public ResponseEntity<GoalResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody GoalRequest request) {
    return ResponseEntity.ok(goalService.update(id, request));
  }

  @PatchMapping("/{id}/cancel")
  @RequiresPlan("PLUS")
  @Operation(summary = "Huỷ goal")
  public ResponseEntity<GoalResponse> cancel(@PathVariable UUID id) {
    return ResponseEntity.ok(goalService.cancel(id));
  }

  @DeleteMapping("/{id}")
  @RequiresPlan("PLUS")
  @Operation(summary = "Xóa goal — transactions liên quan sẽ unset goal_id")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    goalService.delete(id);
    return ResponseEntity.noContent().build();
  }
}