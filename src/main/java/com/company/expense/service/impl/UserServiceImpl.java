package com.company.expense.service.impl;

import com.company.expense.dto.request.CreateUserRequest;
import com.company.expense.dto.response.UserResponse;
import com.company.expense.entity.Department;
import com.company.expense.entity.Role;
import com.company.expense.entity.User;
import com.company.expense.exception.DuplicateResourceException;
import com.company.expense.exception.ResourceNotFoundException;
import com.company.expense.repository.DepartmentRepository;
import com.company.expense.repository.RoleRepository;
import com.company.expense.repository.UserRepository;
import com.company.expense.service.UserService;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("A user with email " + email + " already exists");
        }
        Role role = roleRepository.findByName(request.role())
                .orElseThrow(() -> new IllegalStateException("Role " + request.role() + " is not seeded"));
        Department department = request.departmentId() == null ? null : findDepartment(request.departmentId());

        User user = new User(email, passwordEncoder.encode(request.password()), request.fullName(), role, department);
        if (request.managerId() != null) {
            user.assignManager(findUser(request.managerId()));
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAll(Sort.by("id")).stream()
                .map(UserResponse::from)
                .toList();
    }

    @Override
    public UserResponse assignManager(Long userId, Long managerId) {
        User user = findUser(userId);
        user.assignManager(findUser(managerId));
        return UserResponse.from(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private Department findDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
    }
}
