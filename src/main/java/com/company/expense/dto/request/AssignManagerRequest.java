package com.company.expense.dto.request;

import jakarta.validation.constraints.NotNull;

public record AssignManagerRequest(@NotNull Long managerId) {
}
