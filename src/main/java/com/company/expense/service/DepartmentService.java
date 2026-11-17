package com.company.expense.service;

import com.company.expense.dto.request.CreateDepartmentRequest;
import com.company.expense.dto.response.DepartmentResponse;
import java.util.List;

public interface DepartmentService {

    DepartmentResponse createDepartment(CreateDepartmentRequest request);

    List<DepartmentResponse> listDepartments();

    DepartmentResponse assignManager(Long departmentId, Long managerId);
}
