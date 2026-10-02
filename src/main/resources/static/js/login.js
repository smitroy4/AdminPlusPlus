/* ==========================================================================
   login.js - posts the credentials to /login and reports the result inline.
   Falls back to a plain form submit if fetch is unavailable.
   ========================================================================== */
'use strict';

document.addEventListener('DOMContentLoaded', async () => {
    const form = App.$('#login-form');
    const username = App.$('#username');
    const password = App.$('#password');
    const submit = App.$('#login-submit');
    const alertBox = App.$('#login-alert');

    function showError(message) {
        alertBox.className = 'alert is-visible alert--error';
        alertBox.textContent = message;
    }

    function showSuccess(message) {
        alertBox.className = 'alert is-visible alert--success';
        alertBox.textContent = message;
    }

    /* Already signed in? Go straight to the target page. */
    try {
        const me = await App.getJson('/api/me', { allowUnauthenticated: true });
        if (me.success) {
            window.location.replace('/dashboard.html');
            return;
        }
    } catch (ignored) {
        /* not authenticated - normal case for this page */
    }

    const params = new URLSearchParams(window.location.search);
    if (params.get('logout')) {
        showSuccess('You have been signed out successfully.');
    } else if (params.get('error')) {
        showError('Invalid username or password. Please try again.');
    }
    if (params.get('next')) {
        const next = App.$('#next');
        if (next) {
            next.value = params.get('next');
        }
    }

    await App.applyCsrfToForm(form);
    if (username) {
        username.focus();
    }

    App.$$('[data-demo-user]').forEach((button) => {
        button.addEventListener('click', () => {
            username.value = button.dataset.demoUser;
            password.value = button.dataset.demoPass;
            password.focus();
        });
    });

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        App.hideAlert('#login-alert');

        if (!username.value.trim() || !password.value) {
            showError('Please enter both a username and a password.');
            return;
        }

        App.busy(submit, true, 'Signing in…');
        try {
            await App.getCsrfToken(true);
            const token = await App.getCsrfToken();

            const body = new URLSearchParams();
            body.append('username', username.value.trim());
            body.append('password', password.value);
            body.append('_csrf', token);
            const next = App.$('#next');
            if (next && next.value) {
                body.append('next', next.value);
            }

            const response = await fetch('/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'XMLHttpRequest',
                    'Accept': 'application/json'
                },
                body: body.toString(),
                credentials: 'same-origin',
                redirect: 'follow'
            });

            if (response.ok) {
                const nextParam = App.$('#next');
                window.location.replace(nextParam && nextParam.value ? nextParam.value : '/dashboard.html');
                return;
            }

            let message = 'Invalid username or password. Please try again.';
            try {
                const payload = await response.json();
                if (payload && payload.message) {
                    message = payload.message;
                }
            } catch (ignored) {
                /* non JSON error body - keep the default message */
            }
            showError(message);
            password.value = '';
            password.focus();
        } catch (error) {
            showError('Unable to reach the server. Please try again.');
        } finally {
            App.busy(submit, false);
        }
    });
});
