/* ==========================================================================
   profile.js - the signed-in user edits their own details and password.
   ========================================================================== */
'use strict';

document.addEventListener('DOMContentLoaded', () => {
    Auth.init((user) => {
        App.$('#profile-username').textContent = user.username;
        App.$('#profile-role').textContent = user.role.replace('_', ' ');
        App.$('#profile-email').value = user.email;

        wireProfileForm();
        wirePasswordForm();
    }).catch((error) => App.showAlert('#profile-error', error.message, 'error'));
});

function wireProfileForm() {
    const form = App.$('#profile-form');
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        App.hideAlert('#profile-alert');

        const submit = App.$('#profile-submit');
        const email = App.$('#profile-email').value.trim();
        if (!email) {
            App.showAlert('#profile-alert', 'Email is required.', 'error');
            return;
        }

        App.busy(submit, true, 'Saving…');
        try {
            const response = await App.putJson('/api/me/profile', { email: email });
            App.toast(response.message || 'Profile updated', 'success');
        } catch (error) {
            App.showAlert('#profile-alert', error.message, 'error');
        } finally {
            App.busy(submit, false);
        }
    });
}

function wirePasswordForm() {
    const form = App.$('#password-form');
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        App.hideAlert('#password-alert');

        const submit = App.$('#password-submit');
        const current = App.$('#current-password').value;
        const next = App.$('#new-password').value;
        const confirm = App.$('#confirm-password').value;

        if (!current || !next || !confirm) {
            App.showAlert('#password-alert', 'All three fields are required.', 'error');
            return;
        }
        if (next.length < 8) {
            App.showAlert('#password-alert', 'The new password must be at least 8 characters.', 'error');
            return;
        }
        if (next !== confirm) {
            App.showAlert('#password-alert', 'The new passwords do not match.', 'error');
            return;
        }

        App.busy(submit, true, 'Changing…');
        try {
            const response = await App.postJson('/api/auth/change-password', {
                currentPassword: current,
                newPassword: next
            });
            App.toast(response.message || 'Password changed', 'success');
            form.reset();
        } catch (error) {
            App.showAlert('#password-alert', error.message, 'error');
        } finally {
            App.busy(submit, false);
        }
    });
}
