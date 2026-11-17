package com.company.expense.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.expense.dto.request.CreateUserRequest;
import com.company.expense.dto.response.UserResponse;
import com.company.expense.entity.Department;
import com.company.expense.entity.Role;
import com.company.expense.entity.User;
import com.company.expense.enums.RoleName;
import com.company.expense.exception.DuplicateResourceException;
import com.company.expense.exception.ResourceNotFoundException;
import com.company.expense.repository.DepartmentRepository;
import com.company.expense.repository.RoleRepository;
import com.company.expense.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void createsUserWithNormalizedEmailAndHashedPassword() {
        Role employeeRole = role(RoleName.EMPLOYEE);
        when(userRepository.existsByEmail("asha@company.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.EMPLOYEE)).thenReturn(Optional.of(employeeRole));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(new Department("Engineering", "ENG")));
        when(passwordEncoder.encode("secret-pass")).thenReturn("{bcrypt}hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.createUser(
                new CreateUserRequest(" Asha@Company.com ", "secret-pass", "Asha Rao", RoleName.EMPLOYEE, 1L, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}hashed");
        assertThat(response.email()).isEqualTo("asha@company.com");
        assertThat(response.role()).isEqualTo(RoleName.EMPLOYEE);
    }

    @Test
    void rejectsDuplicateEmailBeforeHashingPassword() {
        when(userRepository.existsByEmail("asha@company.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(
                        new CreateUserRequest("ASHA@company.com", "secret-pass", "Asha", RoleName.EMPLOYEE, 1L, null)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownDepartment() {
        when(userRepository.existsByEmail("asha@company.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.EMPLOYEE)).thenReturn(Optional.of(mock(Role.class)));
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.createUser(
                        new CreateUserRequest("asha@company.com", "secret-pass", "Asha", RoleName.EMPLOYEE, 99L, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Department 99 was not found");
    }

    private static Role role(RoleName name) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(name);
        return role;
    }
}
