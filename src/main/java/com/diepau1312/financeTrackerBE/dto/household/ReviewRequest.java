package com.diepau1312.financeTrackerBE.dto.household;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ReviewRequest {
    @Min(1)
    @Max(5)
    @NotNull
    private Short rating;

    @Size(max = 1000)
    private String reviewText;

    private Boolean wouldBuyAgain;
}