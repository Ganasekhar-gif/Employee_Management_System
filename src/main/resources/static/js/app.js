import { api, ApiError, clearCredentials, hasCredentials, onUnauthorized, saveCredentials } from './api.js';
import { badge, busy, callout, emptyState, field, format, h, linkButton, loading, toast } from './ui.js';
import { expenseDetailView, expenseFormView, expenseListView } from './views/expenses.js';
import { approvalHistoryView, approvalQueueView } from './views/approvals.js';
import { financeView } from './views/finance.js';
import { auditView, categoriesView, departmentsView, rulesView, usersView } from './views/admin.js';

const EMPLOYEE = 'EMPLOYEE';
const MANAGER = 'MANAGER';
const FINANCE = 'FINANCE';
const ADMIN = 'ADMIN';

const routes = [
    { path: '/expenses', view: expenseListView, roles: [EMPLOYEE, MANAGER] },
    { path: '/expenses/new', view: expenseFormView, roles: [EMPLOYEE, MANAGER] },
    { path: '/expenses/:id/edit', view: expenseFormView, roles: [EMPLOYEE, MANAGER] },
    { path: '/expenses/:id', view: expenseDetailView, roles: [EMPLOYEE, MANAGER, FINANCE] },
    { path: '/approvals', view: approvalQueueView, roles: [MANAGER, FINANCE] },
    { path: '/approvals/history', view: approvalHistoryView, roles: [MANAGER, FINANCE] },
    { path: '/finance', view: financeView, roles: [FINANCE] },
    { path: '/admin/users', view: usersView, roles: [ADMIN] },
    { path: '/admin/departments', view: departmentsView, roles: [ADMIN] },
    { path: '/admin/categories', view: categoriesView, roles: [ADMIN] },
    { path: '/admin/rules', view: rulesView, roles: [ADMIN] },
    { path: '/admin/audit', view: auditView, roles: [ADMIN] },
];

const NAVIGATION = {
    [EMPLOYEE]: [
        { section: 'My work' },
        { href: '/expenses', icon: '🧾', label: 'My expenses' },
        { href: '/expenses/new', icon: '➕', label: 'New expense' },
    ],
    [MANAGER]: [
        { section: 'Approvals' },
        { href: '/approvals', icon: '✅', label: 'Approval queue', queue: true },
        { href: '/approvals/history', icon: '🕘', label: 'My decisions' },
        { section: 'My work' },
        { href: '/expenses', icon: '🧾', label: 'My expenses' },
        { href: '/expenses/new', icon: '➕', label: 'New expense' },
    ],
    [FINANCE]: [
        { section: 'Finance' },
        { href: '/finance', icon: '📊', label: 'Reports & export' },
        { section: 'Approvals' },
        { href: '/approvals', icon: '✅', label: 'Approval queue', queue: true },
        { href: '/approvals/history', icon: '🕘', label: 'My decisions' },
    ],
    [ADMIN]: [
        { section: 'Organisation' },
        { href: '/admin/users', icon: '👥', label: 'Users' },
        { href: '/admin/departments', icon: '🏢', label: 'Departments' },
        { section: 'Configuration' },
        { href: '/admin/categories', icon: '🏷️', label: 'Categories' },
        { href: '/admin/rules', icon: '🔀', label: 'Approval rules' },
        { section: 'Compliance' },
        { href: '/admin/audit', icon: '📜', label: 'Audit log' },
    ],
};

const HOME = { [EMPLOYEE]: '/expenses', [MANAGER]: '/approvals', [FINANCE]: '/finance', [ADMIN]: '/admin/users' };

const root = document.getElementById('app');
let currentUser = null;
let renderToken = 0;

export function navigate(path) {
    if (location.hash === `#${path}`) {
        render();
    } else {
        location.hash = path;
    }
}

// Lets a view mark the section it belongs to, e.g. an approver reviewing someone else's expense.
export function highlightNav(href) {
    document.querySelectorAll('.nav a').forEach((link) => {
        link.classList.toggle('active', link.getAttribute('href') === `#${href}`);
    });
}

export function refreshQueueCount() {
    const badgeElement = document.querySelector('[data-queue-count]');
    if (!badgeElement || ![MANAGER, FINANCE].includes(currentUser?.role)) {
        return;
    }
    api('/api/approvals')
        .then((tasks) => {
            badgeElement.textContent = tasks.length || '';
            badgeElement.hidden = tasks.length === 0;
        })
        .catch(() => {});
}

function signOut(message) {
    clearCredentials();
    currentUser = null;
    document.getElementById('toasts').replaceChildren();
    if (message) {
        toast(message, 'error');
    }
    location.hash = '/login';
    render();
}

onUnauthorized(() => {
    if (currentUser) {
        signOut('Your session has ended. Please sign in again.');
    }
});

function parseLocation() {
    const [path, queryString] = (location.hash.slice(1) || '/').split('?');
    return { path, query: new URLSearchParams(queryString || '') };
}

function matchRoute(path) {
    for (const route of routes) {
        const names = [];
        const pattern = new RegExp(`^${route.path.replace(/:(\w+)/g, (_, name) => {
            names.push(name);
            return '([^/]+)';
        })}$`);
        const match = pattern.exec(path);
        if (match) {
            const params = Object.fromEntries(names.map((name, index) => [name, decodeURIComponent(match[index + 1])]));
            return { route, params };
        }
    }
    return null;
}

async function render() {
    const token = ++renderToken;
    const { path, query } = parseLocation();

    if (!currentUser && hasCredentials()) {
        try {
            currentUser = await api('/api/auth/me');
        } catch {
            clearCredentials();
        }
    }
    if (token !== renderToken) return;
    if (!currentUser) {
        root.replaceChildren(loginView());
        return;
    }
    if (path === '/' || path === '/login') {
        navigate(HOME[currentUser.role]);
        return;
    }

    const matched = matchRoute(path);
    const main = h('main', { class: 'main' }, loading());
    root.replaceChildren(h('div', { class: 'shell' }, sidebar(path), main));
    refreshQueueCount();

    if (!matched) {
        main.replaceChildren(emptyState('🧭', 'Page not found', 'That page does not exist.',
            [linkButton('Go to home', `#${HOME[currentUser.role]}`, 'btn-primary')]));
        return;
    }
    if (!matched.route.roles.includes(currentUser.role)) {
        main.replaceChildren(emptyState('🔒', 'Not available for your role',
            `${format.title(currentUser.role)} users cannot open this page.`,
            [linkButton('Go to home', `#${HOME[currentUser.role]}`, 'btn-primary')]));
        return;
    }

    try {
        const view = await matched.route.view({ params: matched.params, query, user: currentUser });
        if (token === renderToken) {
            main.replaceChildren(view);
            window.scrollTo(0, 0);
        }
    } catch (error) {
        if (token !== renderToken) return;
        const notFound = error instanceof ApiError && error.status === 404;
        main.replaceChildren(emptyState(notFound ? '🔍' : '⚠️',
            notFound ? 'Not found' : 'Something went wrong',
            notFound ? 'It does not exist or you do not have access to it.' : error.message,
            [linkButton('Go to home', `#${HOME[currentUser.role]}`, 'btn-primary')]));
    }
}

function sidebar(activePath) {
    const items = NAVIGATION[currentUser.role] || [];
    const activeHref = items
        .filter((item) => item.href && (activePath === item.href || activePath.startsWith(`${item.href}/`)))
        .sort((a, b) => b.href.length - a.href.length)[0]?.href;
    return h('aside', { class: 'sidebar' },
        h('div', { class: 'brand' }, h('div', { class: 'brand-logo' }, '₹'), 'Expense Manager'),
        h('nav', { class: 'nav' }, items.map((item) => item.section
            ? h('div', { class: 'nav-section' }, item.section)
            : h('a', { href: `#${item.href}`, class: item.href === activeHref ? 'active' : '' },
                h('span', { class: 'icon' }, item.icon), item.label,
                item.queue ? h('span', { class: 'count', 'data-queue-count': true, hidden: true }) : null))),
        h('div', { class: 'sidebar-user' },
            h('div', { class: 'name' }, currentUser.fullName),
            h('div', { class: 'email' }, currentUser.email),
            h('div', { class: 'row' }, badge(format.title(currentUser.role), 'role')),
            h('button', { type: 'button', onclick: () => signOut() }, 'Sign out')));
}

function loginView() {
    const email = h('input', { type: 'email', name: 'email', required: true, autocomplete: 'username', placeholder: 'you@company.com' });
    const password = h('input', { type: 'password', name: 'password', required: true, autocomplete: 'current-password' });
    const submit = h('button', { type: 'submit', class: 'btn-primary btn-block' }, 'Sign in');
    const errorBox = h('div');

    const form = h('form', { class: 'form' }, field('Email', email), field('Password', password), errorBox, submit);
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        errorBox.replaceChildren();
        await busy(submit, async () => {
            saveCredentials(email.value.trim(), password.value);
            try {
                currentUser = await api('/api/auth/me');
                toast(`Welcome, ${currentUser.fullName}`, 'success');
                navigate(HOME[currentUser.role]);
            } catch (error) {
                clearCredentials();
                errorBox.replaceChildren(callout('danger', null,
                    error.status === 401 ? 'Incorrect email or password, or the account is inactive.' : error.message));
            }
        });
    });

    const step = (number, text) => h('div', { class: 'flow-item' }, h('div', { class: 'num' }, number), h('div', {}, text));
    return h('div', { class: 'login' },
        h('div', { class: 'login-hero' },
            h('h1', {}, 'Expense Management'),
            h('p', {}, 'Submit expenses with receipts, route them through the right approvers, and give finance a clean, auditable export.'),
            h('div', { class: 'flow' },
                step(1, 'Employees create an expense and attach receipts'),
                step(2, 'Their manager approves first, then any approvers the rules add'),
                step(3, 'Rejected expenses come back with a reason and can be resubmitted'),
                step(4, 'Finance filters approved expenses and exports CSV'))),
        h('div', { class: 'login-panel' },
            h('div', { class: 'login-card' },
                h('h2', {}, 'Sign in'),
                h('div', { class: 'muted' }, 'Use the account your administrator created for you.'),
                form)));
}

window.addEventListener('hashchange', render);
render();
