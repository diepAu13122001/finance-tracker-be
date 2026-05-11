package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.category.CategoryRequest;
import com.diepau1312.financeTrackerBE.dto.category.CategoryResponse;
import com.diepau1312.financeTrackerBE.entity.Category;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.exception.AuthException;
import com.diepau1312.financeTrackerBE.exception.NotFoundException;
import com.diepau1312.financeTrackerBE.repository.CategoryRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final UserRepository userRepository;

  /**
   * Lấy tất cả categories của user hiện tại.
   * Tham số type (optional): nếu có, chỉ lấy categories cùng type.
   */
  @Transactional(readOnly = true)
  public List<CategoryResponse> getAll(TransactionType type) {
    UUID userId = getCurrentUserId();

    List<Category> categories = (type != null)
        ? categoryRepository.findByUserIdAndTypeOrderByNameAsc(userId, type)
        : categoryRepository.findByUserIdOrderByNameAsc(userId);

    return categories.stream()
        .map(c -> CategoryResponse.from(
            c,
            categoryRepository.countTransactionsByCategoryId(c.getId()),
            categoryRepository.sumAmountByCategoryId(c.getId())  // 👈 THÊM
        ))
        .toList();
  }

  /**
   * Tạo category mới. Validate:
   * - Tên không trùng với category cùng type của user
   */
  @Transactional
  public CategoryResponse create(CategoryRequest request) {
    UUID userId = getCurrentUserId();

    // Validate trùng tên
    if (categoryRepository.existsByUserIdAndNameAndType(
        userId, request.getName(), request.getType())) {
      throw new AuthException(
          "Bạn đã có danh mục " + request.getType() + " tên \""
              + request.getName() + "\"");
    }

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy user"));

    Category category = Category.builder()
        .user(user)
        .name(request.getName().trim())
        .icon(request.getIcon() != null ? request.getIcon() : "tag")
        .color(request.getColor() != null ? request.getColor() : "#82b01e")
        .type(request.getType())
        .build();

    Category saved = categoryRepository.save(category);
    return CategoryResponse.from(saved);
  }

  /**
   * Update category. Validate:
   * - Category phải thuộc về user (ownership)
   * - Tên mới không trùng với category khác cùng type
   */
  @Transactional
  public CategoryResponse update(UUID id, CategoryRequest request) {
    UUID userId = getCurrentUserId();

    Category category = categoryRepository.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));

    // Nếu đổi tên hoặc đổi type, check trùng
    boolean nameChanged = !category.getName().equals(request.getName());
    boolean typeChanged = !category.getType().equals(request.getType());

    if ((nameChanged || typeChanged)
        && categoryRepository.existsByUserIdAndNameAndType(
        userId, request.getName(), request.getType())) {
      throw new AuthException(
          "Bạn đã có danh mục " + request.getType() + " tên \""
              + request.getName() + "\"");
    }

    category.setName(request.getName().trim());
    if (request.getIcon() != null) category.setIcon(request.getIcon());
    if (request.getColor() != null) category.setColor(request.getColor());
    category.setType(request.getType());

    return CategoryResponse.from(category);
  }

  /**
   * Xóa category. Transactions trỏ tới sẽ có category_id = NULL
   * (cấu hình ON DELETE SET NULL trong migration).
   */
  @Transactional
  public void delete(UUID id) {
    UUID userId = getCurrentUserId();

    Category category = categoryRepository.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));

    categoryRepository.delete(category);
  }

  // ── Helpers ──────────────────────────────────────────────────────────

  private UUID getCurrentUserId() {
    String email = SecurityUtil.getCurrentUserEmail();
    return userRepository.findByEmail(email)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy user"))
        .getId();
  }
}