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
import com.diepau1312.financeTrackerBE.repository.TransactionRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
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

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    private UUID getCurrentUserId() {
        return userRepository.findByEmail(SecurityUtil.getCurrentUserEmail())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy user"))
                .getId();
    }

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

    @Transactional
    public WalletResponse create(WalletRequest request) {
        UUID userId = getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy user"));

        Wallet wallet = Wallet.builder()
                .user(user)
                .name(request.getName().trim())
                .icon(request.getIcon() != null ? request.getIcon() : "wallet")
                .color(request.getColor() != null ? request.getColor() : "#8b5cf6")
                .type(request.getType())
                .subtype(request.getSubtype())
                .currentAmount(0L)
                .status(WalletStatus.ACTIVE)
                // CREDIT_CARD
                .creditLimit(request.getCreditLimit())
                .billingDate(request.getBillingDate())
                // INSTALLMENT
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
        if (request.getIcon() != null) wallet.setIcon(request.getIcon());
        if (request.getColor() != null) wallet.setColor(request.getColor());
        wallet.setType(request.getType());
        wallet.setSubtype(request.getSubtype());
        wallet.setCreditLimit(request.getCreditLimit());
        wallet.setBillingDate(request.getBillingDate());
        wallet.setNumberOfPeriods(request.getNumberOfPeriods());
        wallet.setMonthlyPayment(request.getMonthlyPayment());
        wallet.setInitialAmount(request.getInitialAmount());

        return WalletResponse.from(wallet);
    }

    @Transactional
    public WalletResponse cancel(UUID id) {
        UUID userId = getCurrentUserId();
        Wallet wallet = walletRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví"));

        if (wallet.getStatus() == WalletStatus.CANCELLED) {
            throw new AuthException("Ví đã bị huỷ");
        }
        wallet.setStatus(WalletStatus.CANCELLED);
        return WalletResponse.from(wallet);
    }

    @Transactional
    public void delete(UUID id) {
        UUID userId = getCurrentUserId();
        Wallet wallet = walletRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví"));
        walletRepository.delete(wallet);
    }

    /**
     * Được gọi từ TransactionService sau mỗi create/update/delete.
     *
     * NORMAL wallet: balance = SUM(INCOME) - SUM(EXPENSE)
     * DEBT wallet:   currentAmount = SUM(EXPENSE) - SUM(INCOME) = số nợ hiện tại
     */
    @Transactional
    public void recalculateBalance(UUID walletId) {
        if (walletId == null) return;
        walletRepository.findById(walletId).ifPresent(wallet -> {
            Long income = transactionRepository.sumAmountByWalletIdAndType(walletId, TransactionType.INCOME);
            Long expense = transactionRepository.sumAmountByWalletIdAndType(walletId, TransactionType.EXPENSE);
            long inc = income != null ? income : 0L;
            long exp = expense != null ? expense : 0L;

            long newBalance = (wallet.getType() == WalletType.NORMAL)
                    ? inc - exp   // NORMAL: tiền có trong ví
                    : exp - inc;  // DEBT: tiền đang nợ

            wallet.setCurrentAmount(newBalance);
            walletRepository.save(wallet);
        });
    }

    /**
     * Tạo ví mặc định "Tiền mặt" khi đăng ký tài khoản.
     * Gọi từ AuthService.register()
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
}