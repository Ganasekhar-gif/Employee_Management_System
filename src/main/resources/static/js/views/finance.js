import { api, downloadFile } from '../api.js';
import { navigate } from '../app.js';
import {
    busy, button, emptyState, field, format, h, pageHeader, select, stat, statusBadge, table, toast,
} from '../ui.js';

const FILTER_KEYS = ['from', 'to', 'departmentId', 'categoryId', 'employeeId'];

export async function financeView({ query }) {
    const filters = Object.fromEntries(FILTER_KEYS.map((key) => [key, query.get(key) || '']));
    const [options, report] = await Promise.all([
        api('/api/finance/expenses/filters'),
        api('/api/finance/expenses', { query: filters }),
    ]);

    const toOptions = (items, withDetail) => items.map((item) => ({
        value: item.id,
        label: withDetail && item.detail ? `${item.name} · ${item.detail}` : item.name,
    }));
    const from = h('input', { type: 'date', name: 'from', value: filters.from });
    const to = h('input', { type: 'date', name: 'to', value: filters.to });
    const department = select('departmentId', toOptions(options.departments), filters.departmentId, { placeholder: 'All departments' });
    const category = select('categoryId', toOptions(options.categories), filters.categoryId, { placeholder: 'All categories' });
    const employee = select('employeeId', toOptions(options.employees, true), filters.employeeId, { placeholder: 'All employees' });

    const form = h('form', { class: 'filters' },
        field('From', from), field('To', to), field('Department', department), field('Category', category), field('Employee', employee),
        h('div', { class: 'row' },
            h('button', { type: 'submit', class: 'btn-primary' }, 'Apply'),
            button('Reset', () => navigate('/finance'))));
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        const params = new URLSearchParams();
        new FormData(form).forEach((value, key) => {
            if (value) params.set(key, value);
        });
        navigate(`/finance${params.toString() ? `?${params}` : ''}`);
    });

    const exportButton = button('⬇️ Export CSV', () => busy(exportButton, () =>
        downloadFile('/api/finance/expenses/export', { query: filters }, 'approved-expenses.csv')
            .then(() => toast('Export downloaded. It is recorded in the audit log.', 'success'))
            .catch((error) => toast(error.message, 'error'))), 'btn-primary', { disabled: report.count === 0 });

    const average = report.count ? Number(report.totalAmount) / report.count : 0;
    const columns = [
        { label: 'Expense', render: (r) => h('span', { class: 'strong' }, `#${r.expenseId}`) },
        { label: 'Employee', render: (r) => r.employee },
        { label: 'Department', render: (r) => r.department || '—' },
        { label: 'Category', render: (r) => r.category },
        { label: 'Expense date', render: (r) => format.date(r.expenseDate) },
        { label: 'Amount', numeric: true, render: (r) => h('span', { class: 'amount' }, format.money(r.amount)) },
        { label: 'Status', render: (r) => statusBadge(r.status) },
        { label: 'Submitted', render: (r) => format.dateTime(r.submittedAt) },
    ];

    return h('div', {},
        pageHeader('Approved expenses', 'Filter fully approved expenses, check the totals and export them for payment.', [exportButton]),
        h('section', { class: 'card' }, h('div', { class: 'card-body' }, form)),
        h('div', { class: 'stats' },
            stat('Expenses', report.count),
            stat('Total amount', format.money(report.totalAmount)),
            stat('Average', format.money(average))),
        h('section', { class: 'card' }, table(columns, report.expenses, {
            onRowClick: (r) => navigate(`/expenses/${r.expenseId}`),
            empty: emptyState('📊', 'No approved expenses match', 'Try widening the date range or clearing filters.'),
        })));
}
