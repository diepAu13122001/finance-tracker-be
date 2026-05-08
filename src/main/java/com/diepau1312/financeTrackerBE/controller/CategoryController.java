package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.dto.category.CategoryRequest;
import com.diepau1312.financeTrackerBE.dto.category.CategoryResponse;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import com.diepau1312.financeTrackerBE.annotation.RequiresPlan;
import com.diepau1312.financeTrackerBE.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Plus feature — quản lý danh mục thu chi")
public class CategoryController {

  private final CategoryService categoryService;

  @GetMapping
  @RequiresPlan("PLUS")
  @Operation(summary = "Lấy danh sách categories của user",
      description = "Optional filter theo type INCOME hoặc EXPENSE")
  public ResponseEntity<List<CategoryResponse>> getAll(
      @RequestParam(required = false) TransactionType type) {
    return ResponseEntity.ok(categoryService.getAll(type));
  }

  @PostMapping
  @RequiresPlan("PLUS")
  @Operation(summary = "Tạo category mới")
  public ResponseEntity<CategoryResponse> create(
      @Valid @RequestBody CategoryRequest request) {
    return ResponseEntity.ok(categoryService.create(request));
  }

  @PutMapping("/{id}")
  @RequiresPlan("PLUS")
  @Operation(summary = "Cập nhật category")
  public ResponseEntity<CategoryResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody CategoryRequest request) {
    return ResponseEntity.ok(categoryService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequiresPlan("PLUS")
  @Operation(summary = "Xóa category — transactions liên quan sẽ unset category")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    categoryService.delete(id);
    return ResponseEntity.noContent().build();  // 204
  }
}