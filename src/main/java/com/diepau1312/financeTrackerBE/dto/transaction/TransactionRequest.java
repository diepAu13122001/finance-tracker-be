package com.diepau1312.financeTrackerBE.dto.transaction;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import java.util.UUID;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "Request tạo/cập nhật giao dịch")
public class TransactionRequest {

  @Schema(description = "Loại giao dịch", example = "EXPENSE", allowableValues = { "INCOME", "EXPENSE" })
  @NotNull(message = "Loại giao dịch không được để trống")
  private TransactionType type;

  @Schema(description = "Số tiền (VND, không có số thập phân)", example = "45000")
  @NotNull(message = "Số tiền không được để trống")
  @Min(value = 1, message = "Số tiền phải lớn hơn 0")
  private Long amount;

  @Schema(description = "Ghi chú tùy chọn", example = "Cà phê Highlands")
  private String note;

  @Schema(description = "Ngày giao dịch (không được là tương lai)", example = "2026-01-15")
  @NotNull(message = "Ngày giao dịch không được để trống")
  @PastOrPresent(message = "Ngày giao dịch không được lớn hơn ngày hiện tại")
  private LocalDate transactionDate;

  @Schema(description = "Đơn vị tiền tệ", example = "VND", defaultValue = "VND")
  private String currency = "VND";

  private UUID categoryId; // optional, có thể null
}