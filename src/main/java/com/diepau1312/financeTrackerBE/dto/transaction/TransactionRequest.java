package com.diepau1312.financeTrackerBE.dto.transaction;

import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TransactionRequest {

    @NotNull(message = "Loại giao dịch không được để trống")
    private TransactionType type;  // INCOME hoặc EXPENSE

    @NotNull(message = "Số tiền không được để trống")
    @Min(value = 1, message = "Số tiền phải lớn hơn 0")
    private Long amount;

    private String note;

    @NotNull(message = "Ngày không được để trống")
    private LocalDate transactionDate;

    private String currency = "VND";
}