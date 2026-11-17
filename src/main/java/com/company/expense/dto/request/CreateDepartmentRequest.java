package com.company.expense.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDepartmentRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 20) String code) {
}
