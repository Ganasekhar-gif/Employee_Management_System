package com.company.expense.service.impl;

import com.company.expense.dto.request.CreateDepartmentRequest;
import com.company.expense.dto.response.DepartmentResponse;
import com.company.expense.entity.Department;
import com.company.expense.entity.User;
import com.company.expense.exception.DuplicateResourceException;
import com.company.expense.exception.ResourceNotFoundException;
import com.company.expense.repository.DepartmentRepository;
import com.company.expense.repository.UserRepository;
import com.company.expense.service.DepartmentService;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public DepartmentServiceImpl(DepartmentRepository departmentRepository, UserRepository userRepository) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    @Override
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        Department department = new Department(request.name(), request.code());
        if (departmentRepository.existsByCode(department.getCode())) {
            throw new DuplicateResourceException("Department code " + department.getCode() + " already exists");
        }
        if (departmentRepository.existsByName(department.getName())) {
            throw new DuplicateResourceException("Department name " + department.getName() + " already exists");
        }
        return DepartmentResponse.from(departmentRepository.save(department));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> listDepartments() {
        return departmentRepository.findAll(Sort.by("id")).stream()
                .map(DepartmentResponse::from)
                .toList();
    }

    @Override
    public DepartmentResponse assignManager(Long departmentId, Long managerId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", managerId));
        department.assignManager(manager);
        return DepartmentResponse.from(department);
    }
}
