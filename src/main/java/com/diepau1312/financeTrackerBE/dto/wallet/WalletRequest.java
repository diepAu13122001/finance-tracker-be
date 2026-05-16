package com.diepau1312.financeTrackerBE.dto.wallet;

import com.diepau1312.financeTrackerBE.entity.Wallet.WalletSubtype;
import com.diepau1312.financeTrackerBE.entity.Wallet.WalletType;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class WalletRequest {

    @NotBlank(message = "Tên ví không được để trống")
    @Size(max = 100, message = "Tên tối đa 100 ký tự")
    private String name;

    private String icon;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Màu phải là mã hex 6 ký tự")
    private String color;

    @NotNull(message = "Loại ví không được để trống")
    private WalletType type;

    // Chỉ dùng khi type = DEBT
    private WalletSubtype subtype;

    // ── CREDIT_CARD ────────────────────────────────────────────────────────────
    @Min(value = 0, message = "Hạn mức phải >= 0")
    private Long creditLimit;

    @Min(value = 1, message = "Ngày đáo hạn từ 1-28")
    @Max(value = 28, message = "Ngày đáo hạn từ 1-28")
    private Integer billingDate;

    // ── INSTALLMENT ────────────────────────────────────────────────────────────
    @Min(value = 1, message = "Số kỳ phải >= 1")
    private Integer numberOfPeriods;

    @Min(value = 0, message = "Tiền trả mỗi kỳ phải >= 0")
    private Long monthlyPayment;

    @Min(value = 0, message = "Số tiền vay phải >= 0")
    private Long initialAmount;
}