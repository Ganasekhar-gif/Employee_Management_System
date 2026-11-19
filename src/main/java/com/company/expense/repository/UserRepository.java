package com.company.expense.repository;

import com.company.expense.entity.User;
import com.company.expense.enums.RoleName;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    boolean existsByRole_Name(RoleName roleName);

    long countByRole_NameAndActiveTrue(RoleName roleName);

    List<User> findByManager(User manager);

    @EntityGraph(attributePaths = "department")
    List<User> findByRole_NameInOrderByFullName(Collection<RoleName> roleNames);

    // Serialises one employee's expense changes so two requests cannot both pass the open-expense check.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
