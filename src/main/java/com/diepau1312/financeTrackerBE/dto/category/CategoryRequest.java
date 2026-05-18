package com.diepau1312.financeTrackerBE.dto.category;

import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CategoryRequest {

  @NotBlank(message = "Tên danh mục không được để trống")
  @Size(max = 50, message = "Tên danh mục tối đa 50 ký tự")
  private String name;

  @Size(max = 20)
  private String icon;

  @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Màu phải là mã hex 6 ký tự (vd: #82b01e)")
  private String color;

  @NotNull(message = "Loại danh mục không được để trống")
  private TransactionType type;

  // nullable — nếu null thì là root category (cấp 1)
  // Nếu không null thì là child, parent_id trỏ về root
  private UUID parentCategoryId;
}