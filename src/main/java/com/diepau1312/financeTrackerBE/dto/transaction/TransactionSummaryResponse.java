package com.diepau1312.financeTrackerBE.dto.transaction;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class TransactionSummaryResponse {

    private Long totalIncome;
    private Long totalExpense;
    private Long balance;         // totalIncome - totalExpense

    // Thông tin giới hạn cho Free user
    private long  transactionCount;   // số giao dịch tháng này
    private int   transactionLimit;   // giới hạn (-1 = không giới hạn)
    private boolean limitReached;
    private LocalDate startDate;
    private LocalDate endDate;
}