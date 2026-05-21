package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.recurring.*;
import com.diepau1312.financeTrackerBE.dto.transaction.TransactionResponse;
import com.diepau1312.financeTrackerBE.entity.*;
import com.diepau1312.financeTrackerBE.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecurringService Tests")
class RecurringServiceTest {

  @Mock RecurringTransactionRepository recurringRepo;
  @Mock UserRepository                 userRepo;
  @Mock CategoryRepository             categoryRepo;
  @Mock WalletRepository               walletRepo;
  @Mock TransactionService             transactionService;
  @InjectMocks RecurringService        recurringService;

  private final UUID userId = UUID.randomUUID();
  private User user;

  @BeforeEach
  void setUp() {
    // Simulate SecurityContext với userId trong details
    var auth = new UsernamePasswordAuthenticationToken("user@test.com", null, List.of());
    auth.setDetails(userId);
    SecurityContextHolder.getContext().setAuthentication(auth);

    user = new User();
    user.setId(userId);
    user.setEmail("user@test.com");
  }

  @Test
  @DisplayName("findAll: trả về đúng danh sách theo userId")
  void findAll_returnsUserRecurrings() {
    var r = buildRecurring(RecurringTransaction.Frequency.MONTHLY, 0);
    when(recurringRepo.findByUserIdOrderByNextExecutionDateAsc(userId)).thenReturn(List.of(r));

    var result = recurringService.findAll();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getFrequency()).isEqualTo(RecurringTransaction.Frequency.MONTHLY);
  }

  @Test
  @DisplayName("create: lưu entity đúng fields")
  void create_savesCorrectEntity() {
    when(userRepo.findById(userId)).thenReturn(Optional.of(user));
    when(recurringRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var req = new CreateRecurringRequest();
    req.setType(Transaction.TransactionType.EXPENSE);
    req.setAmount(500_000L);
    req.setNote("Tiền điện");
    req.setFrequency(RecurringTransaction.Frequency.MONTHLY);

    var result = recurringService.create(req);

    assertThat(result.getAmount()).isEqualTo(500_000L);
    assertThat(result.getNote()).isEqualTo("Tiền điện");
    assertThat(result.getFrequency()).isEqualTo(RecurringTransaction.Frequency.MONTHLY);
  }

  @Test
  @DisplayName("create: startDate mặc định = hôm nay nếu không truyền")
  void create_defaultsStartDateToToday() {
    when(userRepo.findById(userId)).thenReturn(Optional.of(user));
    when(recurringRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var req = new CreateRecurringRequest();
    req.setType(Transaction.TransactionType.INCOME);
    req.setAmount(10_000_000L);
    req.setFrequency(RecurringTransaction.Frequency.MONTHLY);
    // Không set startDate

    var result = recurringService.create(req);

    assertThat(result.getNextExecutionDate()).isEqualTo(LocalDate.now());
  }

  @Test
  @DisplayName("update: chỉ update fields không null")
  void update_partialUpdate() {
    var r = buildRecurring(RecurringTransaction.Frequency.WEEKLY, 0);
    when(recurringRepo.findByIdAndUserId(r.getId(), userId)).thenReturn(Optional.of(r));
    when(recurringRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var req = new UpdateRecurringRequest();
    req.setAmount(200_000L); // chỉ update amount
    // note = null → giữ nguyên

    recurringService.update(r.getId(), req);

    assertThat(r.getAmount()).isEqualTo(200_000L);
    assertThat(r.getNote()).isEqualTo("Old note"); // unchanged
  }

  @Test
  @DisplayName("delete: xóa đúng entity")
  void delete_removesEntity() {
    var r = buildRecurring(RecurringTransaction.Frequency.DAILY, 0);
    when(recurringRepo.findByIdAndUserId(r.getId(), userId)).thenReturn(Optional.of(r));

    recurringService.delete(r.getId());

    verify(recurringRepo, times(1)).delete(r);
  }

  @Test
  @DisplayName("execute: tạo transaction + tính nextExecutionDate đúng theo frequency")
  void execute_createsTransactionAndAdvancesDate() {
    LocalDate today = LocalDate.now();
    var r = buildRecurring(RecurringTransaction.Frequency.MONTHLY, 0);
    r.setNextExecutionDate(today);

    when(recurringRepo.findByIdAndUserId(r.getId(), userId)).thenReturn(Optional.of(r));
    when(transactionService.create(any())).thenReturn(mock(TransactionResponse.class));
    when(recurringRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    recurringService.execute(r.getId());

    // MONTHLY: nextExecutionDate tăng 1 tháng
    assertThat(r.getNextExecutionDate()).isEqualTo(today.plusMonths(1));
    verify(transactionService, times(1)).create(any());
  }

  // ── Helper ─────────────────────────────────────────────────────────────

  private RecurringTransaction buildRecurring(
      RecurringTransaction.Frequency freq, int daysFromNow) {
    var r = new RecurringTransaction();
    r.setId(UUID.randomUUID());
    r.setUser(user);
    r.setType(Transaction.TransactionType.EXPENSE);
    r.setAmount(100_000L);
    r.setNote("Old note");
    r.setFrequency(freq);
    r.setNextExecutionDate(LocalDate.now().plusDays(daysFromNow));
    r.setIsActive(true);
    return r;
  }
}
