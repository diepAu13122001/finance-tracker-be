package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.wallet.WalletRequest;
import com.diepau1312.financeTrackerBE.dto.wallet.WalletResponse;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.entity.Wallet;
import com.diepau1312.financeTrackerBE.entity.Wallet.WalletStatus;
import com.diepau1312.financeTrackerBE.entity.Wallet.WalletType;
import com.diepau1312.financeTrackerBE.exception.AuthException;
import com.diepau1312.financeTrackerBE.exception.NotFoundException;
import com.diepau1312.financeTrackerBE.exception.PlanUpgradeRequiredException;
import com.diepau1312.financeTrackerBE.repository.TransactionRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.repository.UserSubscriptionRepository;
import com.diepau1312.financeTrackerBE.repository.WalletRepository;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    /**
     * Free user: tối đa 5 ví ACTIVE cùng lúc.
     * Ví đã đóng (CANCELLED) KHÔNG tính vào giới hạn này
     * → đóng ví giải phóng 1 slot, cho phép mở lại hoặc tạo ví mới.
     */
    private static final int FREE_USER_ACTIVE_WALLET_LIMIT = 5;

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final UserSubscriptionRepository subscriptionRepository;

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private UUID getCurrentUserId() {
        return userRepository.findByEmail(SecurityUtil.getCurrentUserEmail())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy user"))
                .getId();
    }

    private String getCurrentUserPlan(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
                .map(sub -> sub.getPlanId())
                .orElse("FREE");
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WalletResponse> getAll() {
        UUID userId = getCurrentUserId();
        return walletRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(WalletResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> getActive() {
        UUID userId = getCurrentUserId();
        return walletRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, WalletStatus.ACTIVE)
                .stream().map(WalletResponse::from).toList();
    }

    /** Số ví ACTIVE — dùng để kiểm tra giới hạn Free user */
    @Transactional(readOnly = true)
    public long getActiveWalletCount(UUID userId) {
        return walletRepository.countActiveByUserId(userId);
    }

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional
    public WalletResponse create(WalletRequest request) {
        UUID userId = getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy user"));

        // Giới hạn Free user: chỉ tính ví ACTIVE (không tính CANCELLED)
        String planId = getCurrentUserPlan(userId);
        if ("FREE".equals(planId)) {
            long activeCount = walletRepository.countActiveByUserId(userId);
            if (activeCount >= FREE_USER_ACTIVE_WALLET_LIMIT) {
                throw new PlanUpgradeRequiredException("PLUS",
                        "Gói Miễn phí chỉ được có tối đa " + FREE_USER_ACTIVE_WALLET_LIMIT
                                + " nguồn tiền đang hoạt động. Đóng ví cũ hoặc nâng cấp Plus.");
            }
        }

        // Ví trả góp: khoản vay ban đầu là nợ ngay khi tạo
        long initialCurrentAmount = 0L;
        if (request.getType() == WalletType.DEBT
                && request.getSubtype() == Wallet.WalletSubtype.INSTALLMENT
                && request.getInitialAmount() != null) {
            initialCurrentAmount = request.getInitialAmount();
        }

        Wallet wallet = Wallet.builder()
                .user(user)
                .name(request.getName().trim())
                .icon(request.getIcon() != null ? request.getIcon() : "wallet")
                .color(request.getColor() != null ? request.getColor() : "#8b5cf6")
                .type(request.getType())
                .subtype(request.getSubtype())
                .currentAmount(initialCurrentAmount)
                .status(WalletStatus.ACTIVE)
                .creditLimit(request.getCreditLimit())
                .billingDate(request.getBillingDate())
                .numberOfPeriods(request.getNumberOfPeriods())
                .monthlyPayment(request.getMonthlyPayment())
                .initialAmount(request.getInitialAmount())
                .build();

        return WalletResponse.from(walletRepository.save(wallet));
    }

    @Transactional
    public WalletResponse update(UUID id, WalletRequest request) {
        UUID userId = getCurrentUserId();
        Wallet wallet = walletRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví"));

        wallet.setName(request.getName().trim());
        if (request.getIcon() != null)
            wallet.setIcon(request.getIcon());
        if (request.getColor() != null)
            wallet.setColor(request.getColor());
        wallet.setSubtype(request.getSubtype());
        wallet.setCreditLimit(request.getCreditLimit());
        wallet.setBillingDate(request.getBillingDate());
        wallet.setNumberOfPeriods(request.getNumberOfPeriods());
        wallet.setMonthlyPayment(request.getMonthlyPayment());
        wallet.setInitialAmount(request.getInitialAmount());

        return WalletResponse.from(walletRepository.save(wallet));
    }

    /**
     * Đóng ví (soft delete).
     * Giải phóng 1 slot ACTIVE cho Free user → cho phép tạo ví mới hoặc reopen.
     */
    @Transactional
    public WalletResponse cancel(UUID id) {
        UUID userId = getCurrentUserId();
        Wallet wallet = walletRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví"));

        if (wallet.getStatus() == WalletStatus.CANCELLED) {
            throw new AuthException("Ví này đã được đóng trước đó");
        }
        wallet.setStatus(WalletStatus.CANCELLED);
        return WalletResponse.from(walletRepository.save(wallet));
    }

    /**
     * Mở lại ví đã đóng.
     * Free user: kiểm tra còn slot ACTIVE không.
     * Plus user: không giới hạn.
     */
    @Transactional
    public WalletResponse reopen(UUID id) {
        UUID userId = getCurrentUserId();
        Wallet wallet = walletRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví"));

        if (wallet.getStatus() != WalletStatus.CANCELLED) {
            throw new AuthException("Ví này đang hoạt động, không cần mở lại");
        }

        // Free user: kiểm tra slot
        String planId = getCurrentUserPlan(userId);
        if ("FREE".equals(planId)) {
            long activeCount = walletRepository.countActiveByUserId(userId);
            if (activeCount >= FREE_USER_ACTIVE_WALLET_LIMIT) {
                throw new PlanUpgradeRequiredException("PLUS",
                        "Đã có " + FREE_USER_ACTIVE_WALLET_LIMIT + " ví đang hoạt động. "
                                + "Đóng 1 ví khác trước hoặc nâng cấp Plus.");
            }
        }

        wallet.setStatus(WalletStatus.ACTIVE);
        return WalletResponse.from(walletRepository.save(wallet));
    }

    @Transactional
    public void delete(UUID id) {
        UUID userId = getCurrentUserId();
        Wallet wallet = walletRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví"));
        walletRepository.delete(wallet);
    }

    // ─── Balance Recalculation ────────────────────────────────────────────────

    /**
     * Tính lại số dư wallet sau mỗi thay đổi transaction.
     *
     * NORMAL wallet:
     * balance = SUM(INCOME) + SUM(transfer_in) − SUM(EXPENSE) − SUM(transfer_out)
     *
     * DEBT (CREDIT_CARD) wallet:
     * currentAmount = SUM(EXPENSE) − SUM(INCOME)
     * (EXPENSE = dùng thẻ, INCOME = trả thẻ)
     *
     * DEBT (INSTALLMENT) wallet:
     * currentAmount = initialAmount − SUM(INCOME)
     * (initialAmount = khoản vay ban đầu, INCOME = các kỳ đã trả)
     * Kết quả là số nợ còn lại.
     */
    @Transactional
    public void recalculateBalance(UUID walletId) {
        if (walletId == null)
            return;
        walletRepository.findById(walletId).ifPresent(wallet -> {
            long newBalance;

            if (wallet.getType() == WalletType.NORMAL) {
                Long income = transactionRepository.sumAmountByWalletIdAndType(walletId, TransactionType.INCOME);
                Long expense = transactionRepository.sumAmountByWalletIdAndType(walletId, TransactionType.EXPENSE);
                Long transferIn = transactionRepository.sumTransferByWalletIdAndSource(walletId, "transfer_in");
                Long transferOut = transactionRepository.sumTransferByWalletIdAndSource(walletId, "transfer_out");
                long inc = income != null ? income : 0L;
                long exp = expense != null ? expense : 0L;
                long tin = transferIn != null ? transferIn : 0L;
                long tout = transferOut != null ? transferOut : 0L;
                newBalance = inc - exp + tin - tout;

            } else if (wallet.getSubtype() == Wallet.WalletSubtype.INSTALLMENT) {
                // INSTALLMENT: còn lại = khoản vay ban đầu − đã trả
                long initial = wallet.getInitialAmount() != null ? wallet.getInitialAmount() : 0L;
                Long paid = transactionRepository.sumAmountByWalletIdAndType(walletId, TransactionType.INCOME);
                long paidAmount = paid != null ? paid : 0L;
                newBalance = Math.max(0L, initial - paidAmount); // không âm

            } else {
                // DEBT CREDIT_CARD: số nợ = đã dùng − đã trả
                Long income = transactionRepository.sumAmountByWalletIdAndType(walletId, TransactionType.INCOME);
                Long expense = transactionRepository.sumAmountByWalletIdAndType(walletId, TransactionType.EXPENSE);
                long inc = income != null ? income : 0L;
                long exp = expense != null ? expense : 0L;
                newBalance = Math.max(0L, exp - inc);
            }

            wallet.setCurrentAmount(newBalance);
            walletRepository.save(wallet);
        });
    }

    @Transactional
    public void createDefaultWallet(User user) {
        Wallet defaultWallet = Wallet.builder()
                .user(user)
                .name("Tiền mặt")
                .icon("wallet")
                .color("#8b5cf6")
                .type(WalletType.NORMAL)
                .currentAmount(0L)
                .status(WalletStatus.ACTIVE)
                .build();
        walletRepository.save(defaultWallet);
    }

    /** @deprecated Dùng getActiveWalletCount thay thế */
    @Transactional(readOnly = true)
    public long getTotalWalletCount(UUID userId) {
        return walletRepository.countAllByUserId(userId);
    }
}