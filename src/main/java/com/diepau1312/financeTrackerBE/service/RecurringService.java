package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.recurring.*;
import com.diepau1312.financeTrackerBE.dto.transaction.TransactionRequest;
import com.diepau1312.financeTrackerBE.dto.transaction.TransactionResponse;
import com.diepau1312.financeTrackerBE.entity.*;
import com.diepau1312.financeTrackerBE.exception.ResourceNotFoundException;
import com.diepau1312.financeTrackerBE.repository.*;
import com.diepau1312.financeTrackerBE.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecurringService {

  private final RecurringTransactionRepository recurringRepo;
  private final UserRepository userRepo;
  private final CategoryRepository categoryRepo;
  private final WalletRepository walletRepo;
  private final TransactionService transactionService;

  // ── CRUD ────────────────────────────────────────────────────────────

  public List<RecurringTransactionResponse> findAll() {
    UUID userId = currentUserId();
    return recurringRepo.findByUserIdOrderByNextExecutionDateAsc(userId)
        .stream().map(RecurringTransactionResponse::from).toList();
  }

  @Transactional
  public RecurringTransactionResponse create(CreateRecurringRequest req) {
    UUID userId = currentUserId();
    User user = userRepo.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    Category category = req.getCategoryId() != null
        ? categoryRepo.findByIdAndUserId(req.getCategoryId(), userId).orElse(null)
        : null;

    Wallet wallet = req.getWalletId() != null
        ? walletRepo.findByIdAndUserId(req.getWalletId(), userId).orElse(null)
        : null;

    LocalDate startDate = req.getStartDate() != null ? req.getStartDate() : LocalDate.now();

    RecurringTransaction r = RecurringTransaction.builder()
        .user(user)
        .type(req.getType())
        .amount(req.getAmount())
        .note(req.getNote())
        .category(category)
        .wallet(wallet)
        .frequency(req.getFrequency())
        .dayOfMonth(req.getDayOfMonth())
        .dayOfWeek(req.getDayOfWeek())
        .nextExecutionDate(startDate)
        .isActive(true)
        .build();

    return RecurringTransactionResponse.from(recurringRepo.save(r));
  }

  @Transactional
  public RecurringTransactionResponse update(UUID id, UpdateRecurringRequest req) {
    UUID userId = currentUserId();
    RecurringTransaction r = recurringRepo.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Recurring transaction not found"));

    if (req.getAmount()   != null) r.setAmount(req.getAmount());
    if (req.getNote()     != null) r.setNote(req.getNote());
    if (req.getIsActive() != null) r.setIsActive(req.getIsActive());

    if (req.getCategoryId() != null) {
      categoryRepo.findByIdAndUserId(req.getCategoryId(), userId)
          .ifPresent(r::setCategory);
    }
    if (req.getWalletId() != null) {
      walletRepo.findByIdAndUserId(req.getWalletId(), userId)
          .ifPresent(r::setWallet);
    }

    return RecurringTransactionResponse.from(recurringRepo.save(r));
  }

  @Transactional
  public void delete(UUID id) {
    UUID userId = currentUserId();
    RecurringTransaction r = recurringRepo.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Recurring transaction not found"));
    recurringRepo.delete(r);
  }

  /**
   * Execute: tạo transaction thực từ template và cập nhật nextExecutionDate.
   *
   * Design choice: dùng TransactionService.create() thay vì tự save →
   *   đảm bảo đi qua tất cả validation (giới hạn Free, wallet check...)
   */
  @Transactional
  public TransactionResponse execute(UUID id) {
    UUID userId = currentUserId();
    RecurringTransaction r = recurringRepo.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Recurring transaction not found"));

    // Tạo transaction thực từ template
    TransactionRequest txReq = new TransactionRequest();
    txReq.setType(r.getType());
    txReq.setAmount(r.getAmount());
    txReq.setNote(r.getNote());
    txReq.setTransactionDate(LocalDate.now());
    txReq.setCategoryId(r.getCategory() != null ? r.getCategory().getId() : null);
    txReq.setWalletId(r.getWallet() != null ? r.getWallet().getId() : null);

    TransactionResponse result = transactionService.create(txReq);

    // Cập nhật nextExecutionDate theo frequency
    r.setNextExecutionDate(nextDate(r.getNextExecutionDate(), r.getFrequency()));
    recurringRepo.save(r);

    return result;
  }

  // ── Helpers ───────────────────────────────────────────────────────────

  /**
   * Tính ngày thực hiện tiếp theo.
   * Dùng plus() của LocalDate → tự động handle month overflow (e.g. Jan 31 + 1 month = Feb 28)
   */
  private LocalDate nextDate(LocalDate current, RecurringTransaction.Frequency freq) {
    return switch (freq) {
      case DAILY   -> current.plusDays(1);
      case WEEKLY  -> current.plusWeeks(1);
      case MONTHLY -> current.plusMonths(1);
      case YEARLY  -> current.plusYears(1);
    };
  }

  private UUID currentUserId() {
    return (UUID) SecurityContextHolder.getContext()
        .getAuthentication().getDetails();
  }
}
