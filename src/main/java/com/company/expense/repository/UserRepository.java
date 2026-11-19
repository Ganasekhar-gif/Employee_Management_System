package com.company.expense.repository;

import com.company.expense.entity.User;
import com.company.expense.enums.RoleName;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    boolean existsByRole_Name(RoleName roleName);

    List<User> findByManager(User manager);

    @EntityGraph(attributePaths = "department")
    List<User> findByRole_NameInOrderByFullName(Collection<RoleName> roleNames);
}
