package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.entity.HouseholdItem;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemCategory;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemStatus;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.repository.HouseholdItemRepository;
import com.diepau1312.financeTrackerBE.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

  @Mock
  private HouseholdItemRepository householdRepo;
  @Mock
  private UserSubscriptionRepository subscriptionRepo;
  @Mock
  private NotificationService notifService;

  @InjectMocks
  private ExpiryScheduler scheduler;

  @Test
  @DisplayName("checkExpiringItems() — item còn 3 ngày → tạo notification")
  void check_itemExpiringSoon_createsNotification() {
    User user = User.builder().id(UUID.randomUUID()).email("a@b.com").build();
    HouseholdItem item = HouseholdItem.builder()
        .id(UUID.randomUUID()).user(user).name("Kem chống nắng")
        .category(ItemCategory.SKINCARE).status(ItemStatus.IN_USE)
        .notifyBeforeDays(7).expiryDate(LocalDate.now().plusDays(3)).build();

    when(householdRepo.findAllExpiringItems(any(), any())).thenReturn(List.of(item));
    when(subscriptionRepo.findAll()).thenReturn(List.of());

    scheduler.checkExpiringItems();

    verify(notifService).createIfNotExists(
        eq(user), eq("ITEM_EXPIRING"),
        contains("Kem chống nắng"), contains("3"), eq(item.getId()));
  }

  @Test
  @DisplayName("checkExpiringItems() — item còn 15 ngày, ngưỡng 7 → KHÔNG notify")
  void check_itemOutsideThreshold_noNotification() {
    User user = User.builder().id(UUID.randomUUID()).email("a@b.com").build();
    HouseholdItem item = HouseholdItem.builder()
        .id(UUID.randomUUID()).user(user).name("Nước hoa")
        .category(ItemCategory.OTHER).status(ItemStatus.IN_USE)
        .notifyBeforeDays(7).expiryDate(LocalDate.now().plusDays(15)).build();

    when(householdRepo.findAllExpiringItems(any(), any())).thenReturn(List.of(item));
    when(subscriptionRepo.findAll()).thenReturn(List.of());

    scheduler.checkExpiringItems();

    verify(notifService, never()).createIfNotExists(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("checkExpiringItems() — gọi 2 lần → service được gọi 2 lần (service tự dedup)")
  void check_calledTwice_schedulerCallsServiceTwice() {
    User user = User.builder().id(UUID.randomUUID()).email("a@b.com").build();
    HouseholdItem item = HouseholdItem.builder()
        .id(UUID.randomUUID()).user(user).name("Test")
        .category(ItemCategory.FOOD).status(ItemStatus.IN_USE)
        .notifyBeforeDays(7).expiryDate(LocalDate.now().plusDays(2)).build();

    when(householdRepo.findAllExpiringItems(any(), any())).thenReturn(List.of(item));
    when(subscriptionRepo.findAll()).thenReturn(List.of());

    scheduler.checkExpiringItems();
    scheduler.checkExpiringItems();

    verify(notifService, times(2)).createIfNotExists(any(), any(), any(), any(), any());
  }
}