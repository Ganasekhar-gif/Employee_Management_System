package com.company.expense.service;

import com.company.expense.dto.request.CreateUserRequest;
import com.company.expense.dto.response.UserResponse;
import java.util.List;

public interface UserService {

    UserResponse createUser(CreateUserRequest request);

    UserResponse getUser(Long userId);

    List<UserResponse> listUsers();

    UserResponse assignManager(Long userId, Long managerId);

    /**
     * Deactivates the user. A manager's direct reports move to the department manager in the same transaction.
     */
    UserResponse deactivateUser(Long actorId, Long userId);

    UserResponse activateUser(Long actorId, Long userId);
}
