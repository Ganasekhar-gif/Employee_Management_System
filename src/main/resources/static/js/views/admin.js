import { api } from '../api.js';
import { navigate } from '../app.js';
import {
    badge, button, callout, card, confirmDialog, emptyState, emptyToNull, field, format, formDialog, h,
    pageHeader, select, stat, table, toast,
} from '../ui.js';

const ROLES = ['EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN'];
const ROLES_WITH_DEPARTMENT = ['EMPLOYEE', 'MANAGER'];
const ROLE_PLURALS = { EMPLOYEE: 'Employees', MANAGER: 'Managers', FINANCE: 'Finance', ADMIN: 'Admins' };

function activeBadge(active) {
    return h('span', { class: `badge ${active ? 'active' : 'inactive'}` }, active ? 'Active' : 'Inactive');
}

function byId(items) {
    return new Map(items.map((item) => [item.id, item]));
}

function reloadAfter(message) {
    return () => {
        toast(message, 'success');
        navigate(location.hash.slice(1));
    };
}

async function guarded(action) {
    try {
        await action();
    } catch (error) {
        toast(error.message, 'error');
    }
}

// ---------- Users ----------

export async function usersView() {
    const [users, departments] = await Promise.all([api('/api/admin/users'), api('/api/admin/departments')]);
    const usersById = byId(users);
    const departmentsById = byId(departments);
    const counts = Object.fromEntries(ROLES.map((role) => [role, users.filter((u) => u.role === role && u.active).length]));

    const columns = [
        { label: 'User', render: (u) => h('div', {}, h('div', { class: 'strong' }, u.fullName), h('div', { class: 'muted small' }, u.email)) },
        { label: 'Role', render: (u) => badge(format.title(u.role), 'role') },
        { label: 'Department', render: (u) => departmentsById.get(u.departmentId)?.name || h('span', { class: 'muted' }, '—') },
        { label: 'Direct manager', render: (u) => usersById.get(u.managerId)?.fullName || h('span', { class: 'muted' }, ROLES_WITH_DEPARTMENT.includes(u.role) ? 'Department manager' : '—') },
        { label: 'Status', render: activeBadge },
        {
            label: '', numeric: true, render: (u) => h('div', { class: 'actions' },
                ROLES_WITH_DEPARTMENT.includes(u.role) && u.active
                    ? button('Change manager', () => changeManagerDialog(u, users, departmentsById), 'btn-sm') : null,
                u.role === 'ADMIN' && u.active ? null : u.active
                    ? button('Deactivate', () => deactivateUser(u, users), 'btn-sm btn-ghost-danger')
                    : button('Activate', () => guarded(async () => {
                        await api(`/api/admin/users/${u.id}/activate`, { method: 'POST' });
                        reloadAfter(`${u.fullName} can sign in again`)();
                    }), 'btn-sm')),
        },
    ];

    return h('div', {},
        pageHeader('Users', 'Create accounts, assign roles and direct managers.',
            [button('➕ New user', () => newUserDialog(users, departments), 'btn-primary')]),
        h('div', { class: 'stats' }, ROLES.map((role) => stat(ROLE_PLURALS[role], counts[role], 'active'))),
        departments.length ? null : h('div', { class: 'mb' }, callout('info', 'Start with a department',
            'Employees and managers must belong to a department. Create one on the Departments page first.')),
        h('section', { class: 'card' }, table(columns, users)));
}

function managerOptions(users, departmentId, excludeId) {
    return users
        .filter((u) => u.role === 'MANAGER' && u.active && u.departmentId === Number(departmentId) && u.id !== excludeId)
        .map((u) => ({ value: u.id, label: u.fullName }));
}

function newUserDialog(users, departments) {
    const role = select('role', ROLES.map((r) => ({ value: r, label: format.title(r) })), 'EMPLOYEE', { required: true });
    const department = select('departmentId', departments.map((d) => ({ value: d.id, label: `${d.name} (${d.code})` })), '', { placeholder: 'No department' });
    const manager = select('managerId', [], '', { placeholder: 'Use the department manager' });
    const managerField = field('Direct manager', manager, { name: 'managerId', hint: 'Optional. Without one, the department manager approves.' });

    const refresh = () => {
        const needsDepartment = ROLES_WITH_DEPARTMENT.includes(role.value);
        department.required = needsDepartment;
        managerField.hidden = !needsDepartment;
        manager.replaceChildren(h('option', { value: '' }, 'Use the department manager'),
            ...managerOptions(users, department.value).map((o) => h('option', { value: String(o.value) }, o.label)));
    };
    role.addEventListener('change', refresh);
    department.addEventListener('change', refresh);
    refresh();

    formDialog({
        title: 'New user',
        submitLabel: 'Create user',
        fields: h('div', { class: 'form-grid' },
            field('Full name', h('input', { name: 'fullName', required: true, maxlength: '150' }), { name: 'fullName', full: true }),
            field('Email', h('input', { name: 'email', type: 'email', required: true, autocomplete: 'off' }), { name: 'email' }),
            field('Temporary password', h('input', { name: 'password', type: 'password', required: true, minlength: '8', autocomplete: 'new-password' }),
                { name: 'password', hint: 'At least 8 characters. Share it securely.' }),
            field('Role', role, { name: 'role' }),
            field('Department', department, { name: 'departmentId', hint: 'Required for employees and managers.' }),
            managerField),
        onSubmit: async (data) => {
            const needsDepartment = ROLES_WITH_DEPARTMENT.includes(data.get('role'));
            await api('/api/admin/users', {
                method: 'POST',
                body: {
                    fullName: data.get('fullName'),
                    email: data.get('email'),
                    password: data.get('password'),
                    role: data.get('role'),
                    departmentId: emptyToNull(data.get('departmentId')),
                    managerId: needsDepartment ? emptyToNull(data.get('managerId')) : null,
                },
            });
            reloadAfter(`${data.get('fullName')} created`)();
        },
    });
}

function changeManagerDialog(user, users, departmentsById) {
    const options = managerOptions(users, user.departmentId, user.id);
    if (!options.length) {
        toast(`There is no other active manager in ${departmentsById.get(user.departmentId)?.name || 'this department'}`, 'error');
        return;
    }
    formDialog({
        title: `Direct manager for ${user.fullName}`,
        submitLabel: 'Assign',
        fields: field('Manager', select('managerId', options, user.managerId, { required: true, placeholder: 'Choose a manager' }),
            { name: 'managerId', hint: 'Only active managers in the same department are listed.' }),
        onSubmit: async (data) => {
            await api(`/api/admin/users/${user.id}/manager`, { method: 'PUT', body: { managerId: Number(data.get('managerId')) } });
            reloadAfter('Manager updated')();
        },
    });
}

async function deactivateUser(user, users) {
    const reports = users.filter((u) => u.managerId === user.id && u.active).length;
    const message = reports
        ? `${user.fullName} manages ${reports} people. They will be moved to their department manager. Deactivation is blocked if no other manager is available or if ${user.fullName} still has pending approvals.`
        : `${user.fullName} will no longer be able to sign in. Their history stays in the system.`;
    if (await confirmDialog(`Deactivate ${user.fullName}?`, message, { confirmLabel: 'Deactivate', kind: 'btn-danger' })) {
        await guarded(async () => {
            const result = await api(`/api/admin/users/${user.id}/deactivate`, { method: 'POST' });
            reloadAfter(`${result.fullName} deactivated`)();
        });
    }
}

// ---------- Departments ----------

export async function departmentsView() {
    const [departments, users] = await Promise.all([api('/api/admin/departments'), api('/api/admin/users')]);
    const usersById = byId(users);

    const columns = [
        { label: 'Department', render: (d) => h('span', { class: 'strong' }, d.name) },
        { label: 'Code', render: (d) => h('code', {}, d.code) },
        { label: 'People', numeric: true, render: (d) => users.filter((u) => u.departmentId === d.id && u.active).length },
        {
            label: 'Department manager', render: (d) => (d.managerId
                ? usersById.get(d.managerId)?.fullName
                : h('span', { class: 'badge pending' }, 'Not set')),
        },
        { label: '', numeric: true, render: (d) => button('Set manager', () => departmentManagerDialog(d, users), 'btn-sm') },
    ];

    return h('div', {},
        pageHeader('Departments', 'The department manager approves for employees without a direct manager.',
            [button('➕ New department', newDepartmentDialog, 'btn-primary')]),
        h('section', { class: 'card' }, table(columns, departments, {
            empty: emptyState('🏢', 'No departments yet', 'Create a department, add its people, then choose its manager.'),
        })));
}

function newDepartmentDialog() {
    formDialog({
        title: 'New department',
        submitLabel: 'Create',
        fields: h('div', { class: 'form-grid' },
            field('Name', h('input', { name: 'name', required: true, maxlength: '100', placeholder: 'Engineering' }), { name: 'name' }),
            field('Code', h('input', { name: 'code', required: true, maxlength: '20', placeholder: 'ENG' }), { name: 'code', hint: 'Stored in upper case.' })),
        onSubmit: async (data) => {
            await api('/api/admin/departments', { method: 'POST', body: { name: data.get('name'), code: data.get('code') } });
            reloadAfter('Department created')();
        },
    });
}

function departmentManagerDialog(department, users) {
    const options = managerOptions(users, department.id);
    if (!options.length) {
        toast(`Add an active Manager to ${department.name} first`, 'error');
        return;
    }
    formDialog({
        title: `Manager of ${department.name}`,
        submitLabel: 'Assign',
        fields: field('Manager', select('managerId', options, department.managerId, { required: true, placeholder: 'Choose a manager' }), { name: 'managerId' }),
        onSubmit: async (data) => {
            await api(`/api/admin/departments/${department.id}/manager`, { method: 'PUT', body: { managerId: Number(data.get('managerId')) } });
            reloadAfter('Department manager updated')();
        },
    });
}

// ---------- Categories ----------

export async function categoriesView() {
    const categories = await api('/api/admin/categories');
    const columns = [
        { label: 'Category', render: (c) => h('span', { class: 'strong' }, c.name) },
        { label: 'Description', render: (c) => c.description || h('span', { class: 'muted' }, '—') },
        { label: 'Receipt', render: (c) => (c.requiresReceipt ? badge('Required', 'role') : badge('Optional')) },
        { label: 'Status', render: (c) => activeBadge(c.active) },
        { label: '', numeric: true, render: (c) => button('Edit', () => categoryDialog(c), 'btn-sm') },
    ];
    return h('div', {},
        pageHeader('Expense categories', 'Employees pick a category for every expense. Inactive categories cannot be used for new submissions.',
            [button('➕ New category', () => categoryDialog(null), 'btn-primary')]),
        h('section', { class: 'card' }, table(columns, categories, {
            empty: emptyState('🏷️', 'No categories yet', 'Add categories such as Travel, Meals or Software.'),
        })));
}

function categoryDialog(category) {
    const requiresReceipt = h('input', { type: 'checkbox', name: 'requiresReceipt', checked: category ? category.requiresReceipt : true });
    const active = h('input', { type: 'checkbox', name: 'active', checked: category ? category.active : true });
    formDialog({
        title: category ? `Edit ${category.name}` : 'New category',
        fields: [
            category ? null : field('Name', h('input', { name: 'name', required: true, maxlength: '100' }), { name: 'name' }),
            field('Description', h('input', { name: 'description', maxlength: '255', value: category?.description ?? '' }), { name: 'description' }),
            h('label', { class: 'checkbox' }, requiresReceipt, 'A receipt is required to submit'),
            category ? h('label', { class: 'checkbox' }, active, 'Active') : null,
        ],
        onSubmit: async (data) => {
            const body = { description: emptyToNull(data.get('description')), requiresReceipt: requiresReceipt.checked };
            if (category) {
                await api(`/api/admin/categories/${category.id}`, { method: 'PUT', body: { ...body, active: active.checked } });
            } else {
                await api('/api/admin/categories', { method: 'POST', body: { ...body, name: data.get('name') } });
            }
            reloadAfter(category ? 'Category updated' : 'Category created')();
        },
    });
}

// ---------- Approval rules ----------

export async function rulesView() {
    const [rules, categories, departments, users] = await Promise.all([
        api('/api/admin/approval-rules'), api('/api/admin/categories'), api('/api/admin/departments'), api('/api/admin/users'),
    ]);
    const categoriesById = byId(categories);
    const departmentsById = byId(departments);
    const usersById = byId(users);

    const describeStep = (step) => (step.approverType === 'FINANCE_ROLE'
        ? 'Finance team'
        : usersById.get(step.approverUserId)?.fullName || `User #${step.approverUserId}`);
    const amountRange = (rule) => {
        if (rule.maxAmount === null) return `${format.money(rule.minAmount)} and above`;
        return `${format.money(rule.minAmount)} – ${format.money(rule.maxAmount)}`;
    };

    const list = rules.length ? rules.map((rule) => h('div', { class: `rule${rule.active ? '' : ' inactive'}` },
        h('div', { class: 'priority', title: 'Priority — lower wins' }, rule.priority),
        h('div', { class: 'body' },
            h('div', { class: 'row' }, h('span', { class: 'strong' }, rule.name), activeBadge(rule.active)),
            h('div', { class: 'chips' },
                h('span', { class: 'chip' }, `Category: ${rule.categoryId ? categoriesById.get(rule.categoryId)?.name : 'Any'}`),
                h('span', { class: 'chip' }, `Department: ${rule.departmentId ? departmentsById.get(rule.departmentId)?.name : 'Any'}`),
                h('span', { class: 'chip' }, `Amount: ${amountRange(rule)}`)),
            h('div', { class: 'path' },
                h('span', { class: 'node fixed' }, 'Manager'),
                rule.steps.map((step) => [h('span', { class: 'arrow' }, '→'), h('span', { class: 'node' }, describeStep(step))]),
                h('span', { class: 'arrow' }, '→'), h('span', { class: 'node fixed' }, 'Approved'))),
        rule.active
            ? button('Deactivate', () => guarded(async () => {
                await api(`/api/admin/approval-rules/${rule.id}/deactivate`, { method: 'POST' });
                reloadAfter('Rule deactivated')();
            }), 'btn-sm')
            : button('Activate', () => guarded(async () => {
                await api(`/api/admin/approval-rules/${rule.id}/activate`, { method: 'POST' });
                reloadAfter('Rule activated')();
            }), 'btn-sm')))
        : emptyState('🔀', 'No rules yet', 'Without rules, the manager\'s approval is enough for every expense.');

    return h('div', {},
        pageHeader('Approval rules', 'Rules add approvers after the mandatory manager step.',
            [button('➕ New rule', () => ruleDialog(categories, departments, users), 'btn-primary')]),
        h('div', { class: 'mb' }, callout('info', 'How rules are chosen',
            'On submit, the active rule with the lowest priority whose category, department and amount all match is used. Its steps are copied onto the expense, so later rule changes never affect expenses already submitted.')),
        h('section', { class: 'card' }, list));
}

function ruleDialog(categories, departments, users) {
    const approvers = users
        .filter((u) => u.active && (u.role === 'MANAGER' || u.role === 'FINANCE'))
        .map((u) => ({ value: u.id, label: `${u.fullName} (${format.title(u.role)})` }));
    const stepsContainer = h('div', { class: 'stack' });

    const addStep = () => {
        const type = select('approverType', [
            { value: 'FINANCE_ROLE', label: 'Finance team (anyone)' },
            { value: 'SPECIFIC_USER', label: 'Specific person' },
        ], 'FINANCE_ROLE');
        const person = select('approverUserId', approvers, '', { placeholder: 'Choose a person' });
        const sync = () => {
            person.disabled = type.value !== 'SPECIFIC_USER';
            person.required = type.value === 'SPECIFIC_USER';
        };
        type.addEventListener('change', sync);
        sync();
        const row = h('div', { class: 'step-row' }, h('span', { class: 'n' }), type, person,
            button('✕', () => {
                row.remove();
                renumber();
            }, 'btn-sm', { title: 'Remove step' }));
        stepsContainer.append(row);
        renumber();
    };
    const renumber = () => stepsContainer.querySelectorAll('.step-row .n').forEach((n, index) => { n.textContent = index + 2; });
    addStep();

    const options = (items) => items.map((item) => ({ value: item.id, label: item.name }));
    formDialog({
        title: 'New approval rule',
        submitLabel: 'Create rule',
        wide: true,
        fields: [
            h('div', { class: 'form-grid' },
                field('Name', h('input', { name: 'name', required: true, maxlength: '150', placeholder: 'Large spend needs finance review' }), { name: 'name', full: true }),
                field('Category', select('categoryId', options(categories.filter((c) => c.active)), '', { placeholder: 'Any category' }), { name: 'categoryId' }),
                field('Department', select('departmentId', options(departments), '', { placeholder: 'Any department' }), { name: 'departmentId' }),
                field('Minimum amount (INR)', h('input', { name: 'minAmount', type: 'number', min: '0', step: '0.01', placeholder: '0.00' }), { name: 'minAmount', hint: 'Inclusive. Empty means 0.' }),
                field('Maximum amount (INR)', h('input', { name: 'maxAmount', type: 'number', min: '0', step: '0.01', placeholder: 'No limit' }), { name: 'maxAmount', hint: 'Inclusive. Empty means no upper limit.' }),
                field('Priority', h('input', { name: 'priority', type: 'number', min: '1', step: '1', required: true, value: '1' }), { name: 'priority', hint: 'Lower number wins. Must be unique among active rules.' }),
                h('label', { class: 'checkbox' }, h('input', { type: 'checkbox', name: 'active', checked: true }), 'Activate immediately')),
            h('div', { class: 'field' },
                h('label', {}, 'Approval path'),
                h('div', { class: 'step-row' }, h('span', { class: 'n' }, '1'), h('span', { class: 'strong' }, 'Employee\'s manager'), h('span', { class: 'muted small' }, 'Always first — added automatically'), h('span')),
                stepsContainer,
                h('div', {}, button('➕ Add step', addStep, 'btn-sm'))),
        ],
        onSubmit: async (data, form) => {
            const steps = [...form.querySelectorAll('.step-row')].filter((row) => row.querySelector('select')).map((row) => {
                const [type, person] = row.querySelectorAll('select');
                return { approverType: type.value, approverUserId: type.value === 'SPECIFIC_USER' ? Number(person.value) : null };
            });
            if (!steps.length) {
                throw Object.assign(new Error('Add at least one step after the manager.'), { fieldErrors: {} });
            }
            await api('/api/admin/approval-rules', {
                method: 'POST',
                body: {
                    name: data.get('name'),
                    categoryId: emptyToNull(data.get('categoryId')),
                    departmentId: emptyToNull(data.get('departmentId')),
                    minAmount: emptyToNull(data.get('minAmount')),
                    maxAmount: emptyToNull(data.get('maxAmount')),
                    priority: Number(data.get('priority')),
                    active: data.get('active') === 'on',
                    steps,
                },
            });
            reloadAfter('Rule created')();
        },
    });
}

// ---------- Audit log ----------

const ENTITY_TYPES = ['EXPENSE', 'APPROVAL_RULE', 'USER', 'EXPENSE_REPORT'];

export async function auditView({ query }) {
    const entityType = query.get('entityType') || '';
    const entityId = query.get('entityId') || '';
    const logs = await api('/api/admin/audit-logs', { query: entityType && entityId ? { entityType, entityId } : {} });

    const type = select('entityType', ENTITY_TYPES.map((t) => ({ value: t, label: format.title(t) })), entityType, { placeholder: 'Choose type', required: true });
    const id = h('input', { name: 'entityId', type: 'number', min: '1', required: true, value: entityId, placeholder: 'e.g. 12' });
    const form = h('form', { class: 'filters' }, field('Entity type', type), field('Entity id', id),
        h('div', { class: 'row' }, h('button', { type: 'submit', class: 'btn-primary' }, 'Show history'), button('Latest 100', () => navigate('/admin/audit'))));
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        navigate(`/admin/audit?entityType=${encodeURIComponent(type.value)}&entityId=${encodeURIComponent(id.value)}`);
    });

    const actionKind = (action) => ({ APPROVE: 'approved', REJECT: 'rejected', DEACTIVATE: 'rejected', DELETE: 'rejected', SUBMIT: 'submitted', RESUBMIT: 'submitted' }[action] || 'role');
    const columns = [
        { label: 'When', render: (l) => format.dateTime(l.createdAt) },
        { label: 'Who', render: (l) => l.actorName || h('span', { class: 'muted' }, 'System') },
        { label: 'Action', render: (l) => h('span', { class: `badge plain ${actionKind(l.action)}` }, format.title(l.action)) },
        { label: 'Entity', render: (l) => `${format.title(l.entityType)}${l.entityId ? ` #${l.entityId}` : ''}` },
        {
            label: 'Details', render: (l) => h('div', { class: 'chips' },
                Object.entries(l.details || {}).map(([key, value]) => h('span', { class: 'chip' }, `${key}: ${typeof value === 'object' ? JSON.stringify(value) : value}`))),
        },
    ];

    return h('div', {},
        pageHeader('Audit log', entityType && entityId
            ? `History of ${format.title(entityType)} #${entityId}, oldest first.`
            : 'The latest 100 recorded actions, newest first.'),
        card(null, form),
        h('section', { class: 'card' }, table(columns, logs, {
            empty: emptyState('📜', 'No audit entries', 'Actions such as submissions, decisions and exports are recorded here.'),
        })));
}
