package com.company.expense.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.expense.enums.RoleName;
import com.company.expense.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

class UserTest {

    private final Department engineering = new Department("Engineering", "eng");
    private final Department sales = new Department("Sales", "SALES");

    @Test
    void normalizesEmailToLowercaseWithoutSurroundingSpaces() {
        User user = employee("  Asha.Rao@Company.COM ", engineering);

        assertThat(user.getEmail()).isEqualTo("asha.rao@company.com");
    }

    @Test
    void requiresDepartmentForEmployees() {
        assertThatThrownBy(() -> employee("asha@company.com", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must belong to a department");
    }

    @Test
    void allowsFinanceUsersWithoutDepartment() {
        User finance = new User("fin@company.com", "hash", "Finance User", new Role(RoleName.FINANCE), null);

        assertThat(finance.getDepartment()).isNull();
    }

    @Test
    void assignsActiveManagerFromSameDepartment() {
        User manager = manager("lead@company.com", engineering);
        User employee = employee("asha@company.com", engineering);

        employee.assignManager(manager);

        assertThat(employee.getManager()).isSameAs(manager);
    }

    @Test
    void rejectsSelfAsManager() {
        User manager = manager("lead@company.com", engineering);

        assertThatThrownBy(() -> manager.assignManager(manager))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("own manager");
    }

    @Test
    void rejectsManagerFromAnotherDepartment() {
        User salesManager = manager("sales.lead@company.com", sales);
        User employee = employee("asha@company.com", engineering);

        assertThatThrownBy(() -> employee.assignManager(salesManager))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsManagerWithoutManagerRole() {
        User colleague = employee("ravi@company.com", engineering);
        User employee = employee("asha@company.com", engineering);

        assertThatThrownBy(() -> employee.assignManager(colleague))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsInactiveManager() {
        User manager = manager("lead@company.com", engineering);
        manager.deactivate();
        User employee = employee("asha@company.com", engineering);

        assertThatThrownBy(() -> employee.assignManager(manager))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void departmentAcceptsOnlyItsOwnActiveManager() {
        User engineeringManager = manager("lead@company.com", engineering);
        User salesManager = manager("sales.lead@company.com", sales);

        engineering.assignManager(engineeringManager);

        assertThat(engineering.getManager()).isSameAs(engineeringManager);
        assertThatThrownBy(() -> engineering.assignManager(salesManager))
                .isInstanceOf(BusinessRuleException.class);
    }

    private static User employee(String email, Department department) {
        return new User(email, "hash", "Employee", new Role(RoleName.EMPLOYEE), department);
    }

    private static User manager(String email, Department department) {
        return new User(email, "hash", "Manager", new Role(RoleName.MANAGER), department);
    }
}
