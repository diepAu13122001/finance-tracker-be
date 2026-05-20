package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.category.CategoryRequest;
import com.diepau1312.financeTrackerBE.dto.category.CategoryResponse;
import com.diepau1312.financeTrackerBE.dto.category.TopSpendingResponse;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final UserRepository userRepository;

  // ─── Helpers cho monthStartDay ─────────────────────────────────────────────

  /**
   * Tính kỳ ngân sách hiện tại theo monthStartDay của user.
   * VD: monthStartDay = 5, hôm nay 20/5/2026
   * → kỳ hiện tại: [5/5/2026, 4/6/2026]
   * VD: monthStartDay = 5, hôm nay 3/5/2026 (chưa qua ngày 5)
   * → kỳ hiện tại: [5/4/2026, 4/5/2026]
   */
  private LocalDate[] getCurrentPeriod(LocalDate today, int monthStartDay) {
    int day = Math.min(monthStartDay, 28);
    LocalDate start = today.getDayOfMonth() >= day
        ? today.withDayOfMonth(day)
        : today.minusMonths(1).withDayOfMonth(day);
    LocalDate end = start.plusMonths(1).minusDays(1);
    return new LocalDate[]{start, end};
  }

  /**
   * Kỳ ngay trước kỳ hiện tại — dùng để tính rollover
   */
  private LocalDate[] getPreviousPeriod(LocalDate today, int monthStartDay) {
    LocalDate[] current = getCurrentPeriod(today, monthStartDay);
    return new LocalDate[]{current[0].minusMonths(1), current[0].minusDays(1)};
  }

  // ─── Get current user / monthStartDay ──────────────────────────────────────

  private User getCurrentUser() {
    String email = SecurityUtil.getCurrentUserEmail();
    return userRepository.findByEmail(email)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy user"));
  }

  // ─── GET ALL — render tree với rollover đã fix ─────────────────────────────

  @Transactional(readOnly = true)
  public List<CategoryResponse> getAll(TransactionType type) {
    User user = getCurrentUser();
    UUID userId = user.getId();
    int msd = user.getMonthStartDay() != null ? user.getMonthStartDay() : 1;
    LocalDate today = LocalDate.now();

    LocalDate[] currentPeriod = getCurrentPeriod(today, msd);
    LocalDate[] previousPeriod = getPreviousPeriod(today, msd);

    List<Category> roots = categoryRepository.findRootsByUserId(userId, type);

    return roots.stream().map(root -> {
      CategoryResponse rootResp = buildResponse(root, currentPeriod, previousPeriod);

      List<Category> children = categoryRepository.findByParentIdOrderByNameAsc(root.getId());
      List<CategoryResponse> childrenResp = children.stream()
          .map(c -> buildResponse(c, currentPeriod, previousPeriod))
          .toList();

      rootResp.setChildren(childrenResp);
      return rootResp;
    }).toList();
  }

  /**
   * Build response cho 1 category, dùng cho cả root và child
   */
  private CategoryResponse buildResponse(
      Category cat, LocalDate[] currentPeriod, LocalDate[] previousPeriod) {

    Long txCount = categoryRepository.countTransactionsByCategoryId(cat.getId());
    Long totalAmount = categoryRepository.sumAmountByCategoryId(cat.getId());

    // EXPENSE mới tính spent + rollover, INCOME không có budget
    Long currentSpent = cat.getType() == TransactionType.EXPENSE
        ? categoryRepository.sumExpenseByPeriod(cat.getId(), currentPeriod[0], currentPeriod[1])
        : 0L;

    long rollover = cat.getType() == TransactionType.EXPENSE
        ? calculateRollover(cat, previousPeriod)
        : 0L;

    return CategoryResponse.withBudgetProgress(cat, txCount, totalAmount, currentSpent, rollover);
  }

  /**
   * Tính rollover từ kỳ TRƯỚC sang kỳ HIỆN TẠI.
   * <p>
   * ── FIX LỖI #1 ──
   * Trước: luôn tính budget - spent của tháng trước, bất kể có budget cũ không.
   * Sau: chỉ tính nếu category đã có budget từ kỳ trước trở về trước.
   * budgetStartedAt > previousPeriod.start  → tháng trước CHƯA có budget → rollover = 0
   */
  private long calculateRollover(Category category, LocalDate[] previousPeriod) {
    Long budget = category.getMonthlyBudget();
    LocalDate startedAt = category.getBudgetStartedAt();

    // Không có budget hiện tại → không rollover
    if (budget == null || budget <= 0) return 0L;

    // Không biết budget bắt đầu khi nào → safer = không rollover
    if (startedAt == null) return 0L;

    // Budget mới bắt đầu áp dụng từ kỳ này hoặc tương lai → kỳ trước CHƯA có budget
    if (startedAt.isAfter(previousPeriod[0])) return 0L;

    // Tính rollover = budget - spent tháng trước
    Long prevSpent = categoryRepository.sumExpenseByPeriod(
        category.getId(), previousPeriod[0], previousPeriod[1]);
    long spent = prevSpent != null ? prevSpent : 0L;

    return budget - spent;
  }

  // ─── CREATE ────────────────────────────────────────────────────────────────

  @Transactional
  public CategoryResponse create(CategoryRequest request) {
    User user = getCurrentUser();
    UUID userId = user.getId();

    if (categoryRepository.existsByUserIdAndNameAndType(userId, request.getName(), request.getType())) {
      throw new AuthException("Bạn đã có danh mục " + request.getType()
          + " tên \"" + request.getName() + "\"");
    }

    // Validate parent
    Category parent = null;
    if (request.getParentCategoryId() != null) {
      parent = categoryRepository.findByIdAndUserId(request.getParentCategoryId(), userId)
          .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục cha"));

      if (parent.getType() != request.getType()) {
        throw new AuthException("Danh mục cha phải cùng loại "
            + (request.getType() == TransactionType.INCOME ? "thu nhập" : "chi tiêu"));
      }
      if (parent.getParent() != null) {
        throw new AuthException("Chỉ cho phép phân cấp 2 mức. Danh mục cha '"
            + parent.getName() + "' đã là danh mục con.");
      }

      // ── FIX LỖI #3: validate child budget không vượt parent ──
      validateChildBudgetNotExceedParent(parent, request.getMonthlyBudget(), null);
    }

    // ── FIX LỖI #1: set budgetStartedAt nếu có budget ngay khi tạo ──
    LocalDate budgetStartedAt = null;
    if (request.getMonthlyBudget() != null && request.getMonthlyBudget() > 0) {
      int msd = user.getMonthStartDay() != null ? user.getMonthStartDay() : 1;
      budgetStartedAt = getCurrentPeriod(LocalDate.now(), msd)[0];
    }

    Category category = Category.builder()
        .user(user)
        .name(request.getName().trim())
        .icon(request.getIcon() != null ? request.getIcon() : "tag")
        .color(request.getColor() != null ? request.getColor() : "#82b01e")
        .type(request.getType())
        .parent(parent)
        .monthlyBudget(request.getMonthlyBudget())
        .budgetStartedAt(budgetStartedAt)
        .build();

    return CategoryResponse.from(categoryRepository.save(category));
  }

  // ─── UPDATE ────────────────────────────────────────────────────────────────

  @Transactional
  public CategoryResponse update(UUID id, CategoryRequest request) {
    User user = getCurrentUser();
    UUID userId = user.getId();

    Category category = categoryRepository.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));

    boolean nameChanged = !category.getName().equals(request.getName());
    boolean typeChanged = !category.getType().equals(request.getType());

    if ((nameChanged || typeChanged)
        && categoryRepository.existsByUserIdAndNameAndType(userId, request.getName(), request.getType())) {
      throw new AuthException("Bạn đã có danh mục " + request.getType()
          + " tên \"" + request.getName() + "\"");
    }

    if (typeChanged) {
      long childCount = categoryRepository.countChildrenByParentId(id);
      if (childCount > 0) {
        throw new AuthException("Không thể đổi loại danh mục đang có "
            + childCount + " danh mục con. Hãy xóa các con trước.");
      }
    }

    // Validate parent
    Category parent = null;
    if (request.getParentCategoryId() != null) {
      parent = categoryRepository.findByIdAndUserId(request.getParentCategoryId(), userId)
          .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục cha"));

      if (parent.getType() != request.getType()) {
        throw new AuthException("Danh mục cha phải cùng loại "
            + (request.getType() == TransactionType.INCOME ? "thu nhập" : "chi tiêu"));
      }
      if (parent.getParent() != null) {
        throw new AuthException("Chỉ cho phép phân cấp 2 mức.");
      }
      if (parent.getId().equals(category.getId())) {
        throw new AuthException("Không thể chọn chính mình làm cha.");
      }

      // ── FIX LỖI #3: validate budget không vượt parent (loại trừ chính nó) ──
      validateChildBudgetNotExceedParent(parent, request.getMonthlyBudget(), id);
    }

    // Nếu đang là parent (có con) và budget mới giảm → check tổng con không vượt
    if (categoryRepository.countChildrenByParentId(id) > 0
        && request.getMonthlyBudget() != null) {
      Long sumChildren = categoryRepository.sumChildrenBudget(id, null);
      if (sumChildren != null && sumChildren > request.getMonthlyBudget()) {
        throw new AuthException(String.format(
            "Tổng ngân sách các danh mục con (%,d ₫) đang vượt ngân sách mới của cha (%,d ₫). "
                + "Hãy giảm ngân sách các con trước.",
            sumChildren, request.getMonthlyBudget()));
      }
    }

    // ── FIX LỖI #1: cập nhật budgetStartedAt theo state transition ──
    Long oldBudget = category.getMonthlyBudget();
    Long newBudget = request.getMonthlyBudget();
    LocalDate currentPeriodStart = getCurrentPeriod(
        LocalDate.now(),
        user.getMonthStartDay() != null ? user.getMonthStartDay() : 1
    )[0];

    if ((oldBudget == null || oldBudget <= 0) && newBudget != null && newBudget > 0) {
      // null → có giá trị: budget bắt đầu áp dụng từ kỳ này
      category.setBudgetStartedAt(currentPeriodStart);
    } else if (newBudget == null || newBudget <= 0) {
      // xóa budget: reset budgetStartedAt
      category.setBudgetStartedAt(null);
    }
    // có → có (thay đổi giá trị): GIỮ NGUYÊN budgetStartedAt

    category.setName(request.getName().trim());
    if (request.getIcon() != null) category.setIcon(request.getIcon());
    if (request.getColor() != null) category.setColor(request.getColor());
    category.setType(request.getType());
    category.setParent(parent);
    category.setMonthlyBudget(newBudget);

    return CategoryResponse.from(category);
  }

  /**
   * Validate tổng budget các con không vượt budget cha.
   * excludeId: khi update, loại bỏ chính nó khỏi tính tổng để tránh đếm 2 lần.
   */
  private void validateChildBudgetNotExceedParent(
      Category parent, Long newChildBudget, UUID excludeId) {
    if (parent.getMonthlyBudget() == null || newChildBudget == null) return;

    Long siblingsBudget = categoryRepository.sumChildrenBudget(parent.getId(), excludeId);
    long sum = (siblingsBudget != null ? siblingsBudget : 0L) + newChildBudget;

    if (sum > parent.getMonthlyBudget()) {
      throw new AuthException(String.format(
          "Tổng ngân sách các danh mục con (%,d ₫) vượt ngân sách của '%s' (%,d ₫). "
              + "Hãy tăng ngân sách cha hoặc giảm ngân sách con.",
          sum, parent.getName(), parent.getMonthlyBudget()));
    }
  }

  // ─── DELETE ────────────────────────────────────────────────────────────────

  @Transactional
  public void delete(UUID id) {
    UUID userId = getCurrentUser().getId();
    Category category = categoryRepository.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));

    long childCount = categoryRepository.countChildrenByParentId(id);
    if (childCount > 0) {
      throw new AuthException("Danh mục này đang có " + childCount
          + " danh mục con. Hãy xóa các danh mục con trước.");
    }

    categoryRepository.delete(category);
  }

  // ─── TOP SPENDING — FIX LỖI #4: dùng effectiveBudget ───────────────────────

  @Transactional(readOnly = true)
  public List<TopSpendingResponse> getTopSpending(
      Integer year, Integer month, Integer quarter, int limit) {

    User user = getCurrentUser();
    UUID userId = user.getId();
    int msd = user.getMonthStartDay() != null ? user.getMonthStartDay() : 1;
    LocalDate today = LocalDate.now();
    int targetYear = year != null ? year : today.getYear();

    // ── Xác định date range theo filter ──
    LocalDate startDate, endDate;
    boolean isCurrentMonthMode = (quarter == null && year == null
        && (month == null || month == today.getMonthValue()));

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
      // Kỳ hiện tại theo monthStartDay
      LocalDate[] period = getCurrentPeriod(today, msd);
      startDate = period[0];
      endDate = period[1];
    }

    List<Object[]> rows = categoryRepository.findTopSpendingCategories(
        userId, startDate, endDate, limit);

    // Chỉ tính rollover khi đang ở chế độ "tháng hiện tại"
    // Vì query theo quý/năm thì rollover không có ý nghĩa
    LocalDate[] previousPeriod = isCurrentMonthMode
        ? getPreviousPeriod(today, msd)
        : null;

    return rows.stream().map(r -> {
      UUID catId = (UUID) r[0];
      Long baseBudget = r[4] != null ? ((Number) r[4]).longValue() : null;
      Long spent = ((Number) r[5]).longValue();
      java.sql.Date budgetStartedAtSql = (java.sql.Date) r[7];
      LocalDate budgetStartedAt = budgetStartedAtSql != null
          ? budgetStartedAtSql.toLocalDate() : null;

      // Tính rollover & effectiveBudget
      Long rollover = 0L;
      Long effectiveBudget = baseBudget;

      if (baseBudget != null && baseBudget > 0 && previousPeriod != null
          && budgetStartedAt != null
          && !budgetStartedAt.isAfter(previousPeriod[0])) {
        Long prevSpent = categoryRepository.sumExpenseByPeriod(
            catId, previousPeriod[0], previousPeriod[1]);
        rollover = baseBudget - (prevSpent != null ? prevSpent : 0L);
        effectiveBudget = Math.max(0L, baseBudget + rollover);
      }

      Double pct = null;
      boolean over = false;
      if (effectiveBudget != null && effectiveBudget > 0) {
        pct = Math.round((spent * 100.0 / effectiveBudget) * 10.0) / 10.0;
        over = spent > effectiveBudget;
      }

      return TopSpendingResponse.builder()
          .categoryId(catId)
          .name((String) r[1])
          .icon((String) r[2])
          .color((String) r[3])
          .monthlyBudget(baseBudget)
          .effectiveBudget(effectiveBudget)
          .rolloverAmount(rollover)
          .totalSpent(spent)
          .transactionCount(((Number) r[6]).longValue())
          .budgetProgressPercent(pct)
          .overBudget(over)
          .build();
    }).toList();
  }
}