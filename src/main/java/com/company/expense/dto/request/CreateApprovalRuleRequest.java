package com.company.expense.dto.request;

import com.company.expense.enums.ApproverType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateApprovalRuleRequest(
        @NotBlank @Size(max = 150) String name,
        Long categoryId,
        Long departmentId,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal minAmount,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal maxAmount,
        @NotNull @Min(1) Integer priority,
        Boolean active,
        @NotEmpty List<@Valid Step> steps) {

    public boolean activeOrDefault() {
        return active == null || active;
    }

    public record Step(@NotNull ApproverType approverType, Long approverUserId) {
    }
}
