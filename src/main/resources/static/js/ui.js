// Elements are always built with text nodes, never innerHTML, so user data cannot inject markup.
export function h(tag, attributes, ...children) {
    const element = document.createElement(tag);
    for (const [name, value] of Object.entries(attributes || {})) {
        if (value === null || value === undefined || value === false) {
            continue;
        }
        if (name === 'class') {
            element.className = value;
        } else if (name.startsWith('on') && typeof value === 'function') {
            element.addEventListener(name.slice(2).toLowerCase(), value);
        } else if (name === 'value') {
            element.value = value;
        } else if (value === true) {
            element.setAttribute(name, '');
        } else {
            element.setAttribute(name, value);
        }
    }
    append(element, children);
    return element;
}

export function append(parent, children) {
    for (const child of [children].flat(Infinity)) {
        if (child === null || child === undefined || child === false) {
            continue;
        }
        parent.append(child instanceof Node ? child : String(child));
    }
    return parent;
}

// ---------- Formatting ----------

const moneyFormat = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' });
const dateFormat = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
const dateTimeFormat = new Intl.DateTimeFormat('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
});

export const format = {
    money: (value) => (value === null || value === undefined ? '—' : moneyFormat.format(Number(value))),
    date: (value) => (value ? dateFormat.format(new Date(`${value}T00:00:00`)) : '—'),
    dateTime: (value) => (value ? dateTimeFormat.format(new Date(value)) : '—'),
    fileSize: (bytes) => {
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
    },
    title: (value) => (value ? value.charAt(0) + value.slice(1).toLowerCase().replaceAll('_', ' ') : ''),
};

export function today() {
    const now = new Date();
    const offset = now.getTimezoneOffset() * 60_000;
    return new Date(now - offset).toISOString().slice(0, 10);
}

// ---------- Building blocks ----------

const STATUS_LABELS = {
    DRAFT: 'Draft',
    SUBMITTED: 'In approval',
    APPROVED: 'Approved',
    REJECTED: 'Rejected',
    PENDING: 'Pending',
    SKIPPED: 'Skipped',
};

export function statusBadge(status) {
    return h('span', { class: `badge ${status.toLowerCase()}` }, STATUS_LABELS[status] || format.title(status));
}

export function badge(text, kind = '') {
    return h('span', { class: `badge plain ${kind}` }, text);
}

export function pageHeader(title, subtitle, actions = [], breadcrumb = null) {
    return h('div', { class: 'page-header' },
        h('div', {},
            breadcrumb,
            h('h1', {}, title),
            subtitle ? h('div', { class: 'subtitle' }, subtitle) : null),
        h('div', { class: 'actions' }, actions));
}

export function card(title, body, headerActions = null) {
    return h('section', { class: 'card' },
        title ? h('div', { class: 'card-header' }, h('h2', {}, title), headerActions) : null,
        h('div', { class: 'card-body' }, body));
}

export function stat(label, value, sub, options = {}) {
    return h('div', {
        class: `stat${options.onClick ? ' clickable' : ''}${options.selected ? ' selected' : ''}`,
        onclick: options.onClick,
    }, h('div', { class: 'label' }, label), h('div', { class: 'value' }, value), sub ? h('div', { class: 'sub' }, sub) : null);
}

export function emptyState(icon, title, message, actions = []) {
    return h('div', { class: 'empty' },
        h('div', { class: 'icon' }, icon),
        h('div', { class: 'title' }, title),
        message ? h('div', {}, message) : null,
        actions.length ? h('div', { class: 'actions' }, actions) : null);
}

export function loading() {
    return h('div', { class: 'loading' }, h('div', { class: 'spinner' }));
}

export function callout(kind, title, message) {
    return h('div', { class: `callout ${kind}` },
        h('div', {}, title ? h('div', { class: 'title' }, title) : null, message));
}

export function button(label, onClick, kind = '', extra = {}) {
    return h('button', { type: 'button', class: kind, onclick: onClick, ...extra }, label);
}

export function linkButton(label, href, kind = '') {
    return h('a', { class: `btn ${kind}`, href }, label);
}

export function table(columns, rows, { onRowClick, empty } = {}) {
    if (!rows.length) {
        return empty || emptyState('📭', 'Nothing here yet');
    }
    return h('div', { class: 'table-wrap' },
        h('table', {},
            h('thead', {}, h('tr', {}, columns.map((column) =>
                h('th', { class: column.numeric ? 'num' : '' }, column.label)))),
            h('tbody', {}, rows.map((row) => h('tr', {
                class: onRowClick ? 'clickable' : '',
                onclick: onRowClick ? (event) => {
                    if (!event.target.closest('button, a')) onRowClick(row);
                } : null,
            }, columns.map((column) => h('td', { class: column.numeric ? 'num' : '' }, column.render(row))))))));
}

// ---------- Forms ----------

export function field(label, control, { hint, full = false, name } = {}) {
    const id = control.id || `f-${Math.random().toString(36).slice(2, 9)}`;
    control.id = id;
    return h('div', { class: `field${full ? ' full' : ''}`, 'data-field': name || control.name || null },
        h('label', { for: id }, label),
        control,
        hint ? h('div', { class: 'hint' }, hint) : null);
}

export function select(name, options, selected, { placeholder, required = false } = {}) {
    return h('select', { name, required },
        placeholder !== undefined ? h('option', { value: '' }, placeholder) : null,
        options.map((option) => h('option', {
            value: String(option.value),
            selected: String(option.value) === String(selected ?? ''),
        }, option.label)));
}

export function showFieldErrors(form, error) {
    clearFieldErrors(form);
    for (const [name, message] of Object.entries(error.fieldErrors || {})) {
        const container = form.querySelector(`[data-field="${CSS.escape(name)}"]`);
        if (container) {
            container.classList.add('invalid');
            container.append(h('div', { class: 'error' }, message));
        }
    }
}

export function clearFieldErrors(form) {
    form.querySelectorAll('.field.invalid').forEach((element) => element.classList.remove('invalid'));
    form.querySelectorAll('.field .error').forEach((element) => element.remove());
}

// Disables the button while the action runs so a double click cannot submit twice.
export async function busy(buttonElement, action) {
    const original = buttonElement.textContent;
    buttonElement.disabled = true;
    buttonElement.textContent = 'Working…';
    try {
        return await action();
    } finally {
        buttonElement.disabled = false;
        buttonElement.textContent = original;
    }
}

// ---------- Feedback ----------

export function toast(message, kind = 'info') {
    const element = h('div', { class: `toast ${kind}`, role: 'status' }, message);
    document.getElementById('toasts').append(element);
    setTimeout(() => element.remove(), kind === 'error' ? 6000 : 3500);
}

export function modal({ title, body, actions = [], wide = false, onClose }) {
    const close = () => {
        backdrop.remove();
        document.removeEventListener('keydown', onKey);
        onClose?.();
    };
    const onKey = (event) => {
        if (event.key === 'Escape') close();
    };
    const backdrop = h('div', {
        class: 'modal-backdrop',
        onclick: (event) => {
            if (event.target === backdrop) close();
        },
    }, h('div', { class: `modal${wide ? ' wide' : ''}`, role: 'dialog', 'aria-modal': 'true' },
        h('div', { class: 'modal-header' }, h('h2', {}, title), button('×', close, '', { 'aria-label': 'Close' })),
        h('div', { class: 'modal-body' }, body),
        actions.length ? h('div', { class: 'modal-footer' }, actions) : null));
    document.addEventListener('keydown', onKey);
    document.body.append(backdrop);
    backdrop.querySelector('input, select, textarea')?.focus();
    return close;
}

export function confirmDialog(title, message, { confirmLabel = 'Confirm', kind = 'btn-primary' } = {}) {
    return new Promise((resolve) => {
        let settled = false;
        const finish = (value) => {
            if (!settled) {
                settled = true;
                resolve(value);
            }
        };
        const close = modal({
            title,
            body: h('p', {}, message),
            onClose: () => finish(false),
            actions: [
                button('Cancel', () => close()),
                button(confirmLabel, () => {
                    finish(true);
                    close();
                }, kind),
            ],
        });
    });
}

// Opens a form in a modal; onSubmit may throw ApiError to show field errors without closing.
export function formDialog({ title, fields, submitLabel = 'Save', onSubmit, wide = false }) {
    const form = h('form', { class: 'form' }, fields);
    const submit = h('button', { type: 'submit', class: 'btn-primary' }, submitLabel);
    const errorBox = h('div');
    form.append(errorBox);
    const close = modal({ title, wide, body: form, actions: [button('Cancel', () => close()), submit] });
    submit.setAttribute('form', form.id = `form-${Math.random().toString(36).slice(2, 9)}`);
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        errorBox.replaceChildren();
        clearFieldErrors(form);
        try {
            await busy(submit, () => onSubmit(new FormData(form), form));
            close();
        } catch (error) {
            showFieldErrors(form, error);
            errorBox.replaceChildren(callout('danger', null, error.message));
        }
    });
    return close;
}

export function emptyToNull(value) {
    return value === null || value === undefined || String(value).trim() === '' ? null : value;
}
