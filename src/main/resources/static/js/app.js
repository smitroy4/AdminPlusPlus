/* ==========================================================================
   app.js - shared utilities: fetch wrappers, CSRF, formatting, escaping
   Everything is exposed on the global `App` object. No frameworks, no build.
   ========================================================================== */
'use strict';

const App = (function () {

    const CSRF_COOKIE = 'XSRF-TOKEN';
    const CSRF_HEADER = 'X-XSRF-TOKEN';
    const LOGIN_URL = '/login.html';

    let csrfToken = null;
    let csrfPromise = null;

    /* ------------------------------------------------------------- cookies */

    function readCookie(name) {
        const target = name + '=';
        const parts = document.cookie ? document.cookie.split(';') : [];
        for (const part of parts) {
            const cookie = part.trim();
            if (cookie.indexOf(target) === 0) {
                return decodeURIComponent(cookie.substring(target.length));
            }
        }
        return null;
    }

    /* ---------------------------------------------------------------- CSRF */

    /**
     * Returns the CSRF token, fetching it from /api/csrf once if the cookie is
     * not there yet (the server writes the cookie on that very response).
     */
    async function getCsrfToken(forceRefresh) {
        if (forceRefresh) {
            csrfToken = null;
            csrfPromise = null;
        }
        if (csrfToken) {
            return csrfToken;
        }
        if (csrfPromise) {
            return csrfPromise;
        }

        csrfPromise = (async () => {
            const fromCookie = readCookie(CSRF_COOKIE);
            if (fromCookie) {
                csrfToken = fromCookie;
                return csrfToken;
            }
            const response = await fetch('/api/csrf', {
                method: 'GET',
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json' }
            });
            if (response.ok) {
                const body = await response.json();
                csrfToken = body.data && body.data.token ? body.data.token : readCookie(CSRF_COOKIE);
            }
            if (!csrfToken) {
                csrfToken = readCookie(CSRF_COOKIE);
            }
            if (!csrfToken) {
                throw new Error('Unable to obtain a CSRF token');
            }
            return csrfToken;
        })();

        try {
            return await csrfPromise;
        } finally {
            csrfPromise = null;
        }
    }

    /** Fills a hidden `_csrf` input of a plain HTML form. */
    async function applyCsrfToForm(form) {
        const input = form.querySelector('input[name="_csrf"]');
        if (!input) {
            return;
        }
        try {
            input.value = await getCsrfToken();
        } catch (ignored) {
            /* the form will simply be rejected by the server */
        }
    }

    /* --------------------------------------------------------------- fetch */

    function toUrlSearch(params) {
        const search = new URLSearchParams();
        Object.keys(params || {}).forEach((key) => {
            const value = params[key];
            if (value !== null && value !== undefined && value !== '') {
                search.append(key, value);
            }
        });
        const query = search.toString();
        return query ? '?' + query : '';
    }

    function goToLogin() {
        if (!window.location.pathname.endsWith(LOGIN_URL)) {
            const next = encodeURIComponent(window.location.pathname + window.location.search);
            window.location.href = LOGIN_URL + '?next=' + next;
        }
    }

    async function request(method, url, body, options) {
        const opts = options || {};
        const mutating = method !== 'GET' && method !== 'HEAD';

        let response = await send(method, url, body, opts, mutating && !opts.skipCsrf);

        // The server rotates the token on login / logout, and the cookie can also
        // disappear between two calls. A CSRF rejection is therefore recoverable:
        // fetch a fresh token and replay the request exactly once.
        if (response.status === 403 && mutating && !opts.skipCsrf && !opts.retried) {
            response = await send(method, url, body, opts, true, true);
        }

        if (response.status === 401 && !opts.allowUnauthenticated) {
            goToLogin();
            throw new ApiError('Your session has expired. Please sign in again.', 401, null);
        }
        if (response.status === 403 && !opts.redirectOnForbidden) {
            throw new ApiError('You do not have permission to perform this action.', 403, null);
        }

        if (response.status === 204) {
            return { success: true, message: 'OK', data: null };
        }

        let parsed = null;
        const text = await response.text();
        if (text) {
            try {
                parsed = JSON.parse(text);
            } catch (ignored) {
                parsed = { success: false, message: text };
            }
        }

        if (!response.ok) {
            const message = (parsed && parsed.message) || ('Request failed with status ' + response.status);
            throw new ApiError(message, response.status, parsed);
        }
        return parsed || { success: true, message: 'OK', data: null };
    }

    async function send(method, url, body, opts, withCsrf, forceRefresh) {
        const headers = { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' };

        let payload;
        if (body !== undefined && body !== null) {
            headers['Content-Type'] = 'application/json';
            payload = JSON.stringify(body);
        }
        if (withCsrf) {
            headers[CSRF_HEADER] = await getCsrfToken(forceRefresh);
        }

        return fetch(url, {
            method: method,
            headers: headers,
            body: payload,
            credentials: 'same-origin',
            cache: 'no-store'
        });
    }

    const getJson = (url, options) => request('GET', url, null, options);
    const postJson = (url, body, options) => request('POST', url, body, options);
    const putJson = (url, body, options) => request('PUT', url, body, options);
    const patchJson = (url, body, options) => request('PATCH', url, body, options);
    const delJson = (url, options) => request('DELETE', url, null, options);

    /** Normalised error carrying the HTTP status so callers can branch on it. */
    class ApiError extends Error {
        constructor(message, status, payload) {
            super(message);
            this.name = 'ApiError';
            this.status = status;
            this.payload = payload;
        }
    }

    /* ---------------------------------------------------------- formatting */

    function formatDate(value, withTime) {
        if (!value) {
            return '—';
        }
        const date = new Date(value);
        if (isNaN(date.getTime())) {
            return '—';
        }
        return date.toLocaleString(undefined, {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        });
    }

    function formatRelative(value) {
        if (!value) {
            return '—';
        }
        const date = new Date(value);
        if (isNaN(date.getTime())) {
            return '—';
        }
        const diffSeconds = Math.round((Date.now() - date.getTime()) / 1000);
        const future = diffSeconds < 0;
        const abs = Math.abs(diffSeconds);

        const units = [[60, 'second'], [3600, 'minute'], [86400, 'hour'], [2592000, 'day']];
        if (abs < 60) {
            return future ? 'in a moment' : 'just now';
        }
        for (let i = 0; i < units.length; i++) {
            const limit = units[i][0];
            if (abs < limit) {
                const previous = i === 0 ? 1 : units[i - 1][0];
                const amount = Math.round(abs / previous);
                const label = units[i][1] + (amount === 1 ? '' : 's');
                return future ? ('in ' + amount + ' ' + label) : (amount + ' ' + label + ' ago');
            }
        }
        return formatDate(value);
    }

    const STATUS_LABELS = {
        OPEN: 'Open',
        IN_PROGRESS: 'In Progress',
        COMPLETED: 'Completed',
        CLOSED: 'Closed'
    };

    const PRIORITY_LABELS = {
        LOW: 'Low',
        MEDIUM: 'Medium',
        HIGH: 'High',
        URGENT: 'Urgent'
    };

    function statusLabel(value) {
        return STATUS_LABELS[value] || value || '—';
    }

    function priorityLabel(value) {
        return PRIORITY_LABELS[value] || value || '—';
    }

    /* ------------------------------------------------------------- escaping */

    /**
     * HTML-escapes a value. Every piece of user supplied data goes through this
     * before being interpolated into innerHTML - the SPA has no other XSS
     * defence and trusts nothing from the API.
     */
    function esc(value) {
        if (value === null || value === undefined) {
            return '';
        }
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /** Escapes and turns newlines into <br> for multi-line message bodies. */
    function escMultiline(value) {
        return esc(value).replace(/\r?\n/g, '<br>');
    }

    function initials(name) {
        if (!name) {
            return '?';
        }
        const parts = String(name).trim().split(/[\s._-]+/).filter(Boolean);
        const letters = parts.slice(0, 2).map((part) => part.charAt(0).toUpperCase());
        return letters.join('') || '?';
    }

    /* -------------------------------------------------------------- markup */

    function statusBadge(status) {
        const safe = esc(status || 'UNKNOWN');
        return '<span class="badge badge--status-' + safe + '">' + esc(statusLabel(status)) + '</span>';
    }

    function priorityBadge(priority) {
        const safe = esc(priority || 'UNKNOWN');
        return '<span class="badge badge--priority-' + safe + '">' + esc(priorityLabel(priority)) + '</span>';
    }

    function userCell(user) {
        if (!user) {
            return '<span class="cell-unassigned">Unassigned</span>';
        }
        return '<span class="cell-user"><span class="avatar">' + esc(initials(user.username))
            + '</span>' + esc(user.username) + '</span>';
    }

    /** The customer a task belongs to; "Internal" when it has none. */
    function clientCell(client) {
        if (!client) {
            return '<span class="cell-unassigned">Internal</span>';
        }
        return '<span class="cell-client">' + esc(client.name) + '</span>';
    }

    function emptyState(title, subtitle) {
        return '<div class="empty-state"><div class="empty-state__title">' + esc(title) + '</div>'
            + (subtitle ? '<div>' + esc(subtitle) + '</div>' : '') + '</div>';
    }

    /* -------------------------------------------------------------- toasts */

    function toastHost() {
        let host = document.querySelector('.toast-host');
        if (!host) {
            host = document.createElement('div');
            host.className = 'toast-host';
            document.body.appendChild(host);
        }
        return host;
    }

    function toast(message, type, duration) {
        const node = document.createElement('div');
        node.className = 'toast' + (type ? ' toast--' + type : '');
        node.setAttribute('role', 'status');
        node.textContent = message;
        toastHost().appendChild(node);
        window.setTimeout(() => {
            node.style.opacity = '0';
            node.style.transition = 'opacity .25s';
            window.setTimeout(() => node.remove(), 250);
        }, duration || 3800);
    }

    /* ---------------------------------------------------------------- theme */

    const THEME_KEY = 'admin++-theme';

    function currentTheme() {
        try {
            return window.localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light';
        } catch (ignored) {
            return 'light';
        }
    }

    function applyTheme(theme) {
        if (theme === 'dark') {
            document.documentElement.setAttribute('data-theme', 'dark');
        } else {
            document.documentElement.removeAttribute('data-theme');
        }
    }

    /** Restores the saved preference; light is the default. */
    function applyStoredTheme() {
        applyTheme(currentTheme());
    }

    function setTheme(theme) {
        try {
            window.localStorage.setItem(THEME_KEY, theme);
        } catch (ignored) {
            /* private mode: the choice simply does not persist */
        }
        applyTheme(theme);
        refreshThemeToggle();
    }

    /** Flips between light (default) and dark. */
    function toggleTheme() {
        setTheme(currentTheme() === 'dark' ? 'light' : 'dark');
    }

    function refreshThemeToggle() {
        const button = document.querySelector('[data-theme-toggle]');
        if (button) {
            const dark = currentTheme() === 'dark';
            button.textContent = dark ? '\u2600\uFE0E' : '\u263D';
            button.setAttribute('aria-label', dark ? 'Switch to light mode' : 'Switch to dark mode');
            button.setAttribute('title', dark ? 'Switch to light mode' : 'Switch to dark mode');
        }
    }

    /**
     * Places the light/dark switch into the page chrome: the header on
     * authenticated pages, the brand row on the login card elsewhere.
     */
    function mountThemeToggle() {
        if (document.querySelector('[data-theme-toggle]')) {
            return;
        }
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'theme-toggle';
        button.setAttribute('data-theme-toggle', '');
        button.addEventListener('click', toggleTheme);

        const headerRight = document.querySelector('.app-header__right');
        const loginBrand = document.querySelector('.login-card__brand');
        if (headerRight) {
            headerRight.insertBefore(button, headerRight.firstChild);
        } else if (loginBrand) {
            loginBrand.appendChild(button);
        } else {
            document.body.appendChild(button);
        }
        refreshThemeToggle();
    }

    /* ------------------------------------------------------------ elements */

    const $ = (selector, root) => (root || document).querySelector(selector);
    const $$ = (selector, root) => Array.from((root || document).querySelectorAll(selector));

    function on(selector, event, handler, root) {
        const node = $(selector, root);
        if (node) {
            node.addEventListener(event, handler);
        }
        return node;
    }

    function showAlert(selector, message, type) {
        const node = $(selector);
        if (!node) {
            return;
        }
        node.className = 'alert is-visible alert--' + (type || 'error');
        node.textContent = message;
    }

    function hideAlert(selector) {
        const node = $(selector);
        if (node) {
            node.className = 'alert';
            node.textContent = '';
        }
    }

    /** Toggles a button into a busy state and returns the original label. */
    function busy(button, isBusy, label) {
        if (!button) {
            return;
        }
        if (isBusy) {
            button.dataset.label = button.innerHTML;
            button.disabled = true;
            button.innerHTML = '<span class="spinner"></span> ' + esc(label || 'Working…');
        } else {
            button.disabled = false;
            if (button.dataset.label) {
                button.innerHTML = button.dataset.label;
                delete button.dataset.label;
            }
        }
    }

    /**
     * Client-side sortable table. Pass a config map of column key -> accessor.
     * `initialDirection` (optional, 'asc' | 'desc') seeds the first ordering.
     */
    function makeSortable(table, rows, render, accessors, initialKey, initialDirection) {
        let sortKey = initialKey || null;
        let direction = initialDirection === 'desc' ? 'desc' : 'asc';

        const headers = $$('thead th', table);
        headers.forEach((th) => {
            const key = th.dataset.sortKey;
            if (!key) {
                return;
            }
            th.classList.add('is-sortable');
            th.setAttribute('aria-sort', 'none');
            th.addEventListener('click', () => {
                if (sortKey === key) {
                    direction = direction === 'asc' ? 'desc' : 'asc';
                } else {
                    sortKey = key;
                    direction = 'asc';
                }
                apply();
            });
        });

        function sorted() {
            if (!sortKey) {
                return rows.slice();
            }
            const accessor = accessors[sortKey] || ((row) => row[sortKey]);
            const factor = direction === 'asc' ? 1 : -1;
            return rows.slice().sort((left, right) => {
                const a = accessor(left);
                const b = accessor(right);
                if (a === null || a === undefined || a === '') {
                    return b === null || b === undefined || b === '' ? 0 : 1;
                }
                if (b === null || b === undefined || b === '') {
                    return -1;
                }
                if (typeof a === 'number' && typeof b === 'number') {
                    return (a - b) * factor;
                }
                if (typeof a === 'boolean' || typeof b === 'boolean') {
                    return (Number(a) - Number(b)) * factor;
                }
                return String(a).localeCompare(String(b), undefined, { numeric: true }) * factor;
            });
        }

        function apply() {
            headers.forEach((th) => {
                const caret = th.querySelector('.sort-caret');
                if (!caret) {
                    return;
                }
                if (th.dataset.sortKey === sortKey) {
                    th.classList.add('is-sorted');
                    th.setAttribute('aria-sort', direction === 'asc' ? 'ascending' : 'descending');
                    caret.textContent = direction === 'asc' ? '▲' : '▼';
                } else {
                    th.classList.remove('is-sorted');
                    th.setAttribute('aria-sort', 'none');
                    caret.textContent = '▲';
                }
            });
            render(sorted());
        }

        apply();
        return { refresh: apply };
    }

    return {
        CSRF_HEADER,
        LOGIN_URL,
        ApiError,
        getJson,
        postJson,
        putJson,
        patchJson,
        delJson,
        toUrlSearch,
        goToLogin,
        getCsrfToken,
        applyCsrfToForm,
        readCookie,
        formatDate,
        formatRelative,
        statusLabel,
        priorityLabel,
        statusBadge,
        priorityBadge,
        userCell,
        clientCell,
        initials,
        emptyState,
        esc,
        escMultiline,
        toast,
        $,
        $$,
        on,
        showAlert,
        hideAlert,
        busy,
        makeSortable,
        currentTheme,
        setTheme,
        toggleTheme,
        applyStoredTheme,
        mountThemeToggle
    };
})();

/* Applies the stored preference (default: light) and shows the switch. */
document.addEventListener('DOMContentLoaded', () => {
    App.applyStoredTheme();
    App.mountThemeToggle();
});
