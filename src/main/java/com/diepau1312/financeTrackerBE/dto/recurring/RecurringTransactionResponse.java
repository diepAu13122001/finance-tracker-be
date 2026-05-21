package com.diepau1312.financeTrackerBE.dto.recurring;

import com.diepau1312.financeTrackerBE.entity.RecurringTransaction;
import com.diepau1312.financeTrackerBE.entity.Transaction;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class RecurringTransactionResponse {

    private UUID id;
    private Transaction.TransactionType type;
    private Long amount;
    private String note;

    private UUID categoryId;
    private String categoryName;
    private String categoryColor;

    private UUID walletId;
    private String walletName;

    private RecurringTransaction.Frequency frequency;
    private Integer dayOfMonth;
    private Integer dayOfWeek;
    private LocalDate nextExecutionDate;
    private Boolean isActive;

    public static RecurringTransactionResponse from(RecurringTransaction r) {
        return RecurringTransactionResponse.builder()
            .id(r.getId())
            .type(r.getType())
            .amount(r.getAmount())
            .note(r.getNote())
            .categoryId(r.getCategory() != null ? r.getCategory().getId() : null)
            .categoryName(r.getCategory() != null ? r.getCategory().getName() : null)
            .categoryColor(r.getCategory() != null ? r.getCategory().getColor() : null)
            .walletId(r.getWallet() != null ? r.getWallet().getId() : null)
            .walletName(r.getWallet() != null ? r.getWallet().getName() : null)
            .frequency(r.getFrequency())
            .dayOfMonth(r.getDayOfMonth())
            .dayOfWeek(r.getDayOfWeek())
            .nextExecutionDate(r.getNextExecutionDate())
            .isActive(r.getIsActive())
            .build();
    }
}
