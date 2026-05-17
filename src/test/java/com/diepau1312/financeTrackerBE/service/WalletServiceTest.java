package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.wallet.WalletRequest;
import com.diepau1312.financeTrackerBE.dto.wallet.WalletResponse;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.entity.Wallet;
import com.diepau1312.financeTrackerBE.entity.Wallet.WalletStatus;
import com.diepau1312.financeTrackerBE.entity.Wallet.WalletSubtype;
import com.diepau1312.financeTrackerBE.entity.Wallet.WalletType;
import com.diepau1312.financeTrackerBE.entity.UserSubscription;
import com.diepau1312.financeTrackerBE.entity.SubscriptionPlan;
import com.diepau1312.financeTrackerBE.exception.AuthException;
import com.diepau1312.financeTrackerBE.exception.NotFoundException;
import com.diepau1312.financeTrackerBE.exception.PlanUpgradeRequiredException;
import com.diepau1312.financeTrackerBE.repository.TransactionRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.repository.UserSubscriptionRepository;
import com.diepau1312.financeTrackerBE.repository.WalletRepository;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Tests")
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceTest {

  @Mock
  private WalletRepository walletRepository;
  @Mock
  private UserRepository userRepository;
  @Mock
  private TransactionRepository transactionRepository;
  @Mock
  private UserSubscriptionRepository subscriptionRepository;

  @InjectMocks
  private WalletService walletService;

  private static final String EMAIL = "test@gmail.com";
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID WALLET_ID = UUID.randomUUID();

  private User mockUser;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    mockUser = User.builder().id(USER_ID).email(EMAIL).build();
    securityUtilMock = mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::getCurrentUserEmail).thenReturn(EMAIL);
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  // ─── Helpers ───────────────────────────────────────────────────────────

  private SubscriptionPlan buildPlan(String planId) {
    return SubscriptionPlan.builder().id(planId).name(planId).priceVnd(0L).build();
  }

  private UserSubscription buildSubscription(String planId) {
    return UserSubscription.builder()
        .plan(buildPlan(planId))
        .status("ACTIVE")
        .build();
  }

  private WalletRequest normalWalletRequest(String name) {
    WalletRequest req = new WalletRequest();
    req.setName(name);
    req.setType(WalletType.NORMAL);
    return req;
  }

  private WalletRequest debtCreditRequest() {
    WalletRequest req = new WalletRequest();
    req.setName("Thẻ Visa");
    req.setType(WalletType.DEBT);
    req.setSubtype(WalletSubtype.CREDIT_CARD);
    req.setCreditLimit(50_000_000L);
    req.setBillingDate(15);
    return req;
  }

  // ─── Create Tests ──────────────────────────────────────────────────────

  @Test
  @DisplayName("Create NORMAL wallet thành công với PLUS user")
  void create_normalWallet_plusUser_success() {
    when(subscriptionRepository.findByUserId(USER_ID))
        .thenReturn(Optional.of(buildSubscription("PLUS")));

    Wallet saved = Wallet.builder().id(WALLET_ID).user(mockUser)
        .name("Tiền mặt").type(WalletType.NORMAL).currentAmount(0L)
        .status(WalletStatus.ACTIVE).build();
    when(walletRepository.save(any())).thenReturn(saved);

    WalletResponse result = walletService.create(normalWalletRequest("Tiền mặt"));

    assertThat(result.getName()).isEqualTo("Tiền mặt");
    assertThat(result.getType()).isEqualTo(WalletType.NORMAL);
    verify(walletRepository).save(any());
  }

  @Test
  @DisplayName("Free user tạo ví thứ 5 thành công (giới hạn = 5)")
  void create_freeUser_at4wallets_success() {
    when(subscriptionRepository.findByUserId(USER_ID))
        .thenReturn(Optional.of(buildSubscription("FREE")));
    when(walletRepository.countAllByUserId(USER_ID)).thenReturn(4L); // đang có 4, tạo được thêm 1

    Wallet saved = Wallet.builder().id(WALLET_ID).user(mockUser)
        .name("Ví thứ 5").type(WalletType.NORMAL).currentAmount(0L)
        .status(WalletStatus.ACTIVE).build();
    when(walletRepository.save(any())).thenReturn(saved);

    WalletResponse result = walletService.create(normalWalletRequest("Ví thứ 5"));
    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("Free user đã có 5 ví → không tạo được nữa")
  void create_freeUser_atLimit_throwsPlanUpgradeRequired() {
    when(subscriptionRepository.findByUserId(USER_ID))
        .thenReturn(Optional.of(buildSubscription("FREE")));
    when(walletRepository.countAllByUserId(USER_ID)).thenReturn(5L); // đã đủ giới hạn

    assertThatThrownBy(() -> walletService.create(normalWalletRequest("Ví mới")))
        .isInstanceOf(PlanUpgradeRequiredException.class)
        .hasMessageContaining("5");

    verify(walletRepository, never()).save(any());
  }

  @Test
  @DisplayName("Free user: ví đã đóng vẫn được tính vào giới hạn")
  void create_freeUser_cancelledWalletsCounted_throwsIfAtLimit() {
    when(subscriptionRepository.findByUserId(USER_ID))
        .thenReturn(Optional.of(buildSubscription("FREE")));
    // 3 active + 2 cancelled = 5 total → hết giới hạn
    when(walletRepository.countAllByUserId(USER_ID)).thenReturn(5L);

    assertThatThrownBy(() -> walletService.create(normalWalletRequest("Ví mới")))
        .isInstanceOf(PlanUpgradeRequiredException.class);
  }

  @Test
  @DisplayName("PLUS user không bị giới hạn số ví")
  void create_plusUser_noLimit() {
    when(subscriptionRepository.findByUserId(USER_ID))
        .thenReturn(Optional.of(buildSubscription("PLUS")));
    // Plus không check count, nên không cần mock countAll

    Wallet saved = Wallet.builder().id(WALLET_ID).user(mockUser)
        .name("Ví thứ 100").type(WalletType.NORMAL).currentAmount(0L)
        .status(WalletStatus.ACTIVE).build();
    when(walletRepository.save(any())).thenReturn(saved);

    WalletResponse result = walletService.create(normalWalletRequest("Ví thứ 100"));
    assertThat(result).isNotNull();
    verify(walletRepository, never()).countAllByUserId(any()); // Plus không cần đếm
  }

  // ─── Cancel Tests ──────────────────────────────────────────────────────

  @Test
  @DisplayName("Đóng ví ACTIVE thành công")
  void cancel_activeWallet_success() {
    Wallet wallet = Wallet.builder().id(WALLET_ID).user(mockUser)
        .name("Ví test").type(WalletType.NORMAL).status(WalletStatus.ACTIVE).build();
    when(walletRepository.findByIdAndUserId(WALLET_ID, USER_ID)).thenReturn(Optional.of(wallet));
    when(walletRepository.save(any())).thenReturn(wallet);

    WalletResponse result = walletService.cancel(WALLET_ID);
    assertThat(result.getStatus()).isEqualTo(WalletStatus.CANCELLED);
  }

  @Test
  @DisplayName("Đóng ví đã đóng → throw AuthException")
  void cancel_alreadyCancelled_throwsAuthException() {
    Wallet wallet = Wallet.builder().id(WALLET_ID).user(mockUser)
        .name("Ví đã đóng").type(WalletType.NORMAL).status(WalletStatus.CANCELLED).build();
    when(walletRepository.findByIdAndUserId(WALLET_ID, USER_ID)).thenReturn(Optional.of(wallet));

    assertThatThrownBy(() -> walletService.cancel(WALLET_ID))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("đã được đóng");
  }

  // ─── Balance Recalculation Tests ───────────────────────────────────────

  @Test
  @DisplayName("recalculateBalance NORMAL wallet: tính đúng income - expense + transfer")
  void recalculateBalance_normalWallet_correctCalculation() {
    Wallet wallet = Wallet.builder().id(WALLET_ID).user(mockUser)
        .name("Ví test").type(WalletType.NORMAL).currentAmount(0L)
        .status(WalletStatus.ACTIVE).build();
    when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.of(wallet));
    when(walletRepository.save(any())).thenReturn(wallet);

    // income = 5M, expense = 2M, transfer_in = 1M, transfer_out = 500k
    // expected balance = 5M - 2M + 1M - 500k = 3.5M
    when(transactionRepository.sumAmountByWalletIdAndType(WALLET_ID,
        com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType.INCOME))
        .thenReturn(5_000_000L);
    when(transactionRepository.sumAmountByWalletIdAndType(WALLET_ID,
        com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType.EXPENSE))
        .thenReturn(2_000_000L);
    when(transactionRepository.sumTransferByWalletIdAndSource(WALLET_ID, "transfer_in"))
        .thenReturn(1_000_000L);
    when(transactionRepository.sumTransferByWalletIdAndSource(WALLET_ID, "transfer_out"))
        .thenReturn(500_000L);

    walletService.recalculateBalance(WALLET_ID);

    verify(walletRepository).save(argThat(w -> w.getCurrentAmount() == 3_500_000L));
  }

  @Test
  @DisplayName("recalculateBalance DEBT wallet: không tính transfer")
  void recalculateBalance_debtWallet_ignoresTransfer() {
    Wallet wallet = Wallet.builder().id(WALLET_ID).user(mockUser)
        .name("Thẻ tín dụng").type(WalletType.DEBT)
        .subtype(WalletSubtype.CREDIT_CARD).currentAmount(0L)
        .status(WalletStatus.ACTIVE).build();
    when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.of(wallet));
    when(walletRepository.save(any())).thenReturn(wallet);

    // expense (dùng thẻ) = 3M, income (trả thẻ) = 1M
    // expected debt = 3M - 1M = 2M
    when(transactionRepository.sumAmountByWalletIdAndType(WALLET_ID,
        com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType.INCOME))
        .thenReturn(1_000_000L);
    when(transactionRepository.sumAmountByWalletIdAndType(WALLET_ID,
        com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType.EXPENSE))
        .thenReturn(3_000_000L);

    walletService.recalculateBalance(WALLET_ID);

    verify(walletRepository).save(argThat(w -> w.getCurrentAmount() == 2_000_000L));
    // Không được gọi sumTransfer cho DEBT wallet
    verify(transactionRepository, never()).sumTransferByWalletIdAndSource(any(), any());
  }

  @Test
  @DisplayName("recalculateBalance: null walletId không throw exception")
  void recalculateBalance_nullId_doesNothing() {
    assertThatCode(() -> walletService.recalculateBalance(null))
        .doesNotThrowAnyException();
    verify(walletRepository, never()).findById(any());
  }

  // ─── GetAll / GetActive Tests ──────────────────────────────────────────

  @Test
  @DisplayName("getAll trả về tất cả ví kể cả đã đóng")
  void getAll_returnsAllWallets_includingCancelled() {
    Wallet active = Wallet.builder().id(UUID.randomUUID()).user(mockUser)
        .name("Ví active").type(WalletType.NORMAL).currentAmount(0L)
        .status(WalletStatus.ACTIVE).build();
    Wallet cancelled = Wallet.builder().id(UUID.randomUUID()).user(mockUser)
        .name("Ví đã đóng").type(WalletType.NORMAL).currentAmount(0L)
        .status(WalletStatus.CANCELLED).build();

    when(walletRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
        .thenReturn(List.of(active, cancelled));

    List<WalletResponse> result = walletService.getAll();

    assertThat(result).hasSize(2);
    assertThat(result).extracting(WalletResponse::getStatus)
        .containsExactlyInAnyOrder(WalletStatus.ACTIVE, WalletStatus.CANCELLED);
  }

  @Test
  @DisplayName("getActive chỉ trả về ví ACTIVE")
  void getActive_returnsOnlyActiveWallets() {
    Wallet active = Wallet.builder().id(UUID.randomUUID()).user(mockUser)
        .name("Ví active").type(WalletType.NORMAL).currentAmount(0L)
        .status(WalletStatus.ACTIVE).build();

    when(walletRepository.findByUserIdAndStatusOrderByCreatedAtDesc(USER_ID, WalletStatus.ACTIVE))
        .thenReturn(List.of(active));

    List<WalletResponse> result = walletService.getActive();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStatus()).isEqualTo(WalletStatus.ACTIVE);
  }

  // ─── Delete Tests ──────────────────────────────────────────────────────

  @Test
  @DisplayName("Delete ví không tồn tại → throw NotFoundException")
  void delete_notFound_throwsNotFoundException() {
    when(walletRepository.findByIdAndUserId(WALLET_ID, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> walletService.delete(WALLET_ID))
        .isInstanceOf(NotFoundException.class);
    verify(walletRepository, never()).delete(any());
  }

  @Test
  @DisplayName("createDefaultWallet tạo ví Tiền mặt đúng")
  void createDefaultWallet_createsCorrectWallet() {
    when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    walletService.createDefaultWallet(mockUser);

    verify(walletRepository).save(argThat(w ->
        "Tiền mặt".equals(w.getName())
            && w.getType() == WalletType.NORMAL
            && w.getStatus() == WalletStatus.ACTIVE
            && w.getCurrentAmount() == 0L
    ));
  }
}
