package com.company.expense.dto.response;

import com.company.expense.entity.Department;

public record DepartmentResponse(Long id, String name, String code, Long managerId, boolean active) {

    public static DepartmentResponse from(Department department) {
        Long managerId = department.getManager() == null ? null : department.getManager().getId();
        return new DepartmentResponse(
                department.getId(), department.getName(), department.getCode(), managerId, department.isActive());
    }
}
