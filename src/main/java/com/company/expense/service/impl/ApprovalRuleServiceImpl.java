package com.company.expense.service.impl;

import com.company.expense.dto.request.ApprovalRuleRequest;
import com.company.expense.dto.response.ApprovalRuleResponse;
import com.company.expense.entity.ApprovalRule;
import com.company.expense.entity.Department;
import com.company.expense.entity.ExpenseCategory;
import com.company.expense.entity.User;
import com.company.expense.enums.AuditAction;
import com.company.expense.enums.AuditEntityType;
import com.company.expense.exception.DuplicateResourceException;
import com.company.expense.exception.ResourceNotFoundException;
import com.company.expense.repository.ApprovalRuleRepository;
import com.company.expense.repository.DepartmentRepository;
import com.company.expense.repository.ExpenseCategoryRepository;
import com.company.expense.repository.UserRepository;
import com.company.expense.service.ApprovalRuleService;
import com.company.expense.service.AuditLogService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ApprovalRuleServiceImpl implements ApprovalRuleService {

    private final ApprovalRuleRepository ruleRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public ApprovalRuleServiceImpl(
            ApprovalRuleRepository ruleRepository,
            ExpenseCategoryRepository categoryRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            AuditLogService auditLogService) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    public ApprovalRuleResponse createRule(Long actorId, ApprovalRuleRequest request) {
        ExpenseCategory category = request.categoryId() == null ? null : findCategory(request.categoryId());
        Department department = request.departmentId() == null ? null : findDepartment(request.departmentId());

        ApprovalRule rule = new ApprovalRule(request.name(), category, department,
                request.minAmount(), request.maxAmount(), request.priority());
        addSteps(rule, request);
        if (request.activeOrDefault()) {
            requirePriorityAvailable(rule.getPriority(), null);
            rule.activate();
        }

        ApprovalRule saved = ruleRepository.save(rule);
        auditLogService.record(findUser(actorId), AuditEntityType.APPROVAL_RULE, saved.getId(), AuditAction.CREATE,
                Map.of("name", saved.getName(), "priority", saved.getPriority(), "active", saved.isActive()));
        return ApprovalRuleResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalRuleResponse> listRules() {
        return ruleRepository.findAllByOrderByPriorityAscIdAsc().stream().map(ApprovalRuleResponse::from).toList();
    }

    @Override
    public ApprovalRuleResponse updateRule(Long actorId, Long ruleId, ApprovalRuleRequest request) {
        ApprovalRule rule = findRule(ruleId);
        if (rule.isActive()) {
            requirePriorityAvailable(request.priority(), ruleId);
        }
        rule.update(request.name(),
                request.categoryId() == null ? null : findCategory(request.categoryId()),
                request.departmentId() == null ? null : findDepartment(request.departmentId()),
                request.minAmount(), request.maxAmount(), request.priority());
        rule.clearSteps();
        // Old steps must be deleted before the new ones reuse their step numbers.
        ruleRepository.flush();
        addSteps(rule, request);

        auditLogService.record(findUser(actorId), AuditEntityType.APPROVAL_RULE, ruleId, AuditAction.UPDATE,
                Map.of("name", rule.getName(), "priority", rule.getPriority(), "stepCount", rule.getSteps().size()));
        return ApprovalRuleResponse.from(rule);
    }

    @Override
    public ApprovalRuleResponse activateRule(Long actorId, Long ruleId) {
        ApprovalRule rule = findRule(ruleId);
        if (!rule.isActive()) {
            requirePriorityAvailable(rule.getPriority(), ruleId);
            rule.activate();
            auditLogService.record(findUser(actorId), AuditEntityType.APPROVAL_RULE, ruleId, AuditAction.ACTIVATE,
                    Map.of("priority", rule.getPriority()));
        }
        return ApprovalRuleResponse.from(rule);
    }

    @Override
    public ApprovalRuleResponse deactivateRule(Long actorId, Long ruleId) {
        ApprovalRule rule = findRule(ruleId);
        if (rule.isActive()) {
            rule.deactivate();
            auditLogService.record(findUser(actorId), AuditEntityType.APPROVAL_RULE, ruleId, AuditAction.DEACTIVATE,
                    Map.of("priority", rule.getPriority()));
        }
        return ApprovalRuleResponse.from(rule);
    }

    private void addSteps(ApprovalRule rule, ApprovalRuleRequest request) {
        request.steps().forEach(step -> rule.addStep(
                step.approverType(),
                step.approverUserId() == null ? null : findUser(step.approverUserId())));
    }

    private void requirePriorityAvailable(int priority, Long ruleId) {
        boolean taken = ruleRepository.findByPriorityAndActiveTrue(priority)
                .filter(other -> !other.getId().equals(ruleId))
                .isPresent();
        if (taken) {
            throw new DuplicateResourceException("Another active rule already uses priority " + priority);
        }
    }

    private ApprovalRule findRule(Long ruleId) {
        return ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval rule", ruleId));
    }

    private ExpenseCategory findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }

    private Department findDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
