package com.diepau1312.financeTrackerBE.dto.transaction;

import jakarta.validation.constraints.PastOrPresent;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "Request tạo/cập nhật giao dịch")
public class TransactionRequest {

  @Schema(description = "Loại giao dịch", example = "EXPENSE",
      allowableValues = {"INCOME", "EXPENSE"})
  @NotNull(message = "Loại giao dịch không được để trống")
  private TransactionType type;

  @Schema(description = "Số tiền (VND, không có số thập phân)", example = "45000")
  @NotNull
  @Min(1)
  private Long amount;

  @Schema(description = "Ghi chú tùy chọn", example = "Cà phê Highlands")
  private String note;

  @Schema(description = "Ngày giao dịch (không được là tương lai)", example = "2026-01-15")
  @NotNull
  @PastOrPresent
  private LocalDate transactionDate;

  @Schema(description = "Đơn vị tiền tệ", example = "VND", defaultValue = "VND")
  private String currency = "VND";
}