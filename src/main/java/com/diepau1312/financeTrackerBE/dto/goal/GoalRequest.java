package com.diepau1312.financeTrackerBE.dto.goal;

import com.diepau1312.financeTrackerBE.entity.Goal.GoalType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class GoalRequest {

    @NotBlank(message = "Tên mục tiêu không được để trống")
    @Size(max = 100, message = "Tên tối đa 100 ký tự")
    private String name;

    private String icon;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Màu phải là mã hex 6 ký tự")
    private String color;

    @NotNull(message = "Loại mục tiêu không được để trống")
    private GoalType type;

    @NotNull(message = "Số tiền mục tiêu không được để trống")
    @Min(value = 1, message = "Số tiền phải lớn hơn 0")
    private Long targetAmount;

    private LocalDate deadline; // optional
}