package com.diepau1312.financeTrackerBE.dto.recurring;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateRecurringRequest {

    @Positive
    private Long amount;

    @Size(max = 500)
    private String note;

    private UUID categoryId;
    private UUID walletId;
    private Boolean isActive;
}
