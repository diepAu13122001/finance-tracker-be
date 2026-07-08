package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.household.HouseholdItemRequest;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemCategory;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemStatus;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.exception.NotFoundException;
import com.diepau1312.financeTrackerBE.repository.HouseholdItemRepository;
import com.diepau1312.financeTrackerBE.repository.ItemReviewRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HouseholdServiceTest {

  @Mock
  private HouseholdItemRepository householdRepo;
  @Mock
  private ItemReviewRepository reviewRepo;
  @Mock
  private UserRepository userRepo;
  @InjectMocks
  private HouseholdService householdService;

  private User mockUser;

  @BeforeEach
  void setUp() {
    mockUser = User.builder()
        .id(UUID.randomUUID()).email("test@gmail.com").firstName("Diep").build();

    Authentication auth = mock(Authentication.class);
    when(auth.getName()).thenReturn("test@gmail.com");
    SecurityContext ctx = mock(SecurityContext.class);
    when(ctx.getAuthentication()).thenReturn(auth);
    SecurityContextHolder.setContext(ctx);

    when(userRepo.findByEmail("test@gmail.com")).thenReturn(Optional.of(mockUser));
  }

  @Test
  @DisplayName("create() — lưu item và trả về response đúng")
  void create_success() {
    HouseholdItemRequest req = new HouseholdItemRequest();
    req.setName("Sữa rửa mặt");
    req.setCategory(ItemCategory.SKINCARE);
    req.setNotifyBeforeDays(7);

    HouseholdItem savedItem = HouseholdItem.builder()
        .id(UUID.randomUUID()).user(mockUser).name("Sữa rửa mặt")
        .category(ItemCategory.SKINCARE).status(ItemStatus.IN_USE)
        .notifyBeforeDays(7).build();

    when(householdRepo.save(any(HouseholdItem.class))).thenReturn(savedItem);

    var response = householdService.create(req);

    assertThat(response.getName()).isEqualTo("Sữa rửa mặt");
    assertThat(response.getCategory()).isEqualTo(ItemCategory.SKINCARE);
    assertThat(response.getStatus()).isEqualTo(ItemStatus.IN_USE);
    verify(householdRepo).save(any(HouseholdItem.class));
  }

  @Test
  @DisplayName("delete() — item của user khác → NotFoundException")
  void delete_itemNotFound_throws() {
    UUID itemId = UUID.randomUUID();
    when(householdRepo.findByIdAndUserId(itemId, mockUser.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> householdService.delete(itemId))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Không tìm thấy");

    verify(householdRepo, never()).delete(any());
  }

  @Test
  @DisplayName("updateStatus() — đổi sang FINISHED thành công")
  void updateStatus_toFinished() {
    UUID itemId = UUID.randomUUID();
    HouseholdItem item = HouseholdItem.builder()
        .id(itemId).user(mockUser).name("Kem chống nắng")
        .category(ItemCategory.SKINCARE).status(ItemStatus.IN_USE)
        .notifyBeforeDays(7).build();

    when(householdRepo.findByIdAndUserId(itemId, mockUser.getId())).thenReturn(Optional.of(item));
    when(householdRepo.save(any())).thenReturn(item);

    householdService.updateStatus(itemId, ItemStatus.FINISHED);

    verify(householdRepo).save(argThat(i -> i.getStatus() == ItemStatus.FINISHED));
  }

  @Test
  @DisplayName("HouseholdItemResponse — expiringSoon = true khi còn 3 ngày")
  void response_expiringSoon_correct() {
    HouseholdItem item = HouseholdItem.builder()
        .id(UUID.randomUUID()).user(mockUser).name("Test item")
        .category(ItemCategory.FOOD).status(ItemStatus.IN_USE)
        .notifyBeforeDays(7).expiryDate(LocalDate.now().plusDays(3)).build();

    var response = com.diepau1312.financeTrackerBE.dto.household.HouseholdItemResponse.from(item);

    assertThat(response.isExpiringSoon()).isTrue();
    assertThat(response.isExpired()).isFalse();
    assertThat(response.getDaysUntilExpiry()).isEqualTo(3L);
  }

  @Test
  @DisplayName("HouseholdItemResponse — expired = true khi quá hạn")
  void response_expired_correct() {
    HouseholdItem item = HouseholdItem.builder()
        .id(UUID.randomUUID()).user(mockUser).name("Hết hạn rồi")
        .category(ItemCategory.FOOD).status(ItemStatus.IN_USE)
        .notifyBeforeDays(7).expiryDate(LocalDate.now().minusDays(2)).build();

    var response = com.diepau1312.financeTrackerBE.dto.household.HouseholdItemResponse.from(item);

    assertThat(response.isExpired()).isTrue();
    assertThat(response.isExpiringSoon()).isFalse();
    assertThat(response.getDaysUntilExpiry()).isEqualTo(-2L);
  }
}