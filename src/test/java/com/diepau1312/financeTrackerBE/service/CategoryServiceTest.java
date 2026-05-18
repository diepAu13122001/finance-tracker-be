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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService.java Tests")
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryServiceTest {

  @Mock
  private CategoryRepository categoryRepository;
  @Mock
  private UserRepository userRepository;

  @InjectMocks
  private CategoryService categoryService;

  // Constants
  private static final String EMAIL = "test@gmail.com";
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID CATEGORY_ID = UUID.randomUUID();

  private User mockUser;
  private CategoryRequest createRequest;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    mockUser = User.builder().id(USER_ID).email(EMAIL).build();

    createRequest = new CategoryRequest();
    createRequest.setName("Ăn uống");
    createRequest.setIcon("utensils");
    createRequest.setColor("#ff748b");
    createRequest.setType(TransactionType.EXPENSE);

    securityUtilMock = mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::getCurrentUserEmail).thenReturn(EMAIL);

    // ✅ Cần cả 2: findByEmail (getCurrentUserId) + findById (create method)
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  @Test
  @DisplayName("Create thành công với data hợp lệ")
  void create_validRequest_returnsResponse() {
    when(categoryRepository.existsByUserIdAndNameAndType(USER_ID, "Ăn uống", TransactionType.EXPENSE))
        .thenReturn(false);

    Category saved = Category.builder().id(CATEGORY_ID).user(mockUser).name("Ăn uống").icon("utensils").color("#ff748b")
        .type(TransactionType.EXPENSE).build();
    when(categoryRepository.save(any())).thenReturn(saved);

    CategoryResponse response = categoryService.create(createRequest);

    assertThat(response.getName()).isEqualTo("Ăn uống");
    assertThat(response.getType()).isEqualTo(TransactionType.EXPENSE);
    assertThat(response.getIcon()).isEqualTo("utensils");
  }

  @Test
  @DisplayName("Create thất bại khi tên trùng cùng type")
  void create_duplicateName_throwsAuthException() {
    when(categoryRepository.existsByUserIdAndNameAndType(USER_ID, "Ăn uống", TransactionType.EXPENSE)).thenReturn(true);

    assertThatThrownBy(() -> categoryService.create(createRequest)).isInstanceOf(AuthException.class)
        .hasMessageContaining("Ăn uống");

    verify(categoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("Create cho phép cùng tên nhưng khác type")
  void create_sameName_differentType_succeeds() {
    // User đã có category EXPENSE "Đầu tư"
    // Giờ tạo INCOME "Đầu tư" — phải thành công
    when(categoryRepository.existsByUserIdAndNameAndType(USER_ID, "Đầu tư", TransactionType.INCOME)).thenReturn(false);

    CategoryRequest req = new CategoryRequest();
    req.setName("Đầu tư");
    req.setType(TransactionType.INCOME);

    Category saved = Category.builder().id(CATEGORY_ID).user(mockUser).name("Đầu tư").type(TransactionType.INCOME)
        .build();
    when(categoryRepository.save(any())).thenReturn(saved);

    CategoryResponse response = categoryService.create(req);
    assertThat(response.getName()).isEqualTo("Đầu tư");
    assertThat(response.getType()).isEqualTo(TransactionType.INCOME);
  }

  @Test
  @DisplayName("GetAll trả về tất cả categories của user")
  void getAll_returnsUserCategories() {
    Category cat1 = Category.builder().id(UUID.randomUUID()).user(mockUser).name("Ăn uống")
        .type(TransactionType.EXPENSE).build();
    Category cat2 = Category.builder().id(UUID.randomUUID()).user(mockUser).name("Lương").type(TransactionType.INCOME)
        .build();

    when(categoryRepository.findByUserIdOrderByNameAsc(USER_ID)).thenReturn(List.of(cat1, cat2));
    when(categoryRepository.countTransactionsByCategoryId(any())).thenReturn(0L);

    List<CategoryResponse> result = categoryService.getAll(null);

    assertThat(result).hasSize(2);
    assertThat(result).extracting(CategoryResponse::getName).containsExactly("Ăn uống", "Lương");
  }

  @Test
  @DisplayName("GetAll filter theo type chỉ trả EXPENSE")
  void getAll_filterByType_returnsFiltered() {
    Category cat1 = Category.builder().id(UUID.randomUUID()).user(mockUser).name("Ăn uống")
        .type(TransactionType.EXPENSE).build();

    when(categoryRepository.findByUserIdAndTypeOrderByNameAsc(USER_ID, TransactionType.EXPENSE))
        .thenReturn(List.of(cat1));
    when(categoryRepository.countTransactionsByCategoryId(any())).thenReturn(0L);

    List<CategoryResponse> result = categoryService.getAll(TransactionType.EXPENSE);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getType()).isEqualTo(TransactionType.EXPENSE);
  }

  @Test
  @DisplayName("Delete thất bại khi category không tồn tại hoặc không thuộc user")
  void delete_notFound_throwsNotFoundException() {
    when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> categoryService.delete(CATEGORY_ID)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("Create child category thành công khi parent hợp lệ")
  void create_childCategory_withValidParent_success() {
    UUID parentId = UUID.randomUUID();
    Category parent = Category.builder()
        .id(parentId).user(mockUser).name("Sinh hoạt")
        .type(TransactionType.EXPENSE).parent(null) // parent là root
        .build();

    when(categoryRepository.findByIdAndUserId(parentId, USER_ID))
        .thenReturn(Optional.of(parent));
    when(categoryRepository.existsByUserIdAndNameAndType(
        USER_ID, "Ăn uống", TransactionType.EXPENSE)).thenReturn(false);

    Category saved = Category.builder().id(CATEGORY_ID).user(mockUser)
        .name("Ăn uống").type(TransactionType.EXPENSE).parent(parent).build();
    when(categoryRepository.save(any())).thenReturn(saved);

    CategoryRequest req = new CategoryRequest();
    req.setName("Ăn uống");
    req.setType(TransactionType.EXPENSE);
    req.setParentCategoryId(parentId);

    CategoryResponse result = categoryService.create(req);

    assertThat(result.getName()).isEqualTo("Ăn uống");
    assertThat(result.getParentCategoryId()).isEqualTo(parentId);
  }

  @Test
  @DisplayName("Create child thất bại khi parent đã là child (>2 cấp)")
  void create_grandchild_throwsException() {
    UUID rootId = UUID.randomUUID();
    UUID parentId = UUID.randomUUID();

    Category root = Category.builder()
        .id(rootId).name("Sinh hoạt").type(TransactionType.EXPENSE).build();
    Category parent = Category.builder()
        .id(parentId).user(mockUser).name("Ăn uống")
        .type(TransactionType.EXPENSE).parent(root) // parent đã có cha → là child
        .build();

    when(categoryRepository.findByIdAndUserId(parentId, USER_ID))
        .thenReturn(Optional.of(parent));
    when(categoryRepository.existsByUserIdAndNameAndType(any(), any(), any()))
        .thenReturn(false);

    CategoryRequest req = new CategoryRequest();
    req.setName("Cà phê");
    req.setType(TransactionType.EXPENSE);
    req.setParentCategoryId(parentId);

    assertThatThrownBy(() -> categoryService.create(req))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("2 mức");
  }

  @Test
  @DisplayName("Delete thất bại khi category đang có children")
  void delete_categoryWithChildren_throwsException() {
    Category cat = Category.builder()
        .id(CATEGORY_ID).user(mockUser).name("Sinh hoạt")
        .type(TransactionType.EXPENSE).build();

    when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
        .thenReturn(Optional.of(cat));
    when(categoryRepository.countChildrenByParentId(CATEGORY_ID)).thenReturn(3L);

    assertThatThrownBy(() -> categoryService.delete(CATEGORY_ID))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("3");

    verify(categoryRepository, never()).delete(any());
  }
}