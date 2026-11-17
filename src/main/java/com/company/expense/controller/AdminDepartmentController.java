package com.company.expense.controller;

import com.company.expense.dto.request.AssignManagerRequest;
import com.company.expense.dto.request.CreateDepartmentRequest;
import com.company.expense.dto.response.DepartmentResponse;
import com.company.expense.service.DepartmentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/admin/departments")
public class AdminDepartmentController {

    private final DepartmentService departmentService;

    public AdminDepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> createDepartment(@Valid @RequestBody CreateDepartmentRequest request) {
        DepartmentResponse created = departmentService.createDepartment(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<DepartmentResponse> listDepartments() {
        return departmentService.listDepartments();
    }

    @PutMapping("/{id}/manager")
    public DepartmentResponse assignManager(@PathVariable Long id, @Valid @RequestBody AssignManagerRequest request) {
        return departmentService.assignManager(id, request.managerId());
    }
}
