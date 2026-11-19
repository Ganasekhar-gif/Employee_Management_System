import { api } from '../api.js';
import { navigate } from '../app.js';
import { emptyState, format, h, linkButton, pageHeader, stat, statusBadge, table } from '../ui.js';
import { STEP_LABELS } from './expenses.js';

export async function approvalQueueView({ user }) {
    const tasks = await api('/api/approvals');
    const total = tasks.reduce((sum, task) => sum + Number(task.amount), 0);
    const oldest = tasks.length ? tasks[0].submittedAt : null;

    const columns = [
        { label: 'Expense', render: (t) => h('span', { class: 'strong' }, `#${t.expenseId}`) },
        { label: 'Employee', render: (t) => t.employeeName },
        { label: 'Category', render: (t) => t.categoryName },
        { label: 'Description', render: (t) => t.description },
        { label: 'Date', render: (t) => format.date(t.expenseDate) },
        { label: 'Amount', numeric: true, render: (t) => h('span', { class: 'amount' }, format.money(t.amount)) },
        { label: 'Your step', render: (t) => h('span', {}, STEP_LABELS[t.approverType], h('div', { class: 'muted small' }, `step ${t.stepOrder}${t.submissionNo > 1 ? ` · resubmission #${t.submissionNo}` : ''}`)) },
        { label: 'Waiting since', render: (t) => format.dateTime(t.submittedAt) },
        { label: '', numeric: true, render: (t) => linkButton('Review', `#/expenses/${t.expenseId}`, 'btn-primary btn-sm') },
    ];

    return h('div', {},
        pageHeader('Approval queue',
            user.role === 'FINANCE'
                ? 'Expenses waiting for a finance review. They appear here once every earlier approver has signed off.'
                : 'Expenses from your team that are waiting for your decision.',
            [linkButton('🕘 My decisions', '#/approvals/history')]),
        h('div', { class: 'stats' },
            stat('Waiting for you', tasks.length),
            stat('Total value', format.money(total)),
            stat('Oldest request', oldest ? format.date(oldest.slice(0, 10)) : '—')),
        h('section', { class: 'card' }, table(columns, tasks, {
            onRowClick: (t) => navigate(`/expenses/${t.expenseId}`),
            empty: emptyState('🎉', 'You are all caught up', 'New requests will appear here as soon as they reach your step.'),
        })));
}

export async function approvalHistoryView() {
    const decisions = await api('/api/approvals/history');
    const approved = decisions.filter((d) => d.decision === 'APPROVED').length;

    const columns = [
        { label: 'Decided', render: (d) => format.dateTime(d.decidedAt) },
        { label: 'Expense', render: (d) => h('span', { class: 'strong' }, `#${d.expenseId}`) },
        { label: 'Employee', render: (d) => d.employeeName },
        { label: 'Category', render: (d) => d.categoryName },
        { label: 'Amount', numeric: true, render: (d) => h('span', { class: 'amount' }, format.money(d.amount)) },
        { label: 'Your decision', render: (d) => statusBadge(d.decision) },
        { label: 'Comment', render: (d) => d.comment || h('span', { class: 'muted' }, '—') },
        { label: 'Expense now', render: (d) => statusBadge(d.expenseStatus) },
    ];

    return h('div', {},
        pageHeader('My decisions', 'Every approval step you have decided, newest first.', [linkButton('✅ Approval queue', '#/approvals')]),
        h('div', { class: 'stats' },
            stat('Decisions', decisions.length),
            stat('Approved', approved),
            stat('Rejected', decisions.length - approved)),
        h('section', { class: 'card' }, table(columns, decisions, {
            onRowClick: (d) => navigate(`/expenses/${d.expenseId}`),
            empty: emptyState('🕘', 'No decisions yet', 'Approvals and rejections you make will be listed here.'),
        })));
}
