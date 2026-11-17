package com.company.expense.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.expense.entity.Role;
import com.company.expense.enums.RoleName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RepositoryTest
class RoleRepositoryTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void migrationSeedsExactlyTheFourApplicationRoles() {
        assertThat(roleRepository.findAll())
                .extracting(Role::getName)
                .containsExactlyInAnyOrder(RoleName.values());
    }

    @Test
    void findsRoleByName() {
        assertThat(roleRepository.findByName(RoleName.FINANCE))
                .get()
                .extracting(Role::getDescription)
                .asString()
                .contains("Finance");
    }
}
