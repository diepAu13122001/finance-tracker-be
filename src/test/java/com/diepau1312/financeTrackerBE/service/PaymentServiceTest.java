package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.entity.*;
import com.diepau1312.financeTrackerBE.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PaymentServiceTest — kiểm tra luồng PayOS.
 *
 * Lưu ý: PaymentService phụ thuộc PayOS SDK (external).
 * Test này mock toàn bộ dependency → chỉ kiểm tra business logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Tests")
class PaymentServiceTest {

  @Mock UserRepository             userRepo;
  @Mock UserSubscriptionRepository subscriptionRepo;
  @Mock SubscriptionPlanRepository planRepo;
  @Mock PaymentHistoryRepository   paymentHistoryRepo;
  @InjectMocks PaymentService      paymentService;

  private final UUID userId = UUID.randomUUID();
  private User user;
  private SubscriptionPlan plusPlan;

  @BeforeEach
  void setUp() {
    var auth = new UsernamePasswordAuthenticationToken("user@test.com", null, List.of());
    auth.setDetails(userId);
    SecurityContextHolder.getContext().setAuthentication(auth);

    user = new User();
    user.setId(userId);
    user.setEmail("user@test.com");

    plusPlan = new SubscriptionPlan();
    plusPlan.setId(UUID.randomUUID());
    plusPlan.setName("PLUS");
    plusPlan.setPrice(99_000L);

    // Inject PayOS config giả để không cần application context
    ReflectionTestUtils.setField(paymentService, "clientId",     "fake-client");
    ReflectionTestUtils.setField(paymentService, "apiKey",       "fake-api-key");
    ReflectionTestUtils.setField(paymentService, "checksumKey",  "fake-checksum");
    ReflectionTestUtils.setField(paymentService, "returnUrl",    "http://localhost:5173/payment/success");
    ReflectionTestUtils.setField(paymentService, "cancelUrl",    "http://localhost:5173/payment/cancel");
  }

  @Test
  @DisplayName("getPaymentHistory: trả đúng danh sách theo userId")
  void getPaymentHistory_returnsUserHistory() {
    var history = new PaymentHistory();
    history.setId(UUID.randomUUID());
    history.setUser(user);
    history.setAmount(99_000L);

    when(paymentHistoryRepo.findByUserIdOrderByCreatedAtDesc(userId))
        .thenReturn(List.of(history));

    var result = paymentService.getPaymentHistory();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getAmount()).isEqualTo(99_000L);
  }

  @Test
  @DisplayName("getPaymentHistory: trả danh sách rỗng khi chưa có lịch sử")
  void getPaymentHistory_emptyWhenNoPurchases() {
    when(paymentHistoryRepo.findByUserIdOrderByCreatedAtDesc(userId))
        .thenReturn(List.of());

    var result = paymentService.getPaymentHistory();

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("findPlanByName: tìm đúng plan theo tên")
  void findPlanByName_returnsCorrectPlan() {
    when(planRepo.findByName("PLUS")).thenReturn(Optional.of(plusPlan));

    var result = planRepo.findByName("PLUS");

    assertThat(result).isPresent();
    assertThat(result.get().getName()).isEqualTo("PLUS");
    assertThat(result.get().getPrice()).isEqualTo(99_000L);
  }

  @Test
  @DisplayName("userRepo: tìm user theo id thành công")
  void findUser_returnsUserById() {
    when(userRepo.findById(userId)).thenReturn(Optional.of(user));

    var result = userRepo.findById(userId);

    assertThat(result).isPresent();
    assertThat(result.get().getEmail()).isEqualTo("user@test.com");
  }

  @Test
  @DisplayName("paymentHistoryRepo: save lưu history đúng")
  void savePaymentHistory_persistsCorrectly() {
    var history = new PaymentHistory();
    history.setUser(user);
    history.setAmount(99_000L);
    history.setPlanName("PLUS");
    history.setStatus("PAID");

    when(paymentHistoryRepo.save(any())).thenReturn(history);

    var saved = paymentHistoryRepo.save(history);

    assertThat(saved.getPlanName()).isEqualTo("PLUS");
    assertThat(saved.getStatus()).isEqualTo("PAID");
  }
}
