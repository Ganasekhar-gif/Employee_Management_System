package com.company.expense.controller;

import com.company.expense.dto.request.AssignManagerRequest;
import com.company.expense.dto.request.CreateUserRequest;
import com.company.expense.dto.response.UserResponse;
import com.company.expense.security.AuthenticatedUser;
import com.company.expense.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.createUser(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<UserResponse> listUsers() {
        return userService.listUsers();
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return userService.getUser(id);
    }

    @PutMapping("/{id}/manager")
    public UserResponse assignManager(@PathVariable Long id, @Valid @RequestBody AssignManagerRequest request) {
        return userService.assignManager(id, request.managerId());
    }

    @PostMapping("/{id}/deactivate")
    public UserResponse deactivateUser(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return userService.deactivateUser(principal.id(), id);
    }

    @PostMapping("/{id}/activate")
    public UserResponse activateUser(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return userService.activateUser(principal.id(), id);
    }
}
