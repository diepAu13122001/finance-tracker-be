package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.payment.PaymentLinkResponse;
import com.diepau1312.financeTrackerBE.entity.*;
import com.diepau1312.financeTrackerBE.exception.AuthException;
import com.diepau1312.financeTrackerBE.exception.NotFoundException;
import com.diepau1312.financeTrackerBE.repository.*;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

  private final PayOS payOS;
  private final UserRepository userRepository;
  private final PaymentHistoryRepository paymentRepository;
  private final SubscriptionPlanRepository planRepository;
  private final UserSubscriptionRepository subscriptionRepository;

  @Value("${payos.return-url}")
  private String returnUrl;

  @Value("${payos.cancel-url}")
  private String cancelUrl;

  /**
   * Tạo link thanh toán PayOS cho 1 plan cụ thể.
   * <p>
   * Flow:
   * 1. Tra giá plan từ subscription_plans
   * 2. Tạo record PaymentHistory với status PENDING
   * 3. Gọi PayOS API tạo payment link
   * 4. Lưu paymentLinkId vào DB để verify webhook
   * 5. Trả về checkoutUrl cho frontend redirect
   */
  @Transactional
  public PaymentLinkResponse createPaymentLink(String planId) {

    User user = userRepository.findByEmail(SecurityUtil.getCurrentUserEmail())
        .orElseThrow(() -> new NotFoundException("Không tìm thấy user"));

    SubscriptionPlan plan = planRepository.findById(planId)
        .orElseThrow(() -> new NotFoundException("Plan không hợp lệ"));

    if (plan.getPriceVnd() == null || plan.getPriceVnd() <= 0) {
      throw new AuthException("Plan miễn phí, không cần thanh toán");
    }

    long orderCode = System.currentTimeMillis();
    CreatePaymentLinkResponse response = null;

    try {
      String description = "FT-" + orderCode;

      PaymentLinkItem item = PaymentLinkItem.builder()
          .name(("Upgrade " + planId).substring(0, Math.min(("Upgrade " + planId).length(), 25)))
          .quantity(1)
          .price(plan.getPriceVnd())
          .build();

      CreatePaymentLinkRequest  paymentData = CreatePaymentLinkRequest.builder()
          .orderCode(orderCode)
          .amount(plan.getPriceVnd())
          .description(description)
          .item(item)
          .returnUrl(returnUrl + "?orderCode=" + orderCode)
          .cancelUrl(cancelUrl + "?orderCode=" + orderCode)
          .build();

      response = payOS.paymentRequests().create(paymentData);

      // Chỉ lưu DB khi PayOS trả về thành công và signature hợp lệ
      PaymentHistory payment = PaymentHistory.builder()
          .user(user)
          .planId(planId)
          .amountVnd(plan.getPriceVnd())
          .status("PENDING")
          .payosOrderId(String.valueOf(orderCode))
          .payosPaymentLinkId(response.getPaymentLinkId())
          .build();

      paymentRepository.save(payment);

      log.info("Created PayOS payment link successfully: orderCode={}, user={}", orderCode, user.getEmail());

      return PaymentLinkResponse.builder()
          .checkoutUrl(response.getCheckoutUrl())
          .qrCode(response.getQrCode())
          .orderCode(String.valueOf(orderCode))
          .build();

    } catch (Exception e) {
      log.error("PayOS create payment link failed: orderCode={}, user={}", orderCode, user.getEmail(), e);

      // ✅ Nếu PayOS đã tạo link nhưng verify thất bại → hủy link để tránh rác
      if (response != null) {
        cancelPayOSLinkSilently(orderCode);
      } else {
        // response null nghĩa là PayOS vẫn có thể đã tạo (exception xảy ra khi parse/verify)
        // Dùng orderCode để cancel phòng ngừa
        cancelPayOSLinkSilently(orderCode);
      }

      throw new RuntimeException("Không thể tạo link thanh toán: " + e.getMessage());
    }
  }

  /**
   * Hủy PayOS link không throw exception — chỉ log lỗi
   * Tránh làm mất error gốc khi đang xử lý catch
   */
  private void cancelPayOSLinkSilently(long orderCode) {
    try {
      payOS.paymentRequests().cancel(orderCode, "Auto-cancel do lỗi tạo link");
      log.warn("Auto-cancelled PayOS link: orderCode={}", orderCode);
    } catch (Exception cancelEx) {
      log.error("Failed to auto-cancel PayOS link: orderCode={}, reason={}", orderCode, cancelEx.getMessage());
    }
  }

  /**
   * Kích hoạt subscription sau khi thanh toán thành công.
   * Gọi từ WebhookController khi PayOS confirm payment.
   * <p>
   * Idempotent: gọi nhiều lần với cùng orderCode chỉ activate 1 lần.
   */
  @Transactional
  public void activateSubscription(String orderCode) {
    PaymentHistory payment = paymentRepository.findByPayosOrderId(orderCode).orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch"));

    // Tránh activate 2 lần (PayOS có thể retry webhook)
    if ("PAID".equals(payment.getStatus())) {
      log.info("Payment {} đã active trước đó, skip", orderCode);
      return;
    }

    // Đánh dấu đã thanh toán
    payment.setStatus("PAID");
    payment.setPaidAt(LocalDateTime.now());

    // Lấy hoặc tạo subscription
    User user = payment.getUser();
    SubscriptionPlan newPlan = planRepository.findById(payment.getPlanId()).orElseThrow(() -> new NotFoundException("Plan không tồn tại"));

    UserSubscription sub = subscriptionRepository.findByUserId(user.getId()).orElseGet(() -> UserSubscription.builder().user(user).startedAt(LocalDateTime.now()).build());

    // Plan mới = upgrade hiện tại. Cộng dồn 1 năm vào expiresAt
    sub.setPlan(newPlan);
    sub.setStatus("ACTIVE");
    sub.setPaymentRef(payment.getPayosOrderId());

    // Nếu user đã có sub PLUS/PREMIUM còn hạn → cộng dồn 1 năm
    // Nếu chưa có hoặc đã hết → tính từ hôm nay
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime baseDate = (sub.getExpiresAt() != null && sub.getExpiresAt().isAfter(now)) ? sub.getExpiresAt() : now;
    sub.setExpiresAt(baseDate.plusYears(1));

    subscriptionRepository.save(sub);
    log.info("Activated {} for user {} until {}", newPlan.getId(), user.getEmail(), sub.getExpiresAt());
  }

  /**
   * Đánh dấu payment thất bại/hủy.
   */
  @Transactional
  public void markPaymentFailed(String orderCode, String reason) {
    paymentRepository.findByPayosOrderId(orderCode).ifPresent(p -> {
      if (!"PAID".equals(p.getStatus())) {  // không override nếu đã PAID
        p.setStatus("CANCELLED".equals(reason) ? "CANCELLED" : "FAILED");
        paymentRepository.save(p);
        log.info("Payment {} marked as {}", orderCode, p.getStatus());
      }
    });
  }
}