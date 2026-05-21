package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.entity.*;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import com.diepau1312.financeTrackerBE.repository.TransactionRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportService Tests")
@MockitoSettings(strictness = Strictness.LENIENT)
class ExportServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ExportService exportService;

    private static final String EMAIL = "test@gmail.com";
    private static final UUID USER_ID = UUID.randomUUID();

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
    void tearDown() {
        securityMock.close();
    }

    @Test
    @DisplayName("exportToExcel trả về byte array không rỗng")
    void exportToExcel_returnsNonEmptyBytes() throws Exception {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID()).user(mockUser)
                .type(TransactionType.EXPENSE).amount(45_000L)
                .note("Cà phê").transactionDate(LocalDate.now())
                .source("manual").build();

        Page<Transaction> page = new PageImpl<>(List.of(tx));
        when(transactionRepository.findByUserIdOrderByTransactionDateDesc(
                eq(USER_ID), any())).thenReturn(page);

        byte[] result = exportService.exportToExcel(null, null);

        assertThat(result).isNotEmpty();
        // Excel file bắt đầu bằng PK header (ZIP format)
        assertThat(result[0]).isEqualTo((byte) 'P');
        assertThat(result[1]).isEqualTo((byte) 'K');
    }

    @Test
    @DisplayName("exportToExcel với danh sách rỗng — vẫn tạo file hợp lệ")
    void exportToExcel_emptyTransactions_returnsValidFile() throws Exception {
        Page<Transaction> emptyPage = new PageImpl<>(List.of());
        when(transactionRepository.findByUserIdOrderByTransactionDateDesc(
                eq(USER_ID), any())).thenReturn(emptyPage);

        byte[] result = exportService.exportToExcel(2026, 1);

        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("exportToExcel với transaction có category và wallet — không throw")
    void exportToExcel_withCategoryAndWallet_success() throws Exception {
        Category cat = Category.builder().id(UUID.randomUUID())
                .name("Ăn uống").build();
        Wallet wallet = Wallet.builder().id(UUID.randomUUID())
                .name("Tiền mặt").type(Wallet.WalletType.NORMAL).build();

        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID()).user(mockUser)
                .type(TransactionType.EXPENSE).amount(100_000L)
                .note("Ăn trưa").transactionDate(LocalDate.now())
                .source("manual").category(cat).wallet(wallet).build();

        Page<Transaction> page = new PageImpl<>(List.of(tx));
        when(transactionRepository.findByUserIdOrderByTransactionDateDesc(
                eq(USER_ID), any())).thenReturn(page);

        assertThatCode(() -> exportService.exportToExcel(null, null))
                .doesNotThrowAnyException();
    }
}