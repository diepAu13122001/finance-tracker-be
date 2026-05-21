package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.transaction.TransactionRequest;
import com.diepau1312.financeTrackerBE.entity.*;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import com.diepau1312.financeTrackerBE.entity.Wallet.*;
import com.diepau1312.financeTrackerBE.exception.*;
import com.diepau1312.financeTrackerBE.repository.*;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Tests")
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserSubscriptionRepository subscriptionRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletService walletService;

    @InjectMocks
    private TransactionService transactionService;

    private static final String EMAIL = "test@gmail.com";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WALLET_ID = UUID.randomUUID();
    private static final UUID TARGET_WALLET_ID = UUID.randomUUID();

    private User mockUser;
    private MockedStatic<SecurityUtil> securityMock;

    @BeforeEach
    void setUp() {
        mockUser = User.builder().id(USER_ID).email(EMAIL).build();
        securityMock = mockStatic(SecurityUtil.class);
        securityMock.when(SecurityUtil::getCurrentUserEmail).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
    }

    @AfterEach
    void tearDown() { securityMock.close(); }

    // ── Helper builders ────────────────────────────────────────────────────────

    /** Tạo ví NORMAL với số dư cho trước */
    private Wallet buildNormalWallet(UUID id, long balance) {
        return Wallet.builder()
                .id(id).user(mockUser).name("Ví test")
                .type(WalletType.NORMAL).currentAmount(balance)
                .status(WalletStatus.ACTIVE).build();
    }

    /** Tạo TransactionRequest cơ bản */
    private TransactionRequest buildRequest(TransactionType type, long amount) {
        TransactionRequest req = new TransactionRequest();
        req.setType(type);
        req.setAmount(amount);
        req.setTransactionDate(LocalDate.now());
        return req;
    }

    /** Mock subscription plan cho user */
    private void mockPlan(String planId) {
        var plan = SubscriptionPlan.builder().id(planId).build();
        var sub  = UserSubscription.builder().plan(plan).status("ACTIVE").build();
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(sub));
    }

    // ─── CREATE INCOME/EXPENSE ─────────────────────────────────────────────────

    @Test
    @DisplayName("Create EXPENSE thành công với ví đủ số dư")
    void create_expense_withSufficientBalance_success() {
        mockPlan("PLUS");
        Wallet wallet = buildNormalWallet(WALLET_ID, 500_000L);
        when(walletRepository.findByIdAndUserId(WALLET_ID, USER_ID))
                .thenReturn(Optional.of(wallet));

        Transaction saved = Transaction.builder()
                .id(UUID.randomUUID()).user(mockUser)
                .type(TransactionType.EXPENSE).amount(100_000L)
                .transactionDate(LocalDate.now()).source("manual").build();
        when(transactionRepository.save(any())).thenReturn(saved);

        TransactionRequest req = buildRequest(TransactionType.EXPENSE, 100_000L);
        req.setWalletId(WALLET_ID);

        var result = transactionService.create(req);

        assertThat(result.getType()).isEqualTo(TransactionType.EXPENSE);
        verify(walletService).recalculateBalance(WALLET_ID);
    }

    @Test
    @DisplayName("Create EXPENSE thất bại khi ví NORMAL không đủ số dư")
    void create_expense_insufficientBalance_throwsAuthException() {
        mockPlan("PLUS");
        Wallet wallet = buildNormalWallet(WALLET_ID, 50_000L); // chỉ có 50k

        when(walletRepository.findByIdAndUserId(WALLET_ID, USER_ID))
                .thenReturn(Optional.of(wallet));

        TransactionRequest req = buildRequest(TransactionType.EXPENSE, 100_000L); // chi 100k
        req.setWalletId(WALLET_ID);

        assertThatThrownBy(() -> transactionService.create(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("không đủ");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Free user tạo giao dịch thứ 51 → throw PlanUpgradeRequired")
    void create_freeUser_atLimit_throwsPlanUpgradeRequired() {
        mockPlan("FREE");

        // Mock đã có 50 giao dịch tháng này
        when(transactionRepository.countByUserIdAndDateBetween(
                eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(50L);

        TransactionRequest req = buildRequest(TransactionType.EXPENSE, 45_000L);

        assertThatThrownBy(() -> transactionService.create(req))
                .isInstanceOf(PlanUpgradeRequiredException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Plus user không bị giới hạn 50 giao dịch")
    void create_plusUser_noTransactionLimit() {
        mockPlan("PLUS");
        // PLUS không gọi countByUserIdAndDateBetween
        Transaction saved = Transaction.builder()
                .id(UUID.randomUUID()).user(mockUser)
                .type(TransactionType.EXPENSE).amount(45_000L)
                .transactionDate(LocalDate.now()).source("manual").build();
        when(transactionRepository.save(any())).thenReturn(saved);

        TransactionRequest req = buildRequest(TransactionType.EXPENSE, 45_000L);
        transactionService.create(req);

        verify(transactionRepository, never()).countByUserIdAndDateBetween(any(), any(), any());
    }

    // ─── INSTALLMENT WALLET VALIDATION ────────────────────────────────────────

    @Test
    @DisplayName("EXPENSE trên ví INSTALLMENT → throw AuthException")
    void create_expense_onInstallmentWallet_throwsAuthException() {
        mockPlan("PLUS");

        // Ví trả góp chỉ nhận INCOME (kỳ thanh toán), không nhận EXPENSE
        Wallet installment = Wallet.builder()
                .id(WALLET_ID).user(mockUser).name("Vay trả góp")
                .type(WalletType.DEBT).subtype(WalletSubtype.INSTALLMENT)
                .currentAmount(10_000_000L).status(WalletStatus.ACTIVE).build();

        when(walletRepository.findByIdAndUserId(WALLET_ID, USER_ID))
                .thenReturn(Optional.of(installment));

        TransactionRequest req = buildRequest(TransactionType.EXPENSE, 500_000L);
        req.setWalletId(WALLET_ID);

        assertThatThrownBy(() -> transactionService.create(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("trả góp");
    }

    // ─── TRANSFER ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Transfer thành công — tạo 2 transactions với cùng transferPairId")
    void createTransfer_success_createsTwoTransactions() {
        mockPlan("PLUS");
        Wallet source = buildNormalWallet(WALLET_ID, 1_000_000L);
        Wallet target = buildNormalWallet(TARGET_WALLET_ID, 0L);

        when(walletRepository.findByIdAndUserId(WALLET_ID, USER_ID))
                .thenReturn(Optional.of(source));
        when(walletRepository.findByIdAndUserId(TARGET_WALLET_ID, USER_ID))
                .thenReturn(Optional.of(target));

        // save() được gọi 2 lần — capture để verify cả 2
        Transaction sourceT = Transaction.builder()
                .id(UUID.randomUUID()).user(mockUser)
                .type(TransactionType.TRANSFER).source("transfer_out")
                .amount(500_000L).transactionDate(LocalDate.now())
                .wallet(source).build();
        when(transactionRepository.save(any())).thenReturn(sourceT);

        TransactionRequest req = buildRequest(TransactionType.TRANSFER, 500_000L);
        req.setWalletId(WALLET_ID);
        req.setTargetWalletId(TARGET_WALLET_ID);

        transactionService.create(req);

        // Phải save đúng 2 lần: 1 transfer_out + 1 transfer_in
        verify(transactionRepository, times(2)).save(any());
        // Phải recalculate cả 2 ví
        verify(walletService).recalculateBalance(WALLET_ID);
        verify(walletService).recalculateBalance(TARGET_WALLET_ID);
    }

    @Test
    @DisplayName("Transfer với cùng ví nguồn và đích → throw AuthException")
    void createTransfer_sameWallet_throwsAuthException() {
        mockPlan("PLUS");

        TransactionRequest req = buildRequest(TransactionType.TRANSFER, 100_000L);
        req.setWalletId(WALLET_ID);
        req.setTargetWalletId(WALLET_ID); // cùng 1 ví

        assertThatThrownBy(() -> transactionService.create(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("giống nhau");
    }

    @Test
    @DisplayName("Transfer thiếu ví đích → throw AuthException")
    void createTransfer_missingTargetWallet_throwsAuthException() {
        mockPlan("PLUS");

        TransactionRequest req = buildRequest(TransactionType.TRANSFER, 100_000L);
        req.setWalletId(WALLET_ID);
        // targetWalletId = null

        assertThatThrownBy(() -> transactionService.create(req))
                .isInstanceOf(AuthException.class);
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Xóa transaction thường — chỉ xóa 1 record")
    void delete_normalTransaction_deletesOne() {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID()).user(mockUser)
                .type(TransactionType.EXPENSE).amount(50_000L)
                .transactionDate(LocalDate.now()).source("manual").build();

        when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));

        transactionService.delete(tx.getId());

        verify(transactionRepository).delete(tx);
        // Không tìm paired transaction vì không phải transfer
        verify(transactionRepository, never()).findByTransferPairIdAndIdNot(any(), any());
    }

    @Test
    @DisplayName("Xóa transfer_out → tự động xóa cả transfer_in (paired)")
    void delete_transferTransaction_deletesBothPair() {
        UUID pairId = UUID.randomUUID();

        Transaction transferOut = Transaction.builder()
                .id(UUID.randomUUID()).user(mockUser)
                .type(TransactionType.TRANSFER).source("transfer_out")
                .transferPairId(pairId).amount(100_000L)
                .transactionDate(LocalDate.now()).build();

        Transaction transferIn = Transaction.builder()
                .id(UUID.randomUUID()).user(mockUser)
                .type(TransactionType.TRANSFER).source("transfer_in")
                .transferPairId(pairId).amount(100_000L)
                .transactionDate(LocalDate.now()).build();

        when(transactionRepository.findById(transferOut.getId()))
                .thenReturn(Optional.of(transferOut));
        when(transactionRepository.findByTransferPairIdAndIdNot(pairId, transferOut.getId()))
                .thenReturn(Optional.of(transferIn));

        transactionService.delete(transferOut.getId());

        // Phải xóa cả 2
        verify(transactionRepository).delete(transferOut);
        verify(transactionRepository).delete(transferIn);
    }

    @Test
    @DisplayName("Xóa transaction của user khác → throw ForbiddenException")
    void delete_otherUserTransaction_throwsForbidden() {
        User anotherUser = User.builder().id(UUID.randomUUID()).email("other@gmail.com").build();
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID()).user(anotherUser) // không phải mockUser
                .type(TransactionType.EXPENSE).amount(50_000L)
                .transactionDate(LocalDate.now()).build();

        when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> transactionService.delete(tx.getId()))
                .isInstanceOf(ForbiddenException.class);

        verify(transactionRepository, never()).delete(any());
    }

    // ─── CREDIT CARD VALIDATION ───────────────────────────────────────────────

    @Test
    @DisplayName("EXPENSE trên thẻ tín dụng vượt hạn mức → throw AuthException")
    void create_expense_exceedsCreditLimit_throwsAuthException() {
        mockPlan("PLUS");

        Wallet creditCard = Wallet.builder()
                .id(WALLET_ID).user(mockUser).name("Thẻ Visa")
                .type(WalletType.DEBT).subtype(WalletSubtype.CREDIT_CARD)
                .currentAmount(9_500_000L) // đã dùng 9.5M
                .creditLimit(10_000_000L)  // hạn mức 10M
                .status(WalletStatus.ACTIVE).build();

        when(walletRepository.findByIdAndUserId(WALLET_ID, USER_ID))
                .thenReturn(Optional.of(creditCard));

        TransactionRequest req = buildRequest(TransactionType.EXPENSE, 600_000L); // vượt 100k
        req.setWalletId(WALLET_ID);

        assertThatThrownBy(() -> transactionService.create(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("không đủ");
    }
}