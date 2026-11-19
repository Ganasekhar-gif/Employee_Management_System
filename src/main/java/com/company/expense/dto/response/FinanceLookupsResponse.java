package com.company.expense.dto.response;

import com.company.expense.entity.Department;
import com.company.expense.entity.ExpenseCategory;
import com.company.expense.entity.User;
import java.util.List;

public record FinanceLookupsResponse(List<Option> departments, List<Option> categories, List<Option> employees) {

    public static FinanceLookupsResponse of(
            List<Department> departments, List<ExpenseCategory> categories, List<User> employees) {
        return new FinanceLookupsResponse(
                departments.stream().map(d -> new Option(d.getId(), d.getName(), d.getCode())).toList(),
                categories.stream().map(c -> new Option(c.getId(), c.getName(), null)).toList(),
                employees.stream().map(FinanceLookupsResponse::employeeOption).toList());
    }

    private static Option employeeOption(User user) {
        Department department = user.getDepartment();
        return new Option(user.getId(), user.getFullName(), department == null ? null : department.getName());
    }

    public record Option(Long id, String name, String detail) {
    }
}
