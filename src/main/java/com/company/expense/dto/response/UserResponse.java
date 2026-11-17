package com.company.expense.dto.response;

import static com.company.expense.dto.response.EntityReferences.idOf;

import com.company.expense.entity.User;
import com.company.expense.enums.RoleName;
import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        RoleName role,
        Long departmentId,
        Long managerId,
        boolean active,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                idOf(user.getDepartment()),
                idOf(user.getManager()),
                user.isActive(),
                user.getCreatedAt());
    }
}
