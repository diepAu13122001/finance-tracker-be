package com.diepau1312.financeTrackerBE.dto.recurring;

import com.diepau1312.financeTrackerBE.entity.RecurringTransaction.Frequency;
import com.diepau1312.financeTrackerBE.entity.Transaction.TransactionType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateRecurringRequest {

    @NotNull
    private TransactionType type; // INCOME | EXPENSE

    @NotNull @Positive
    private Long amount;

    @Size(max = 500)
    private String note;

    private UUID categoryId;
    private UUID walletId;

    @NotNull
    private Frequency frequency; // DAILY | WEEKLY | MONTHLY | YEARLY

    /** Ngày trong tháng (bắt buộc với MONTHLY/YEARLY) */
    @Min(1) @Max(31)
    private Integer dayOfMonth;

    /** Thứ trong tuần — 1=T2, 7=CN (bắt buộc với WEEKLY) */
    @Min(1) @Max(7)
    private Integer dayOfWeek;

    /** Ngày thực hiện đầu tiên (mặc định: hôm nay nếu không truyền) */
    private LocalDate startDate;
}
