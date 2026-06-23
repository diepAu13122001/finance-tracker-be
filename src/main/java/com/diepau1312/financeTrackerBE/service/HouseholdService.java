package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.household.HouseholdItemRequest;
import com.diepau1312.financeTrackerBE.dto.household.HouseholdItemResponse;
import com.diepau1312.financeTrackerBE.dto.household.ReviewRequest;
import com.diepau1312.financeTrackerBE.dto.transaction.TransactionRequest;
import com.diepau1312.financeTrackerBE.entity.Category;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemCategory;
import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemStatus;
import com.diepau1312.financeTrackerBE.entity.ItemReview;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import com.diepau1312.financeTrackerBE.entity.User;
import com.diepau1312.financeTrackerBE.entity.Wallet;
import com.diepau1312.financeTrackerBE.entity.Wallet.WalletStatus;
import com.diepau1312.financeTrackerBE.exception.AuthException;
import com.diepau1312.financeTrackerBE.exception.NotFoundException;
import com.diepau1312.financeTrackerBE.repository.CategoryRepository;
import com.diepau1312.financeTrackerBE.repository.HouseholdItemRepository;
import com.diepau1312.financeTrackerBE.repository.ItemReviewRepository;
import com.diepau1312.financeTrackerBE.repository.UserRepository;
import com.diepau1312.financeTrackerBE.repository.WalletRepository;
import com.diepau1312.financeTrackerBE.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HouseholdService {

  private final HouseholdItemRepository householdRepo;
  private final ItemReviewRepository reviewRepo;
  private final UserRepository userRepo;
  private final CategoryRepository categoryRepository;
  private final WalletRepository walletRepository;
  private final TransactionService transactionService;

  private User getCurrentUser() {
    return userRepo.findByEmail(SecurityUtil.getCurrentUserEmail())
        .orElseThrow(() -> new NotFoundException("User khong ton tai"));
  }

  @Transactional(readOnly = true)
  public Page<HouseholdItemResponse> getAll(int page, int size, ItemStatus status, ItemCategory category) {
    UUID userId = getCurrentUser().getId();
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

    Page<HouseholdItem> result;
    if (status != null) {
      result = householdRepo.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
    } else if (category != null) {
      result = householdRepo.findByUserIdAndCategoryOrderByCreatedAtDesc(userId, category, pageable);
    } else {
      result = householdRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    return result.map(HouseholdItemResponse::from);
  }

  @Transactional(readOnly = true)
  public HouseholdItemResponse getById(UUID id) {
    UUID userId = getCurrentUser().getId();
    HouseholdItem item = householdRepo.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Khong tim thay mat hang"));
    return HouseholdItemResponse.from(item);
  }

  @Transactional
  public HouseholdItemResponse create(HouseholdItemRequest request) {
    User user = getCurrentUser();
    HouseholdItem item = HouseholdItem.builder()
        .user(user)
        .name(request.getName().trim())
        .brand(blankToNull(request.getBrand()))
        .category(request.getCategory())
        .price(request.getPrice())
        .purchaseDate(request.getPurchaseDate())
        .expiryDate(request.getExpiryDate())
        .quantity(request.getQuantity())
        .unit(blankToNull(request.getUnit()))
        .notifyBeforeDays(request.getNotifyBeforeDays() != null ? request.getNotifyBeforeDays() : 7)
        .notes(blankToNull(request.getNotes()))
        .build();

    HouseholdItem saved = householdRepo.save(item);

    if (request.isLinkToTransaction() && request.getPrice() != null && request.getPrice() > 0) {
      createLinkedExpense(user.getId(), request);
    }

    return HouseholdItemResponse.from(saved);
  }

  @Transactional
  public HouseholdItemResponse update(UUID id, HouseholdItemRequest request) {
    UUID userId = getCurrentUser().getId();
    HouseholdItem item = householdRepo.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Khong tim thay mat hang"));

    item.setName(request.getName().trim());
    item.setBrand(blankToNull(request.getBrand()));
    item.setCategory(request.getCategory());
    item.setPrice(request.getPrice());
    item.setPurchaseDate(request.getPurchaseDate());
    item.setExpiryDate(request.getExpiryDate());
    item.setQuantity(request.getQuantity());
    item.setUnit(blankToNull(request.getUnit()));
    if (request.getNotifyBeforeDays() != null) {
      item.setNotifyBeforeDays(request.getNotifyBeforeDays());
    }
    item.setNotes(blankToNull(request.getNotes()));
    return HouseholdItemResponse.from(householdRepo.save(item));
  }

  @Transactional
  public HouseholdItemResponse updateStatus(UUID id, ItemStatus newStatus) {
    UUID userId = getCurrentUser().getId();
    HouseholdItem item = householdRepo.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Khong tim thay mat hang"));
    item.setStatus(newStatus);
    return HouseholdItemResponse.from(householdRepo.save(item));
  }

  @Transactional
  public void delete(UUID id) {
    UUID userId = getCurrentUser().getId();
    HouseholdItem item = householdRepo.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new NotFoundException("Khong tim thay mat hang"));
    householdRepo.delete(item);
  }

  @Transactional
  public void addReview(UUID itemId, ReviewRequest request) {
    User user = getCurrentUser();
    HouseholdItem item = householdRepo.findByIdAndUserId(itemId, user.getId())
        .orElseThrow(() -> new NotFoundException("Khong tim thay mat hang"));

    ItemReview review = reviewRepo.findByItemIdAndUserId(itemId, user.getId())
        .orElse(ItemReview.builder().item(item).user(user).build());

    review.setRating(request.getRating());
    review.setReviewText(request.getReviewText());
    review.setWouldBuyAgain(request.getWouldBuyAgain());
    reviewRepo.save(review);
  }

  @Transactional
  public HouseholdItemResponse saveAiCategory(UUID itemId, String aiCategory) {
    UUID userId = getCurrentUser().getId();
    HouseholdItem item = householdRepo.findByIdAndUserId(itemId, userId)
        .orElseThrow(() -> new NotFoundException("Khong tim thay mat hang"));
    item.setAiCategory(aiCategory);
    return HouseholdItemResponse.from(householdRepo.save(item));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> getTopRated(String category, int limit) {
    UUID userId = getCurrentUser().getId();
    List<Object[]> rows = reviewRepo.findTopRatedItems(userId, category, limit);

    return rows.stream().map(r -> {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("itemId", r[0]);
      map.put("name", r[1]);
      map.put("brand", r[2]);
      map.put("category", r[3]);
      map.put("avgRating", r[4]);
      map.put("reviewCount", r[5]);
      map.put("wouldBuyPct", r[7]);
      return map;
    }).toList();
  }

  private void createLinkedExpense(UUID userId, HouseholdItemRequest request) {
    TransactionRequest txRequest = new TransactionRequest();
    txRequest.setType(TransactionType.EXPENSE);
    txRequest.setAmount(request.getPrice());
    txRequest.setCurrency("VND");
    txRequest.setCategoryId(findShoppingCategoryId(userId));
    txRequest.setWalletId(getDefaultWalletId(userId));
    txRequest.setTransactionDate(request.getPurchaseDate() != null ? request.getPurchaseDate() : LocalDate.now());
    txRequest.setNote("Mua " + request.getName().trim()
        + (request.getBrand() != null && !request.getBrand().isBlank() ? " (" + request.getBrand().trim() + ")" : ""));
    transactionService.create(txRequest);
  }

  private UUID findShoppingCategoryId(UUID userId) {
    return categoryRepository.findFirstByUserIdAndTypeAndNameContainingIgnoreCaseOrderByNameAsc(
            userId, TransactionType.EXPENSE, "mua")
        .map(Category::getId)
        .orElse(null);
  }

  private UUID getDefaultWalletId(UUID userId) {
    return walletRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, WalletStatus.ACTIVE)
        .map(Wallet::getId)
        .orElseThrow(() -> new AuthException("Ban can co it nhat mot vi de ghi giao dich"));
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
