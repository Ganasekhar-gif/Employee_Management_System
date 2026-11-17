package com.company.expense.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectionRequest(@NotBlank @Size(max = 500) String reason) {
}
