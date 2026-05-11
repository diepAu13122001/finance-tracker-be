package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.goal.GoalRequest;
import com.diepau1312.financeTrackerBE.dto.goal.GoalResponse;
import com.diepau1312.financeTrackerBE.entity.Goal;
import com.diepau1312.financeTrackerBE.entity.Goal.GoalStatus;
import com.diepau1312.financeTrackerBE.entity.Goal.GoalType;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.exception.NotFoundException;
import com.diepau1312.financeTrackerBE.repository.GoalRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
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
    Mockito.reset(userRepository, goalRepository);

  }

  @AfterEach
  void tearDown() {
    if (securityMock != null) {
      securityMock.close();
    }
  }

  @Test
  @DisplayName("Create goal thành công")
  void create_valid_returnsResponse() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));
    Goal saved = Goal.builder().id(GOAL_ID).user(mockUser)
        .name("Mua iPhone").type(GoalType.SAVINGS)
        .targetAmount(20_000_000L).currentAmount(0L)
        .status(GoalStatus.ACTIVE).build();
    when(goalRepository.save(any())).thenReturn(saved);

    GoalResponse res = goalService.create(createRequest);

    assertThat(res.getName()).isEqualTo("Mua iPhone");
    assertThat(res.getProgressPercent()).isEqualTo(0.0);
    assertThat(res.getStatus()).isEqualTo(GoalStatus.ACTIVE);
  }

  @Test
  @DisplayName("recalculateProgress tự động COMPLETED khi đạt target")
  void recalculate_autoComplete_whenReachedTarget() {
    Goal goal = Goal.builder().id(GOAL_ID).user(mockUser)
        .name("Mua iPhone").type(GoalType.SAVINGS)
        .targetAmount(20_000_000L).currentAmount(0L)
        .status(GoalStatus.ACTIVE).build();
    when(goalRepository.findById(GOAL_ID)).thenReturn(Optional.of(goal));

    goalService.recalculateProgress(GOAL_ID, 20_000_000L);

    verify(goalRepository).save(argThat(g -> g.getStatus() == GoalStatus.COMPLETED &&
        g.getCurrentAmount() == 20_000_000L));
  }

  @Test
  @DisplayName("recalculateProgress revert ACTIVE khi dưới target sau khi xóa transaction")
  void recalculate_revertActive_whenBelowTarget() {
    Goal goal = Goal.builder().id(GOAL_ID).user(mockUser)
        .name("Mua iPhone").type(GoalType.SAVINGS)
        .targetAmount(20_000_000L).currentAmount(20_000_000L)
        .status(GoalStatus.COMPLETED).build();
    when(goalRepository.findById(GOAL_ID)).thenReturn(Optional.of(goal));

    goalService.recalculateProgress(GOAL_ID, 15_000_000L);

    verify(goalRepository).save(argThat(g -> g.getStatus() == GoalStatus.ACTIVE &&
        g.getCurrentAmount() == 15_000_000L));
  }

  @Test
  @DisplayName("DEBT overLimit = true khi current > target")
  void debtGoal_overLimit_whenExceedsTarget() {
    Goal goal = Goal.builder().id(GOAL_ID).user(mockUser)
        .name("Trả nợ thẻ").type(GoalType.DEBT)
        .targetAmount(10_000_000L).currentAmount(12_000_000L)
        .status(GoalStatus.ACTIVE).build();

    GoalResponse res = GoalResponse.from(goal);

    assertThat(res.isOverLimit()).isTrue();
    assertThat(res.getRemainingAmount()).isEqualTo(0L);
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
        .name("Goal 2").type(GoalType.DEBT)
        .targetAmount(10_000_000L).currentAmount(0L).build();

    when(goalRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
        .thenReturn(List.of(g1, g2));

    List<GoalResponse> result = goalService.getAll();

    assertThat(result).hasSize(2);
  }

  @Test
  @DisplayName("Delete thất bại khi goal không thuộc user")
  void delete_notFound_throws() {
    assertThatThrownBy(() -> goalService.delete(GOAL_ID))
        .isInstanceOf(NotFoundException.class);
  }
}