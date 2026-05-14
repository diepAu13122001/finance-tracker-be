package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.goal.GoalRequest;
import com.diepau1312.financeTrackerBE.dto.goal.GoalResponse;
import com.diepau1312.financeTrackerBE.entity.Goal;
import com.diepau1312.financeTrackerBE.entity.Goal.*;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.exception.NotFoundException;
import com.diepau1312.financeTrackerBE.repository.GoalRepository;
import com.diepau1312.financeTrackerBE.repository.TransactionRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoalService Tests")
class GoalServiceTest {

  @Mock
  private GoalRepository goalRepository;
  @Mock
  private UserRepository userRepository;
  @Mock
  private TransactionRepository transactionRepository;

  @InjectMocks
  private GoalService goalService;

  private static final String EMAIL = "test@gmail.com";
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID GOAL_ID = UUID.randomUUID();

  private User mockUser;
  private GoalRequest createRequest;
  private MockedStatic<SecurityUtil> securityMock;

  @BeforeEach
  void setUp() {
    mockUser = User.builder().id(USER_ID).email(EMAIL).build();

    createRequest = new GoalRequest();
    createRequest.setName("Mua iPhone");
    createRequest.setType(GoalType.SAVINGS);
    createRequest.setTargetAmount(20_000_000L);

    securityMock = mockStatic(SecurityUtil.class);
    securityMock.when(SecurityUtil::getCurrentUserEmail).thenReturn(EMAIL);
  }

  @AfterEach
  void tearDown() {
    if (securityMock != null) securityMock.close();
  }

  @Test
  @DisplayName("Create goal thành công")
  void create_valid_returnsResponse() {
    lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
    lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));
    lenient().when(goalRepository.countActiveByUserId(USER_ID)).thenReturn(0L);

    Goal saved = Goal.builder()
        .id(GOAL_ID).user(mockUser)
        .name("Mua iPhone").type(GoalType.SAVINGS)
        .targetAmount(20_000_000L).currentAmount(0L)
        .status(GoalStatus.ACTIVE).build();
    lenient().when(goalRepository.save(any())).thenReturn(saved);

    GoalResponse res = goalService.create(createRequest, "PLUS");

    assertThat(res.getName()).isEqualTo("Mua iPhone");
    assertThat(res.getProgressPercent()).isEqualTo(0.0);
    assertThat(res.getStatus()).isEqualTo(GoalStatus.ACTIVE);
  }

  @Test
  @DisplayName("Create thất bại khi Free user vượt 5 wallet")
  void create_freeUser_exceeds5Wallets_throws() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));
    when(goalRepository.countActiveByUserId(USER_ID)).thenReturn(5L);

    assertThatThrownBy(() -> goalService.create(createRequest, "FREE"))
        .isInstanceOf(com.diepau1312.financeTrackerBE.exception.PlanUpgradeRequiredException.class);

    verify(goalRepository, never()).save(any());
  }

  @Test
  @DisplayName("recalculateProgress SAVINGS — auto COMPLETED khi đạt target")
  void recalculate_savings_autoComplete() {
    Goal goal = Goal.builder().id(GOAL_ID).user(mockUser)
        .name("Mua iPhone").type(GoalType.SAVINGS)
        .targetAmount(20_000_000L).currentAmount(0L)
        .status(GoalStatus.ACTIVE).build();

    when(goalRepository.findById(GOAL_ID)).thenReturn(Optional.of(goal));
    // SUM tất cả transactions = đúng bằng target → COMPLETED
    when(transactionRepository.sumAmountByGoalId(GOAL_ID)).thenReturn(20_000_000L);

    goalService.recalculateProgress(GOAL_ID);

    verify(goalRepository).save(argThat(g ->
        g.getStatus() == GoalStatus.COMPLETED &&
            g.getCurrentAmount() == 20_000_000L
    ));
  }

  @Test
  @DisplayName("recalculateProgress SAVINGS — revert ACTIVE khi dưới target")
  void recalculate_savings_revertActive() {
    Goal goal = Goal.builder().id(GOAL_ID).user(mockUser)
        .name("Mua iPhone").type(GoalType.SAVINGS)
        .targetAmount(20_000_000L).currentAmount(20_000_000L)
        .status(GoalStatus.COMPLETED).build();

    when(goalRepository.findById(GOAL_ID)).thenReturn(Optional.of(goal));
    // Sau khi xóa transaction → còn 15tr < 20tr → revert ACTIVE
    when(transactionRepository.sumAmountByGoalId(GOAL_ID)).thenReturn(15_000_000L);

    goalService.recalculateProgress(GOAL_ID);

    verify(goalRepository).save(argThat(g ->
        g.getStatus() == GoalStatus.ACTIVE &&
            g.getCurrentAmount() == 15_000_000L
    ));
  }

  @Test
  @DisplayName("recalculateProgress NORMAL — balance = INCOME - EXPENSE")
  void recalculate_normal_balanceCorrect() {
    Goal goal = Goal.builder().id(GOAL_ID).user(mockUser)
        .name("MoMo").type(GoalType.NORMAL)
        .targetAmount(0L).currentAmount(0L)
        .status(GoalStatus.ACTIVE).build();

    when(goalRepository.findById(GOAL_ID)).thenReturn(Optional.of(goal));
    when(transactionRepository.sumAmountByGoalIdAndType(GOAL_ID, "INCOME")).thenReturn(5_000_000L);
    when(transactionRepository.sumAmountByGoalIdAndType(GOAL_ID, "EXPENSE")).thenReturn(2_000_000L);

    goalService.recalculateProgress(GOAL_ID);

    // balance = 5tr - 2tr = 3tr
    verify(goalRepository).save(argThat(g -> g.getCurrentAmount() == 3_000_000L));
  }

  @Test
  @DisplayName("recalculateProgress DEBT — current = EXPENSE - INCOME (trả nợ giảm current)")
  void recalculate_debt_currentIsExpenseMinusIncome() {
    Goal goal = Goal.builder().id(GOAL_ID).user(mockUser)
        .name("Thẻ tín dụng").type(GoalType.DEBT)
        .targetAmount(0L).currentAmount(0L)
        .creditLimit(10_000_000L)
        .status(GoalStatus.ACTIVE).build();

    when(goalRepository.findById(GOAL_ID)).thenReturn(Optional.of(goal));
    when(transactionRepository.sumAmountByGoalIdAndType(GOAL_ID, "INCOME")).thenReturn(2_000_000L);   // trả nợ
    when(transactionRepository.sumAmountByGoalIdAndType(GOAL_ID, "EXPENSE")).thenReturn(8_000_000L);  // quẹt thẻ

    goalService.recalculateProgress(GOAL_ID);

    // current = 8tr - 2tr = 6tr (số nợ hiện tại)
    verify(goalRepository).save(argThat(g -> g.getCurrentAmount() == 6_000_000L));
  }

  @Test
  @DisplayName("DEBT overLimit = true khi current > creditLimit")
  void debtGoal_overLimit_whenExceedsCreditLimit() {
    Goal goal = Goal.builder().id(GOAL_ID).user(mockUser)
        .name("Thẻ tín dụng").type(GoalType.DEBT)
        .targetAmount(0L).currentAmount(12_000_000L)
        .creditLimit(10_000_000L)
        .status(GoalStatus.ACTIVE).build();

    GoalResponse res = GoalResponse.from(goal);

    assertThat(res.isOverLimit()).isTrue();
    assertThat(res.getRemainingAmount()).isEqualTo(0L);
    assertThat(res.getBalance()).isEqualTo(-2_000_000L); // vượt 2tr
  }

  @Test
  @DisplayName("GetAll trả về goals của user")
  void getAll_returnsUserGoals() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
//    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));
    Goal g1 = Goal.builder().id(UUID.randomUUID()).user(mockUser)
        .name("Goal 1").type(GoalType.SAVINGS)
        .targetAmount(5_000_000L).currentAmount(0L).build();
    Goal g2 = Goal.builder().id(UUID.randomUUID()).user(mockUser)
        .name("Goal 2").type(GoalType.NORMAL)
        .targetAmount(0L).currentAmount(0L).build();

    when(goalRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
        .thenReturn(List.of(g1, g2));

    List<GoalResponse> result = goalService.getAll();
    assertThat(result).hasSize(2);
  }

  @Test
  @DisplayName("Delete thất bại khi goal không thuộc user")
  void delete_notFound_throws() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
    // findByEmail → trả về user để lấy userId
    when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> goalService.delete(GOAL_ID))
        .isInstanceOf(NotFoundException.class);
  }
}