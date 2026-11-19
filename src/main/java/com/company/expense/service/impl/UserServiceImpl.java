package com.company.expense.service.impl;

import com.company.expense.dto.request.CreateUserRequest;
import com.company.expense.dto.response.UserResponse;
import com.company.expense.entity.Department;
import com.company.expense.entity.Role;
import com.company.expense.entity.User;
import com.company.expense.enums.ApprovalStepStatus;
import com.company.expense.enums.AuditAction;
import com.company.expense.enums.AuditEntityType;
import com.company.expense.exception.BusinessRuleException;
import com.company.expense.exception.DuplicateResourceException;
import com.company.expense.exception.ResourceNotFoundException;
import com.company.expense.repository.DepartmentRepository;
import com.company.expense.repository.ExpenseApprovalStepRepository;
import com.company.expense.repository.RoleRepository;
import com.company.expense.repository.UserRepository;
import com.company.expense.service.AuditLogService;
import com.company.expense.service.UserService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ExpenseApprovalStepRepository stepRepository;
    private final AuditLogService auditLogService;

    public UserServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            ExpenseApprovalStepRepository stepRepository,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.stepRepository = stepRepository;
        this.auditLogService = auditLogService;
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

    @Override
    public UserResponse deactivateUser(Long actorId, Long userId) {
        User actor = findUser(actorId);
        User user = findUser(userId);
        if (user.equals(actor)) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }
        if (!user.isActive()) {
            return UserResponse.from(user);
        }
        long pendingSteps = stepRepository.countByAssignedApproverAndStatus(user, ApprovalStepStatus.PENDING);
        if (pendingSteps > 0) {
            throw new BusinessRuleException(user.getFullName() + " still has " + pendingSteps
                    + " pending approval step(s). They must be decided before the account is deactivated");
        }

        Department department = user.getDepartment();
        boolean wasDepartmentManager = department != null && user.equals(department.getManager());
        User replacement = department == null || wasDepartmentManager ? null : department.getManager();
        List<User> reports = userRepository.findByManager(user);
        requireReplacementFor(user, reports, replacement);

        if (wasDepartmentManager) {
            department.removeManager();
        }
        int reassigned = 0;
        for (User report : reports) {
            if (report.isActive()) {
                report.assignManager(replacement);
                reassigned++;
            } else {
                report.removeManager();
            }
        }
        user.deactivate();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reassignedReports", reassigned);
        details.put("removedAsDepartmentManager", wasDepartmentManager);
        auditLogService.record(actor, AuditEntityType.USER, userId, AuditAction.DEACTIVATE, details);
        return UserResponse.from(user);
    }

    @Override
    public UserResponse activateUser(Long actorId, Long userId) {
        User user = findUser(userId);
        if (!user.isActive()) {
            user.activate();
            auditLogService.record(findUser(actorId), AuditEntityType.USER, userId, AuditAction.ACTIVATE, Map.of());
        }
        return UserResponse.from(user);
    }

    // Checked before anything changes, so a blocked deactivation leaves every entity untouched.
    private static void requireReplacementFor(User leavingManager, List<User> reports, User replacement) {
        for (User report : reports) {
            if (report.isActive()
                    && (replacement == null || replacement.equals(report) || !replacement.isActive())) {
                throw new BusinessRuleException("Assign another active manager to " + report.getFullName()
                        + " before deactivating " + leavingManager.getFullName());
            }
        }
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
