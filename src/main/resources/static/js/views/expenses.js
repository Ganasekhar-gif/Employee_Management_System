import { api, downloadFile, fetchBlob } from '../api.js';
import { highlightNav, navigate, refreshQueueCount } from '../app.js';
import {
    busy, button, callout, card, clearFieldErrors, confirmDialog, emptyState, field, format, h, linkButton, modal,
    pageHeader, select, showFieldErrors, stat, statusBadge, table, toast, today,
} from '../ui.js';

const MAX_RECEIPT_BYTES = 5 * 1024 * 1024;
const RECEIPT_TYPES = ['application/pdf', 'image/png', 'image/jpeg'];

export const STEP_LABELS = {
    MANAGER: 'Manager approval',
    FINANCE_ROLE: 'Finance review',
    SPECIFIC_USER: 'Named approver',
};

const STATUS_FILTERS = [
    { status: '', label: 'All expenses' },
    { status: 'DRAFT', label: 'Drafts' },
    { status: 'SUBMITTED', label: 'In approval' },
    { status: 'APPROVED', label: 'Approved' },
    { status: 'REJECTED', label: 'Rejected' },
];

// ---------- List ----------

export async function expenseListView({ query }) {
    const expenses = await api('/api/expenses');
    const selected = query.get('status') || '';
    const visible = selected ? expenses.filter((expense) => expense.status === selected) : expenses;
    const rejected = expenses.filter((expense) => expense.status === 'REJECTED').length;

    const stats = h('div', { class: 'stats' }, STATUS_FILTERS.map(({ status, label }) => {
        const matching = status ? expenses.filter((expense) => expense.status === status) : expenses;
        const total = matching.reduce((sum, expense) => sum + Number(expense.amount), 0);
        return stat(label, matching.length, format.money(total), {
            selected: status === selected,
            onClick: () => navigate(status ? `/expenses?status=${status}` : '/expenses'),
        });
    }));

    const columns = [
        { label: '#', render: (e) => h('span', { class: 'muted' }, `#${e.id}`) },
        { label: 'Date', render: (e) => format.date(e.expenseDate) },
        { label: 'Category', render: (e) => e.categoryName },
        { label: 'Description', render: (e) => e.description },
        { label: 'Amount', numeric: true, render: (e) => h('span', { class: 'amount' }, format.money(e.amount)) },
        { label: 'Status', render: (e) => statusBadge(e.status) },
        { label: 'Submitted', render: (e) => (e.submittedAt ? format.dateTime(e.submittedAt) : h('span', { class: 'muted' }, 'Not yet')) },
    ];

    return h('div', {},
        pageHeader('My expenses', 'Create expenses, attach receipts and follow them through approval.',
            [linkButton('➕ New expense', '#/expenses/new', 'btn-primary')]),
        rejected ? h('div', { class: 'mb' }, callout('danger', `${rejected} expense(s) need your attention`,
            'A reviewer rejected them. Open one to read the reason, fix it and resubmit.')) : null,
        stats,
        h('section', { class: 'card' }, table(columns, visible, {
            onRowClick: (e) => navigate(`/expenses/${e.id}`),
            empty: emptyState('🧾', selected ? 'No expenses with this status' : 'No expenses yet',
                'Record a business expense to get it reimbursed.',
                [linkButton('Create your first expense', '#/expenses/new', 'btn-primary')]),
        })));
}

// ---------- Create / edit ----------

export async function expenseFormView({ params }) {
    const editing = params.id !== undefined;
    const [categories, expense] = await Promise.all([
        api('/api/categories'),
        editing ? api(`/api/expenses/${params.id}`) : null,
    ]);

    if (editing && !expense.permissions.canEdit) {
        return h('div', {},
            pageHeader(`Expense #${expense.id}`, null, [], backLink(`/expenses/${expense.id}`, 'Back to expense')),
            callout('warning', 'This expense cannot be edited',
                'Only drafts and rejected expenses can be changed. Expenses in approval or already approved are locked.'));
    }

    const categoryOptions = categories.map((category) => ({
        value: category.id,
        label: category.requiresReceipt ? `${category.name} (receipt required)` : category.name,
    }));
    const categorySelect = select('categoryId', categoryOptions, expense?.categoryId, { placeholder: 'Choose a category', required: true });
    const amount = h('input', { name: 'amount', type: 'number', min: '0.01', step: '0.01', required: true, value: expense?.amount ?? '', placeholder: '0.00' });
    const date = h('input', { name: 'expenseDate', type: 'date', max: today(), required: true, value: expense?.expenseDate ?? today() });
    const description = h('textarea', { name: 'description', maxlength: '500', required: true, placeholder: 'What was it for? e.g. Taxi to client office for quarterly review' }, expense?.description ?? '');
    const counter = h('span', {}, `${description.value.length}/500`);
    description.addEventListener('input', () => { counter.textContent = `${description.value.length}/500`; });

    const submit = h('button', { type: 'submit', class: 'btn-primary' }, editing ? 'Save changes' : 'Create draft');
    const errorBox = h('div');
    const form = h('form', { class: 'form' },
        h('div', { class: 'form-grid' },
            field('Category', categorySelect, { name: 'categoryId', full: true }),
            field('Amount (INR)', amount, { name: 'amount', hint: 'Up to 2 decimal places.' }),
            field('Expense date', date, { name: 'expenseDate', hint: 'Cannot be in the future.' }),
            field('Description', description, { name: 'description', full: true, hint: counter })),
        errorBox,
        h('div', { class: 'row' }, submit, linkButton('Cancel', editing ? `#/expenses/${expense.id}` : '#/expenses')));

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        errorBox.replaceChildren();
        clearFieldErrors(form);
        const body = {
            categoryId: Number(categorySelect.value),
            amount: amount.value,
            expenseDate: date.value,
            description: description.value.trim(),
        };
        try {
            const saved = await busy(submit, () => editing
                ? api(`/api/expenses/${expense.id}`, { method: 'PUT', body })
                : api('/api/expenses', { method: 'POST', body }));
            toast(editing ? 'Changes saved' : 'Draft created. Next, attach a receipt and submit.', 'success');
            navigate(`/expenses/${saved.id}`);
        } catch (error) {
            showFieldErrors(form, error);
            errorBox.replaceChildren(callout('danger', null, error.message));
        }
    });

    const latestRejection = expense ? latestSteps(expense).find((step) => step.status === 'REJECTED') : null;
    return h('div', {},
        pageHeader(editing ? `Edit expense #${expense.id}` : 'New expense',
            editing ? 'Update the details, then resubmit from the expense page.' : 'Step 1 of 3 — describe the expense.',
            [], backLink(editing ? `/expenses/${expense.id}` : '/expenses', editing ? 'Back to expense' : 'My expenses')),
        h('div', { class: 'grid-2' },
            h('div', { class: 'stack' },
                latestRejection ? callout('danger', `Rejected by ${latestRejection.decidedByName}`, latestRejection.comment) : null,
                card('Expense details', form)),
            card('How it works', h('ol', { class: 'checklist' },
                checkItem('1', 'Create the draft with date, amount and category', 'ok'),
                checkItem('2', 'Attach receipts — PDF, PNG or JPEG up to 5 MB', 'todo'),
                checkItem('3', 'Submit — your manager approves first, then any extra approvers', 'todo')))));
}

// ---------- Detail ----------

export async function expenseDetailView({ params, user }) {
    const expense = await api(`/api/expenses/${params.id}`);
    const isOwner = expense.employeeId === user.id;
    const reload = () => navigate(`/expenses/${expense.id}`);

    const actions = [];
    if (expense.permissions.canDelete) {
        actions.push(button('Delete draft', async () => {
            if (await confirmDialog('Delete this draft?', 'The draft and its receipts will be removed permanently.',
                { confirmLabel: 'Delete', kind: 'btn-danger' })) {
                try {
                    await api(`/api/expenses/${expense.id}`, { method: 'DELETE' });
                    toast('Draft deleted', 'success');
                    navigate('/expenses');
                } catch (error) {
                    toast(error.message, 'error');
                }
            }
        }, 'btn-ghost-danger'));
    }
    if (expense.permissions.canEdit) {
        actions.push(linkButton('✏️ Edit', `#/expenses/${expense.id}/edit`));
        const submitButton = button(expense.status === 'REJECTED' ? '🔁 Resubmit for approval' : '🚀 Submit for approval', async () => {
            try {
                await busy(submitButton, () => api(`/api/expenses/${expense.id}/submit`, { method: 'POST' }));
                toast('Submitted. Your manager has been notified in their queue.', 'success');
                reload();
            } catch (error) {
                toast(error.message, 'error');
            }
        }, 'btn-primary');
        if (expense.receiptRequired && !expense.receipts.length) {
            submitButton.disabled = true;
            submitButton.title = 'Attach a receipt first — this category requires one';
        }
        actions.push(submitButton);
    }

    const backTarget = isOwner ? ['/expenses', 'My expenses']
        : user.role === 'FINANCE' && expense.status === 'APPROVED' && !expense.permissions.canDecide ? ['/finance', 'Reports']
            : ['/approvals', 'Approval queue'];
    highlightNav(backTarget[0]);

    return h('div', {},
        pageHeader(h('span', { class: 'row' }, `Expense #${expense.id}`, statusBadge(expense.status)),
            `${expense.categoryName} · ${format.money(expense.amount)} · ${isOwner ? 'Your expense' : `Submitted by ${expense.employeeName}`}`,
            actions, backLink(...backTarget)),
        h('div', { class: 'stack' },
            h('section', { class: 'card' }, stepper(expense)),
            statusCallout(expense, isOwner),
            expense.permissions.canDecide ? decisionCard(expense) : null,
            h('div', { class: 'grid-2' },
                h('div', { class: 'stack' }, detailsCard(expense), receiptsCard(expense, reload)),
                h('div', { class: 'stack' },
                    expense.permissions.canEdit ? checklistCard(expense) : null,
                    approvalPathCard(expense)))));
}

function stepper(expense) {
    const submitted = expense.submittedAt !== null;
    const finished = expense.status === 'APPROVED' || expense.status === 'REJECTED';
    const stages = [
        { label: 'Draft', state: 'done' },
        { label: expense.approvalSteps.some((s) => s.submissionNo > 1) ? 'Resubmitted' : 'Submitted', state: submitted ? 'done' : 'current' },
        { label: 'In approval', state: expense.status === 'SUBMITTED' ? 'current' : finished ? 'done' : '' },
        expense.status === 'REJECTED'
            ? { label: 'Rejected', state: 'failed', icon: '✕' }
            : { label: 'Approved', state: expense.status === 'APPROVED' ? 'done' : '' },
    ];
    const nodes = [];
    stages.forEach((stage, index) => {
        if (index > 0) {
            nodes.push(h('div', { class: `line${stage.state === 'done' || stage.state === 'failed' || stage.state === 'current' ? ' done' : ''}` }));
        }
        nodes.push(h('div', { class: `stage ${stage.state}` },
            h('div', { class: 'dot' }, stage.icon || (stage.state === 'done' ? '✓' : index + 1)),
            stage.label));
    });
    return h('div', { class: 'stepper' }, nodes);
}

function statusCallout(expense, isOwner) {
    const current = currentStep(expense);
    switch (expense.status) {
        case 'DRAFT':
            return isOwner ? callout('info', 'This is a draft', 'Nobody else can see it yet. Attach any required receipts, then submit it for approval.') : null;
        case 'SUBMITTED':
            return callout('warning', `Waiting for ${approverName(current)}`,
                `${STEP_LABELS[current.approverType]} — step ${current.stepOrder} of ${latestSteps(expense).length}. Submitted ${format.dateTime(expense.submittedAt)}.`);
        case 'REJECTED': {
            const rejection = latestSteps(expense).find((step) => step.status === 'REJECTED');
            return callout('danger', `Rejected by ${rejection.decidedByName} on ${format.dateTime(rejection.decidedAt)}`,
                h('div', {},
                    h('div', { class: 'strong' }, `Reason: ${rejection.comment}`),
                    isOwner ? h('div', {}, 'Edit the expense or add a receipt, then resubmit. The full history is kept.') : null));
        }
        case 'APPROVED':
            return callout('success', 'Fully approved', 'Every approver has signed off. Finance can now include this expense in reports and exports.');
        default:
            return null;
    }
}

function decisionCard(expense) {
    const step = currentStep(expense);
    const comment = h('textarea', { name: 'comment', maxlength: '500', placeholder: 'Optional when approving. Required when rejecting — tell the employee what to fix.' });
    const errorBox = h('div');
    const decide = (decision, buttonElement) => async () => {
        errorBox.replaceChildren();
        const text = comment.value.trim();
        if (decision === 'reject' && !text) {
            errorBox.replaceChildren(callout('danger', null, 'Please give a reason so the employee knows what to fix.'));
            comment.focus();
            return;
        }
        try {
            await busy(buttonElement(), () => api(`/api/approvals/${expense.id}/${decision}`, {
                method: 'POST',
                body: decision === 'reject' ? { reason: text } : { comment: text || null },
            }));
            toast(decision === 'approve' ? 'Approved' : 'Rejected — the employee can now see your reason', 'success');
            refreshQueueCount();
            navigate('/approvals');
        } catch (error) {
            errorBox.replaceChildren(callout('danger', null, error.message));
        }
    };
    let approveButton;
    let rejectButton;
    approveButton = button('✓ Approve', decide('approve', () => approveButton), 'btn-success');
    rejectButton = button('✕ Reject', decide('reject', () => rejectButton), 'btn-danger');

    return h('section', { class: 'card' },
        h('div', { class: 'card-header' },
            h('h2', {}, 'Your decision'),
            h('span', { class: 'muted' }, `${STEP_LABELS[step.approverType]} · step ${step.stepOrder} of ${latestSteps(expense).length}`)),
        h('div', { class: 'card-body form' },
            h('div', { class: 'muted' }, 'Review the details and receipts below before deciding.'),
            field('Comment', comment),
            errorBox,
            h('div', { class: 'row' }, approveButton, rejectButton)));
}

function detailsCard(expense) {
    const row = (label, value) => [h('dt', {}, label), h('dd', {}, value)];
    return card('Details', h('dl', { class: 'details' },
        row('Employee', expense.employeeName),
        row('Category', expense.categoryName),
        row('Amount', h('span', { class: 'amount' }, format.money(expense.amount))),
        row('Expense date', format.date(expense.expenseDate)),
        row('Description', expense.description),
        row('Created', format.dateTime(expense.createdAt)),
        row('Last submitted', expense.submittedAt ? format.dateTime(expense.submittedAt) : 'Not yet submitted')));
}

function receiptsCard(expense, reload) {
    const items = expense.receipts.map((receipt) => h('li', { class: 'receipt' },
        h('div', { class: 'file-icon' }, receipt.contentType === 'application/pdf' ? 'PDF' : 'IMG'),
        h('div', { class: 'info' },
            h('div', { class: 'name', title: receipt.fileName }, receipt.fileName),
            h('div', { class: 'muted small' }, `${format.fileSize(receipt.fileSizeBytes)} · uploaded ${format.dateTime(receipt.uploadedAt)}`)),
        button('View', () => previewReceipt(expense, receipt).catch((error) => toast(error.message, 'error')), 'btn-sm'),
        button('Download', () => downloadFile(receiptUrl(expense, receipt), {}, receipt.fileName)
            .catch((error) => toast(error.message, 'error')), 'btn-sm'),
        expense.permissions.canDelete ? button('Remove', async () => {
            if (await confirmDialog('Remove receipt?', `${receipt.fileName} will be deleted.`, { confirmLabel: 'Remove', kind: 'btn-danger' })) {
                try {
                    await api(receiptUrl(expense, receipt), { method: 'DELETE' });
                    toast('Receipt removed', 'success');
                } catch (error) {
                    toast(error.message, 'error');
                }
                reload();
            }
        }, 'btn-sm btn-ghost-danger') : null));

    const body = h('div', {},
        items.length ? h('ul', { class: 'receipts' }, items)
            : h('div', { class: 'muted' }, expense.receiptRequired ? 'No receipts yet. This category requires at least one.' : 'No receipts attached. They are optional for this category.'),
        expense.permissions.canEdit ? uploadZone(expense, reload) : null);
    return card(`Receipts (${expense.receipts.length})`, body);
}

function uploadZone(expense, reload) {
    const input = h('input', { type: 'file', accept: '.pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg' });
    const label = h('div', {}, h('div', { class: 'strong' }, '📎 Click or drop a receipt here'), h('div', { class: 'small' }, 'PDF, PNG or JPEG · max 5 MB'));
    const zone = h('label', { class: 'dropzone' }, input, label);

    const upload = async (file) => {
        if (!file) return;
        if (!RECEIPT_TYPES.includes(file.type)) {
            toast('Only PDF, PNG or JPEG files are accepted', 'error');
            return;
        }
        if (file.size > MAX_RECEIPT_BYTES) {
            toast('The file is larger than 5 MB', 'error');
            return;
        }
        const form = new FormData();
        form.append('file', file);
        label.replaceChildren(h('div', { class: 'strong' }, `Uploading ${file.name}…`));
        try {
            await api(`/api/expenses/${expense.id}/receipts`, { method: 'POST', form });
            toast('Receipt attached', 'success');
            reload();
        } catch (error) {
            toast(error.message, 'error');
            reload();
        }
    };
    input.addEventListener('change', () => upload(input.files[0]));
    zone.addEventListener('dragover', (event) => {
        event.preventDefault();
        zone.classList.add('dragging');
    });
    zone.addEventListener('dragleave', () => zone.classList.remove('dragging'));
    zone.addEventListener('drop', (event) => {
        event.preventDefault();
        zone.classList.remove('dragging');
        upload(event.dataTransfer.files[0]);
    });
    return zone;
}

async function previewReceipt(expense, receipt) {
    const { blob } = await fetchBlob(receiptUrl(expense, receipt));
    const url = URL.createObjectURL(blob);
    const content = receipt.contentType === 'application/pdf'
        ? h('iframe', { class: 'preview-frame', src: url, title: receipt.fileName })
        : h('img', { class: 'preview-image', src: url, alt: receipt.fileName });
    modal({
        title: receipt.fileName,
        wide: true,
        body: content,
        onClose: () => URL.revokeObjectURL(url),
        actions: [button('Download', () => downloadFile(receiptUrl(expense, receipt), {}, receipt.fileName), 'btn-primary')],
    });
}

function checklistCard(expense) {
    const hasReceipt = expense.receipts.length > 0;
    const receiptState = hasReceipt ? 'ok' : expense.receiptRequired ? 'todo' : 'ok';
    const ready = hasReceipt || !expense.receiptRequired;
    return card('Ready to submit?', h('div', { class: 'stack' },
        h('ul', { class: 'checklist' },
            checkItem('✓', 'Details entered', 'ok'),
            checkItem(receiptState === 'ok' ? '✓' : '!', hasReceipt ? `${expense.receipts.length} receipt(s) attached`
                : expense.receiptRequired ? 'Receipt required for this category' : 'Receipt optional for this category', receiptState),
            checkItem(ready ? '→' : '…', ready ? 'Click "Submit for approval" above' : 'Attach a receipt to enable submission', ready ? 'todo' : '')),
        h('div', { class: 'muted small' }, 'After you submit, the expense is locked until a reviewer approves or rejects it.')));
}

function approvalPathCard(expense) {
    if (!expense.approvalSteps.length) {
        return card('Approval path', emptyState('🔀', 'Not submitted yet',
            'When you submit, your manager is always the first approver. Company rules may add finance or a named approver after them.'));
    }
    const latest = latestSteps(expense);
    const current = currentStep(expense);
    const older = groupBySubmission(expense.approvalSteps.filter((step) => step.submissionNo !== latest[0].submissionNo));

    return card('Approval path', h('div', {},
        latest[0].submissionNo > 1 ? h('div', { class: 'muted small', }, `Submission #${latest[0].submissionNo}`) : null,
        timeline(latest, current),
        older.length ? h('details', { class: 'history' },
            h('summary', {}, `Earlier submissions (${older.length})`),
            older.map(([number, steps]) => h('div', {},
                h('div', { class: 'muted small strong' }, `Submission #${number}`),
                timeline(steps, null)))) : null));
}

function timeline(steps, current) {
    return h('ol', { class: 'timeline' }, steps.map((step) => {
        const isCurrent = current && step === current;
        const state = isCurrent ? 'current' : step.status.toLowerCase();
        const marker = { approved: '✓', rejected: '✕', skipped: '–', current: '…' }[state] || step.stepOrder;
        let meta;
        if (step.decidedAt) {
            meta = `${step.status === 'APPROVED' ? 'Approved' : 'Rejected'} by ${step.decidedByName} · ${format.dateTime(step.decidedAt)}`;
        } else if (isCurrent) {
            meta = `Waiting for ${approverName(step)}`;
        } else if (step.status === 'SKIPPED') {
            meta = 'Skipped because an earlier step rejected the expense';
        } else {
            meta = `Will go to ${approverName(step)} after the previous step`;
        }
        return h('li', { class: state },
            h('div', { class: 'marker' }, marker),
            h('div', { class: 'title' }, STEP_LABELS[step.approverType],
                step.status === 'PENDING' && !isCurrent ? h('span', { class: 'badge skipped' }, 'Upcoming') : statusBadge(step.status)),
            h('div', { class: 'meta' }, step.assignedApproverName ? `Approver: ${step.assignedApproverName}` : 'Any member of the finance team'),
            h('div', { class: 'meta' }, meta),
            step.comment ? h('div', { class: 'comment' }, `“${step.comment}”`) : null);
    }));
}

// ---------- Helpers ----------

function latestSteps(expense) {
    const latest = Math.max(0, ...expense.approvalSteps.map((step) => step.submissionNo));
    return expense.approvalSteps.filter((step) => step.submissionNo === latest);
}

function currentStep(expense) {
    if (expense.status !== 'SUBMITTED') return null;
    return latestSteps(expense).find((step) => step.status === 'PENDING') || null;
}

function groupBySubmission(steps) {
    const groups = new Map();
    steps.forEach((step) => groups.set(step.submissionNo, [...(groups.get(step.submissionNo) || []), step]));
    return [...groups.entries()].sort((a, b) => b[0] - a[0]);
}

function approverName(step) {
    return step?.assignedApproverName || 'the finance team';
}

function receiptUrl(expense, receipt) {
    return `/api/expenses/${expense.id}/receipts/${receipt.id}`;
}

function checkItem(mark, text, state) {
    return h('li', { class: state }, h('span', { class: 'tick' }, mark), h('span', {}, text));
}

export function backLink(path, label) {
    return h('div', { class: 'breadcrumb' }, h('a', { href: `#${path}` }, `← ${label}`));
}
