package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.wallet.WalletRequest;
import com.diepau1312.financeTrackerBE.dto.wallet.WalletResponse;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.entity.Wallet;
import com.diepau1312.financeTrackerBE.entity.Wallet.WalletStatus;
import com.diepau1312.financeTrackerBE.entity.Wallet.WalletType;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
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

    // Free user: tối đa 5 ví (bao gồm cả đã đóng)
    private static final int FREE_USER_WALLET_LIMIT = 5;

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

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional
    public WalletResponse create(WalletRequest request) {
        UUID userId = getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy user"));

        // Kiểm tra giới hạn Free user
        String planId = getCurrentUserPlan(userId);
        if ("FREE".equals(planId)) {
            long totalWallets = walletRepository.countAllByUserId(userId); // bao gồm cả đã đóng
            if (totalWallets >= FREE_USER_WALLET_LIMIT) {
                throw new PlanUpgradeRequiredException("PLUS",
                        "Gói Miễn phí chỉ được tạo tối đa " + FREE_USER_WALLET_LIMIT
                                + " nguồn tiền. Nâng cấp Plus để tạo không giới hạn.");
            }
        }

        Wallet wallet = Wallet.builder()
                .user(user)
                .name(request.getName().trim())
                .icon(request.getIcon() != null ? request.getIcon() : "wallet")
                .color(request.getColor() != null ? request.getColor() : "#8b5cf6")
                .type(request.getType())
                .subtype(request.getSubtype())
                .currentAmount(0L)
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
        // type không cho phép thay đổi sau khi tạo

        return WalletResponse.from(walletRepository.save(wallet));
    }

    /** Đóng ví (soft delete — vẫn giữ transactions cũ) */
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

    /** Xóa hoàn toàn — transactions sẽ unset wallet_id */
    @Transactional
    public void delete(UUID id) {
        UUID userId = getCurrentUserId();
        Wallet wallet = walletRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví"));
        walletRepository.delete(wallet);
    }

    // ─── Balance Calculation ──────────────────────────────────────────────────

    /**
     * Tính lại số dư của ví sau mỗi create/update/delete transaction.
     *
     * NORMAL wallet:
     * balance = SUM(INCOME) + SUM(transfer_in) - SUM(EXPENSE) - SUM(transfer_out)
     *
     * DEBT wallet:
     * currentAmount = SUM(EXPENSE) - SUM(INCOME)
     * (transfer không áp dụng cho debt wallet vì debt là khoản nợ/tín dụng)
     */
    @Transactional
    public void recalculateBalance(UUID walletId) {
        if (walletId == null)
            return;
        walletRepository.findById(walletId).ifPresent(wallet -> {
            Long income = transactionRepository.sumAmountByWalletIdAndType(walletId, TransactionType.INCOME);
            Long expense = transactionRepository.sumAmountByWalletIdAndType(walletId, TransactionType.EXPENSE);
            long inc = income != null ? income : 0L;
            long exp = expense != null ? expense : 0L;

            long newBalance;
            if (wallet.getType() == WalletType.NORMAL) {
                // Với NORMAL wallet, tính thêm transfer
                Long transferIn = transactionRepository.sumTransferByWalletIdAndSource(walletId, "transfer_in");
                Long transferOut = transactionRepository.sumTransferByWalletIdAndSource(walletId, "transfer_out");
                long tin = transferIn != null ? transferIn : 0L;
                long tout = transferOut != null ? transferOut : 0L;
                newBalance = inc - exp + tin - tout;
            } else {
                // DEBT: chỉ tính INCOME (thanh toán) và EXPENSE (dùng thẻ/vay)
                newBalance = exp - inc;
            }

            wallet.setCurrentAmount(newBalance);
            walletRepository.save(wallet);
        });
    }

    /**
     * Tạo ví "Tiền mặt" mặc định khi đăng ký.
     * Không check limit vì đây là ví đầu tiên bắt buộc.
     */
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

    /**
     * Trả về tổng số ví của user (bao gồm đã đóng).
     * Dùng cho frontend để hiển thị "X/5 ví" cho Free user.
     */
    @Transactional(readOnly = true)
    public long getTotalWalletCount(UUID userId) {
        return walletRepository.countAllByUserId(userId);
    }
}