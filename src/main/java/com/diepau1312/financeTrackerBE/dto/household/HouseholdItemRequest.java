package com.diepau1312.financeTrackerBE.dto.household;

import com.diepau1312.financeTrackerBE.entity.HouseholdItem.ItemCategory;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class HouseholdItemRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 100)
    private String brand;

    @NotNull
    private ItemCategory category;

    @Min(0)
    private Long price;

    private LocalDate purchaseDate;
    private LocalDate expiryDate;

    @DecimalMin("0")
    private BigDecimal quantity;

    @Size(max = 20)
    private String unit;

    @Min(1)
    @Max(90)
    private Integer notifyBeforeDays = 7;

    private String notes;
}