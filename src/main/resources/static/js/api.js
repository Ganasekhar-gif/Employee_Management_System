// HTTP Basic credentials live in sessionStorage, so they are dropped when the tab closes.
const CREDENTIALS_KEY = 'ems.credentials';

let unauthorizedHandler = () => {};

export class ApiError extends Error {
    constructor(status, message, fieldErrors = {}) {
        super(message);
        this.status = status;
        this.fieldErrors = fieldErrors;
    }

    static async from(response) {
        let problem = {};
        try {
            problem = await response.json();
        } catch {
            // Not every error carries a JSON body.
        }
        const message = problem.detail || problem.title || `Request failed (${response.status})`;
        return new ApiError(response.status, message, problem.errors || {});
    }
}

export function onUnauthorized(handler) {
    unauthorizedHandler = handler;
}

export function hasCredentials() {
    return sessionStorage.getItem(CREDENTIALS_KEY) !== null;
}

export function saveCredentials(email, password) {
    const bytes = new TextEncoder().encode(`${email}:${password}`);
    sessionStorage.setItem(CREDENTIALS_KEY, btoa(String.fromCharCode(...bytes)));
}

export function clearCredentials() {
    sessionStorage.removeItem(CREDENTIALS_KEY);
}

async function send(path, { method = 'GET', body, form, query } = {}) {
    const headers = { 'X-Requested-With': 'XMLHttpRequest', Accept: 'application/json' };
    const credentials = sessionStorage.getItem(CREDENTIALS_KEY);
    if (credentials) {
        headers.Authorization = `Basic ${credentials}`;
    }
    let payload;
    if (form) {
        payload = form;
    } else if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
        payload = JSON.stringify(body);
    }

    const response = await fetch(path + toQueryString(query), { method, headers, body: payload });
    if (response.status === 401) {
        unauthorizedHandler();
    }
    if (!response.ok) {
        throw await ApiError.from(response);
    }
    return response;
}

export async function api(path, options) {
    const response = await send(path, options);
    if (response.status === 204) {
        return null;
    }
    const type = response.headers.get('Content-Type') || '';
    return type.includes('json') ? response.json() : response.text();
}

export async function fetchBlob(path, options) {
    const response = await send(path, options);
    return { blob: await response.blob(), fileName: fileNameFrom(response) };
}

export async function downloadFile(path, options, fallbackName = 'download') {
    const { blob, fileName } = await fetchBlob(path, options);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName || fallbackName;
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

function toQueryString(query) {
    if (!query) {
        return '';
    }
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(query)) {
        if (value !== undefined && value !== null && value !== '') {
            params.set(key, value);
        }
    }
    const text = params.toString();
    return text ? `?${text}` : '';
}

function fileNameFrom(response) {
    const disposition = response.headers.get('Content-Disposition') || '';
    const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
    if (encoded) {
        return decodeURIComponent(encoded[1]);
    }
    const plain = /filename="?([^";]+)"?/i.exec(disposition);
    return plain ? plain[1] : null;
}
