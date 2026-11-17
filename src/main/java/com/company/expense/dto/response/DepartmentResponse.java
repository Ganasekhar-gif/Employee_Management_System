package com.company.expense.dto.response;

import static com.company.expense.dto.response.EntityReferences.idOf;

import com.company.expense.entity.Department;

public record DepartmentResponse(Long id, String name, String code, Long managerId, boolean active) {

    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getName(),
                department.getCode(),
                idOf(department.getManager()),
                department.isActive());
    }
}
