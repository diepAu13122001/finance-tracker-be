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
import com.diepau1312.financeTrackerBE.dto.category.TopSpendingResponse;

import java.time.LocalDate;
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
    int year = LocalDate.now().getYear();
    int month = LocalDate.now().getMonthValue();

    List<Category> roots = categoryRepository.findRootsByUserId(userId, type);

    return roots.stream().map(root -> {
      Long rootSpent = root.getType() == TransactionType.EXPENSE ? categoryRepository.sumExpenseByMonth(root.getId(), year, month) : 0L;
      long rootRollover = root.getType() == TransactionType.EXPENSE ? calculateRollover(root, year, month) : 0L;

      CategoryResponse rootResp = CategoryResponse.withBudgetProgress(root, categoryRepository.countTransactionsByCategoryId(root.getId()), categoryRepository.sumAmountByCategoryId(root.getId()), rootSpent, rootRollover);

      List<Category> children = categoryRepository.findByParentIdOrderByNameAsc(root.getId());
      List<CategoryResponse> childrenResp = children.stream().map(c -> {
        Long childSpent = c.getType() == TransactionType.EXPENSE ? categoryRepository.sumExpenseByMonth(c.getId(), year, month) : 0L;
        long childRollover = c.getType() == TransactionType.EXPENSE ? calculateRollover(c, year, month) : 0L;
        return CategoryResponse.withBudgetProgress(c, categoryRepository.countTransactionsByCategoryId(c.getId()), categoryRepository.sumAmountByCategoryId(c.getId()), childSpent, childRollover);
      }).toList();

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

    if (categoryRepository.existsByUserIdAndNameAndType(userId, request.getName(), request.getType())) {
      throw new AuthException("Bạn đã có danh mục " + request.getType() + " tên \"" + request.getName() + "\"");
    }

    User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Không tìm thấy user"));

    // Validate parent nếu có
    Category parent = null;
    if (request.getParentCategoryId() != null) {
      parent = categoryRepository.findByIdAndUserId(request.getParentCategoryId(), userId).orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục cha"));

      // Rule 1: parent phải cùng type
      if (parent.getType() != request.getType()) {
        throw new AuthException("Danh mục cha phải cùng loại " + (request.getType() == TransactionType.INCOME ? "thu nhập" : "chi tiêu"));
      }

      // Rule 2: chỉ cho phép 2 cấp — parent không được là child
      if (parent.getParent() != null) {
        throw new AuthException("Chỉ cho phép phân cấp 2 mức. Danh mục cha '" + parent.getName() + "' đã là danh mục con.");
      }
    }

    Category category = Category.builder().user(user).name(request.getName().trim()).icon(request.getIcon() != null ? request.getIcon() : "tag").color(request.getColor() != null ? request.getColor() : "#82b01e").type(request.getType()).parent(parent).monthlyBudget(request.getMonthlyBudget()).build();

    return CategoryResponse.from(categoryRepository.save(category));
  }

  @Transactional
  public CategoryResponse update(UUID id, CategoryRequest request) {
    UUID userId = getCurrentUserId();

    Category category = categoryRepository.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));

    boolean nameChanged = !category.getName().equals(request.getName());
    boolean typeChanged = !category.getType().equals(request.getType());

    if ((nameChanged || typeChanged) && categoryRepository.existsByUserIdAndNameAndType(userId, request.getName(), request.getType())) {
      throw new AuthException("Bạn đã có danh mục " + request.getType() + " tên \"" + request.getName() + "\"");
    }

    // Nếu đổi type → không cho phép nếu category đang là parent (có con)
    if (typeChanged) {
      long childCount = categoryRepository.countChildrenByParentId(id);
      if (childCount > 0) {
        throw new AuthException("Không thể đổi loại danh mục đang có " + childCount + " danh mục con. Hãy xóa các con trước.");
      }
    }


    // Validate parent nếu có
    Category parent = null;
    if (request.getParentCategoryId() != null) {
      parent = categoryRepository.findByIdAndUserId(request.getParentCategoryId(), userId).orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục cha"));

      // Rule 1: parent phải cùng type
      if (parent.getType() != request.getType()) {
        throw new AuthException("Danh mục cha phải cùng loại " + (request.getType() == TransactionType.INCOME ? "thu nhập" : "chi tiêu"));
      }

      // Rule 2: chỉ cho phép 2 cấp — parent không được là child
      if (parent.getParent() != null) {
        throw new AuthException("Chỉ cho phép phân cấp 2 mức. Danh mục cha '" + parent.getName() + "' đã là danh mục con.");
      }

      // Rule 3: cha không được trùng với con
      if (parent.getName().equals(request.getName())) {
        throw new AuthException("Không thể cài đặt Danh mục cha là chính mình.");
      }
    }


    // cap nhat cha -> khong cho phep neu co con
    if (category.getParent() != null && parent != null) {
      long childCount = categoryRepository.countChildrenByParentId(id);
      if (childCount > 0) {
        throw new AuthException("Không thể thêm danh mục cha đang có " + childCount + " danh mục con. Hãy xóa các con trước.");
      }
    }

    category.setName(request.getName().trim());
    if (request.getIcon() != null) category.setIcon(request.getIcon());
    if (request.getColor() != null) category.setColor(request.getColor());
    category.setType(request.getType());
    category.setParent(parent);
    category.setMonthlyBudget(request.getMonthlyBudget()); // null = xóa budget

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

    Category category = categoryRepository.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));

    // Check có children không
    long childCount = categoryRepository.countChildrenByParentId(id);
    if (childCount > 0) {
      throw new AuthException("Danh mục này đang có " + childCount + " danh mục con. Hãy xóa các danh mục con trước.");
    }

    categoryRepository.delete(category);
  }

  private UUID getCurrentUserId() {
    String email = SecurityUtil.getCurrentUserEmail();
    return userRepository.findByEmail(email).orElseThrow(() -> new NotFoundException("Không tìm thấy user")).getId();
  }

  /**
   * Tính rollover từ tháng trước:
   * - Dư (tiêu < budget): rollover dương → tháng này có thêm tiền
   * - Lố (tiêu > budget): rollover âm → tháng này bị trừ
   * Trả về 0 nếu category chưa có budget hoặc không có chi tiêu tháng trước.
   */
  private long calculateRollover(Category category, int currentYear, int currentMonth) {
    if (category.getMonthlyBudget() == null || category.getMonthlyBudget() <= 0) {
      return 0L;
    }

    // Tính tháng trước
    int prevMonth = currentMonth == 1 ? 12 : currentMonth - 1;
    int prevYear = currentMonth == 1 ? currentYear - 1 : currentYear;

    Long prevSpent = categoryRepository.sumExpenseByMonth(category.getId(), prevYear, prevMonth);
    long spent = prevSpent != null ? prevSpent : 0L;

    // rollover = budget - spent
    // Dương = dư, âm = lố
    return category.getMonthlyBudget() - spent;
  }

  @Transactional(readOnly = true)
  public List<TopSpendingResponse> getTopSpending(
      Integer year, Integer month, Integer quarter, int limit
  ) {
    UUID userId = getCurrentUserId();
    LocalDate today = LocalDate.now();
    int targetYear = year != null ? year : today.getYear();
    LocalDate startDate, endDate;

    // Xác định range giống pattern bên TransactionService.getSummary
    if (quarter != null) {
      int startMonth = (quarter - 1) * 3 + 1;
      int endMonth = startMonth + 2;
      startDate = LocalDate.of(targetYear, startMonth, 1);
      endDate = LocalDate.of(targetYear, endMonth, 1)
          .withDayOfMonth(LocalDate.of(targetYear, endMonth, 1).lengthOfMonth());
    } else if (month != null) {
      startDate = LocalDate.of(targetYear, month, 1);
      endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
    } else if (year != null) {
      startDate = LocalDate.of(targetYear, 1, 1);
      endDate = LocalDate.of(targetYear, 12, 31);
    } else {
      startDate = today.withDayOfMonth(1);
      endDate = today.withDayOfMonth(today.lengthOfMonth());
    }

    List<Object[]> rows = categoryRepository.findTopSpendingCategories(
        userId, startDate, endDate, limit
    );

    return rows.stream().map(r -> {
      Long budget = r[4] != null ? ((Number) r[4]).longValue() : null;
      Long spent = ((Number) r[5]).longValue();
      Double pct = null;
      boolean over = false;

      if (budget != null && budget > 0) {
        pct = Math.round((spent * 100.0 / budget) * 10.0) / 10.0;
        over = spent > budget;
      }

      return TopSpendingResponse.builder()
          .categoryId((UUID) r[0])
          .name((String) r[1])
          .icon((String) r[2])
          .color((String) r[3])
          .monthlyBudget(budget)
          .totalSpent(spent)
          .transactionCount(((Number) r[6]).longValue())
          .budgetProgressPercent(pct)
          .overBudget(over)
          .build();
    }).toList();
  }
}