package com.company.expense.dto.request;

import com.company.expense.entity.User;
import com.company.expense.enums.RoleName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 255)
        @Pattern(regexp = User.EMAIL_PATTERN, message = "must look like name@company.com") String email,
        // BCrypt ignores everything after 72 bytes.
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 150) String fullName,
        @NotNull RoleName role,
        Long departmentId,
        Long managerId) {

    @Override
    public String toString() {
        return "CreateUserRequest[email=" + email + ", fullName=" + fullName + ", role=" + role
                + ", departmentId=" + departmentId + ", managerId=" + managerId + "]";
    }
}
