package com.company.expense.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.expense.entity.Department;
import com.company.expense.entity.Role;
import com.company.expense.entity.User;
import com.company.expense.enums.RoleName;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@RepositoryTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    private Department engineering;
    private Role employeeRole;
    private Role managerRole;

    @BeforeEach
    void setUp() {
        engineering = departmentRepository.save(new Department("Engineering", "ENG"));
        employeeRole = roleRepository.findByName(RoleName.EMPLOYEE).orElseThrow();
        managerRole = roleRepository.findByName(RoleName.MANAGER).orElseThrow();
    }

    @Test
    void savesUserWithManagerAndDepartmentManager() {
        User manager = userRepository.save(new User("lead@company.com", "hash", "Team Lead", managerRole, engineering));
        User employee = new User("asha@company.com", "hash", "Asha Rao", employeeRole, engineering);
        employee.assignManager(manager);
        userRepository.save(employee);
        engineering.assignManager(manager);
        flushAndClear();

        User reloaded = userRepository.findByEmail("asha@company.com").orElseThrow();

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getManager().getId()).isEqualTo(manager.getId());
        assertThat(reloaded.getDepartment().getManager().getId()).isEqualTo(manager.getId());
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isEqualTo(reloaded.getCreatedAt());
    }

    @Test
    void reloadedTimestampsMatchSavedTimestamps() {
        User saved = userRepository.saveAndFlush(
                new User("asha@company.com", "hash", "Asha Rao", employeeRole, engineering));
        entityManager.clear();

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getCreatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        userRepository.saveAndFlush(new User("asha@company.com", "hash", "Asha Rao", employeeRole, engineering));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                        new User("ASHA@company.com", "hash", "Another Asha", employeeRole, engineering)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByEmailUsesNormalizedEmail() {
        userRepository.saveAndFlush(new User("Asha@Company.com", "hash", "Asha Rao", employeeRole, engineering));

        assertThat(userRepository.existsByEmail(User.normalizeEmail(" ASHA@company.COM "))).isTrue();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
