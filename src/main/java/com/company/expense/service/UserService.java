package com.company.expense.service;

import com.company.expense.dto.request.CreateUserRequest;
import com.company.expense.dto.response.UserResponse;
import java.util.List;

public interface UserService {

    UserResponse createUser(CreateUserRequest request);

    UserResponse getUser(Long userId);

    List<UserResponse> listUsers();

    UserResponse assignManager(Long userId, Long managerId);
}
