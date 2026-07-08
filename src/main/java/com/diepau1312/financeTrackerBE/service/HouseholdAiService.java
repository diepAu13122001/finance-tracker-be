package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.household.RestockPredictionDTO;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.exception.NotFoundException;
import com.diepau1312.financeTrackerBE.repository.HouseholdItemRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HouseholdAiService {

  private final HouseholdItemRepository householdItemRepository;
  private final UserRepository userRepository;

  public RestockPredictionDTO predictRestock(UUID itemId) {
    String userEmail = SecurityUtil.getCurrentUserEmail();
    User user = userRepository.findByEmail(userEmail)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy user"));
    UUID userId = user.getId();

    HouseholdItem currentItem = householdItemRepository.findByIdAndUserId(itemId, userId)
        .orElseThrow(() -> new NotFoundException("Khong tim thay mat hang"));

    // Lấy lịch sử mua cùng tên, bỏ các lần không có ngày mua, sắp mới -> cũ
    List<HouseholdItem> history = householdItemRepository
        .findByUserIdAndNameIgnoreCaseOrderByPurchaseDateDesc(userId, currentItem.getName())
        .stream()
        .filter(item -> item.getPurchaseDate() != null)
        .sorted(Comparator.comparing(HouseholdItem::getPurchaseDate).reversed())
        .toList();

    // Cần tối thiểu 2 lần mua mới tính được khoảng cách
    if (history.size() < 2) {
      return RestockPredictionDTO.builder()
          .itemId(itemId).itemName(currentItem.getName())
          .hasEnoughData(false)
          .explanation("Chua co du lich su mua hang de du doan. Hay ghi lai them vai lan mua san pham nay.")
          .build();
    }

    // Tính tổng số ngày giữa các lần mua liên tiếp + đếm số khoảng
    long totalDays = 0;
    int intervals = 0;
    for (int i = 0; i < history.size() - 1; i++) {
      long days = ChronoUnit.DAYS.between(
          history.get(i + 1).getPurchaseDate(), history.get(i).getPurchaseDate());
      if (days > 0) {
        totalDays += days;
        intervals++;
      }
    }

    if (intervals == 0) {
      return RestockPredictionDTO.builder()
          .itemId(itemId).itemName(currentItem.getName())
          .hasEnoughData(false)
          .explanation("Chua du thong tin ngay mua de tinh chu ky dung san pham.")
          .build();
    }

    // Khoảng cách trung bình (tối thiểu 1 ngày) -> dự đoán ngày dùng hết
    long averageDays = Math.max(1, Math.round(totalDays / (double) intervals));
    LocalDate lastPurchase = history.get(0).getPurchaseDate();
    LocalDate predictedRunOut = lastPurchase.plusDays(averageDays);
    long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), predictedRunOut);
    int estimatedDaysLeft = (int) Math.max(0, daysLeft);

    String explanation = String.format(
        "Dua vao %d lan mua gan day, ban thuong mua lai %s sau khoang %d ngay. " +
            "Uoc tinh con khoang %d ngay nua can mua them.",
        history.size(), currentItem.getName(), averageDays, estimatedDaysLeft);

    return RestockPredictionDTO.builder()
        .itemId(itemId).itemName(currentItem.getName())
        .predictedRunOutDate(predictedRunOut)
        .estimatedDaysLeft(estimatedDaysLeft)
        .explanation(explanation)
        .hasEnoughData(true)
        .build();
  }
}