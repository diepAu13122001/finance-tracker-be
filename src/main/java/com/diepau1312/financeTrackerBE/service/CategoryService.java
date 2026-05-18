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
     * Lấy categories dạng cây (tree):
     * - Mỗi root category được populate `children`
     * - Children được sắp xếp theo tên
     * - Optional filter theo type
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll(TransactionType type) {
        UUID userId = getCurrentUserId();

        // 1. Lấy tất cả root categories (parent IS NULL)
        List<Category> roots = categoryRepository.findRootsByUserId(userId, type);

        // 2. Với mỗi root, build response kèm children
        return roots.stream().map(root -> {
            CategoryResponse rootResp = CategoryResponse.from(
                    root,
                    categoryRepository.countTransactionsByCategoryId(root.getId()),
                    categoryRepository.sumAmountByCategoryId(root.getId()));

            // Lấy children của root này
            List<Category> children = categoryRepository.findByParentIdOrderByNameAsc(root.getId());
            List<CategoryResponse> childrenResp = children.stream()
                    .map(c -> CategoryResponse.from(
                            c,
                            categoryRepository.countTransactionsByCategoryId(c.getId()),
                            categoryRepository.sumAmountByCategoryId(c.getId())))
                    .toList();

            rootResp.setChildren(childrenResp);
            return rootResp;
        }).toList();
    }

    /**
     * Tạo category mới. Validation:
     * - Tên không trùng cùng type với category của user
     * - Nếu có parent: parent phải tồn tại, cùng user, cùng type
     * - Parent không được là child (chỉ cho phép 2 cấp)
     */
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        UUID userId = getCurrentUserId();

        if (categoryRepository.existsByUserIdAndNameAndType(
                userId, request.getName(), request.getType())) {
            throw new AuthException("Bạn đã có danh mục " + request.getType()
                    + " tên \"" + request.getName() + "\"");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy user"));

        // Validate parent nếu có
        Category parent = null;
        if (request.getParentCategoryId() != null) {
            parent = categoryRepository.findByIdAndUserId(request.getParentCategoryId(), userId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục cha"));

            // Rule 1: parent phải cùng type
            if (parent.getType() != request.getType()) {
                throw new AuthException("Danh mục cha phải cùng loại "
                        + (request.getType() == TransactionType.INCOME ? "thu nhập" : "chi tiêu"));
            }

            // Rule 2: chỉ cho phép 2 cấp — parent không được là child
            if (parent.getParent() != null) {
                throw new AuthException("Chỉ cho phép phân cấp 2 mức. Danh mục cha '"
                        + parent.getName() + "' đã là danh mục con.");
            }
        }

        Category category = Category.builder()
                .user(user)
                .name(request.getName().trim())
                .icon(request.getIcon() != null ? request.getIcon() : "tag")
                .color(request.getColor() != null ? request.getColor() : "#82b01e")
                .type(request.getType())
                .parent(parent)
                .build();

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        UUID userId = getCurrentUserId();

        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));

        boolean nameChanged = !category.getName().equals(request.getName());
        boolean typeChanged = !category.getType().equals(request.getType());

        if ((nameChanged || typeChanged)
                && categoryRepository.existsByUserIdAndNameAndType(
                        userId, request.getName(), request.getType())) {
            throw new AuthException("Bạn đã có danh mục " + request.getType()
                    + " tên \"" + request.getName() + "\"");
        }

        // Nếu đổi type → không cho phép nếu category đang là parent (có con)
        if (typeChanged) {
            long childCount = categoryRepository.countChildrenByParentId(id);
            if (childCount > 0) {
                throw new AuthException("Không thể đổi loại danh mục đang có "
                        + childCount + " danh mục con. Hãy xóa các con trước.");
            }
        }

        category.setName(request.getName().trim());
        if (request.getIcon() != null)
            category.setIcon(request.getIcon());
        if (request.getColor() != null)
            category.setColor(request.getColor());
        category.setType(request.getType());

        return CategoryResponse.from(category);
    }

    /**
     * Xóa category. Rule:
     * - Không cho xóa category đang có children
     * - Transactions liên quan: category_id sẽ thành NULL (ON DELETE SET NULL)
     */
    @Transactional
    public void delete(UUID id) {
        UUID userId = getCurrentUserId();

        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));

        // Check có children không
        long childCount = categoryRepository.countChildrenByParentId(id);
        if (childCount > 0) {
            throw new AuthException("Danh mục này đang có " + childCount
                    + " danh mục con. Hãy xóa các danh mục con trước.");
        }

        categoryRepository.delete(category);
    }

    private UUID getCurrentUserId() {
        String email = SecurityUtil.getCurrentUserEmail();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy user"))
                .getId();
    }
}