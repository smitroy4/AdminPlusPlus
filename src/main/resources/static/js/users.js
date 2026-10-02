/* ==========================================================================
   users.js - admin-only account management: list, create, edit and roles.
   ========================================================================== */
'use strict';

/* Staff roles only - ADMIN and CLIENT accounts are excluded from this list. */
const ROLES = ['ASSOCIATE', 'COORDINATOR', 'MANAGER'];

document.addEventListener('DOMContentLoaded', () => {
    Auth.init(async () => {
        if (!Auth.isAdmin()) {
            App.showAlert('#users-error', 'This page is restricted to administrators.', 'error');
            Auth.hide(App.$('#users-content'));
            return;
        }
        await loadUsers();
        wireCreateDialog();
        wireEditDialog();
    }).catch((error) => App.showAlert('#users-error', error.message, 'error'));
});

async function loadUsers() {
    const tbody = App.$('#users-body');
    try {
        const response = await App.getJson('/api/admin/users');
        render(response.data);
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="6">' + App.emptyState('Could not load users', error.message) + '</td></tr>';
    }
}

function render(users) {
    const tbody = App.$('#users-body');
    const staff = users.filter((user) => ROLES.indexOf(user.role) !== -1);
    window.__usersCache = staff;
    if (!staff.length) {
        tbody.innerHTML = '<tr><td colspan="6">' + App.emptyState('No staff accounts yet') + '</td></tr>';
        return;
    }

    tbody.innerHTML = staff.map((user) => {
        const isSelf = Auth.current() && Auth.current().id === user.id;
        return '<tr>'
            + '<td><span class="cell-user"><span class="avatar">' + App.esc(App.initials(user.username))
            + '</span>' + App.esc(user.username) + (isSelf ? ' <span class="muted">(you)</span>' : '') + '</span></td>'
            + '<td>' + App.esc(user.email) + '</td>'
            + '<td><select class="select select--sm" data-role-for="' + App.esc(user.id) + '"'
            + (isSelf ? ' disabled title="You cannot change your own role"' : '') + '>'
            + ROLES.map((role) =>
                '<option value="' + role + '"' + (user.role === role ? ' selected' : '') + '>'
                + role.replace('_', ' ') + '</option>').join('')
            + '</select></td>'
            + '<td>' + App.esc(user.role.replace('_', ' ')) + '</td>'
            + '<td class="cell-date">' + App.esc(App.formatDate(user.createdAt)) + '</td>'
            + '<td class="text-right">'
            + '<button type="button" class="btn btn--secondary btn--sm" data-edit-user="' + App.esc(user.id) + '">Edit</button> '
            + (isSelf ? '' : '<button type="button" class="btn btn--secondary btn--sm" data-save-role="' + App.esc(user.id) + '">Save</button>')
            + '</td>'
            + '</tr>';
    }).join('');

    App.$$('[data-save-role]', tbody).forEach((button) => {
        button.addEventListener('click', () => saveRole(button.dataset.saveRole, button));
    });
    App.$$('[data-edit-user]', tbody).forEach((button) => {
        button.addEventListener('click', () => openEditDialog(button.dataset.editUser));
    });
}

async function saveRole(userId, button) {
    const select = App.$('[data-role-for="' + userId + '"]');
    if (!select) {
        return;
    }
    App.busy(button, true, '…');
    try {
        const response = await App.patchJson('/api/admin/users/' + encodeURIComponent(userId) + '/role', {
            role: select.value
        });
        App.toast(response.message || 'Role updated', 'success');
        await loadUsers();
    } catch (error) {
        App.toast(error.message, 'error');
        App.busy(button, false);
    }
}

function wireCreateDialog() {
    const dialog = App.$('#create-user-modal');
    const form = App.$('#create-user-form');
    if (!dialog || !form) {
        return;
    }

    const close = () => dialog.classList.remove('is-open');
    App.on('#create-user-cancel', 'click', close);
    App.on('#create-user-close', 'click', close);
    App.on('[data-open-create-user]', 'click', () => {
        form.reset();
        App.hideAlert('#create-user-alert');
        dialog.classList.add('is-open');
        App.$('#new-username').focus();
    });
    dialog.addEventListener('click', (event) => {
        if (event.target === dialog) {
            close();
        }
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && dialog.classList.contains('is-open')) {
            close();
        }
    });

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        App.hideAlert('#create-user-alert');

        const submit = App.$('#create-user-submit');
        const username = App.$('#new-username').value.trim();
        const email = App.$('#new-email').value.trim();
        const password = App.$('#new-password').value;
        const role = App.$('#new-role').value;

        if (!username || !email || !password) {
            App.showAlert('#create-user-alert', 'Username, email and password are all required.', 'error');
            return;
        }

        App.busy(submit, true, 'Creating…');
        try {
            const response = await App.postJson('/api/admin/users', {
                username: username,
                email: email,
                password: password,
                role: role
            });
            App.toast(response.message || 'User created', 'success');
            close();
            await loadUsers();
        } catch (error) {
            App.showAlert('#create-user-alert', error.message, 'error');
        } finally {
            App.busy(submit, false);
        }
    });
}

/* ---------------------------------------------------------- edit dialog */

let editSnapshot = null;

function wireEditDialog() {
    const dialog = App.$('#edit-user-modal');
    const form = App.$('#edit-user-form');
    if (!dialog || !form) {
        return;
    }

    const close = () => dialog.classList.remove('is-open');
    App.on('#edit-user-cancel', 'click', close);
    App.on('#edit-user-close', 'click', close);
    dialog.addEventListener('click', (event) => {
        if (event.target === dialog) {
            close();
        }
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && dialog.classList.contains('is-open')) {
            close();
        }
    });

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        App.hideAlert('#edit-user-alert');

        const submit = App.$('#edit-user-submit');
        const userId = App.$('#edit-user-id').value;
        const username = App.$('#edit-username').value.trim();
        const email = App.$('#edit-email').value.trim();
        const password = App.$('#edit-password').value;

        if (!username || !email) {
            App.showAlert('#edit-user-alert', 'Username and email are required.', 'error');
            return;
        }

        App.busy(submit, true, 'Saving…');
        try {
            if (editSnapshot && (username !== editSnapshot.username || email !== editSnapshot.email)) {
                await App.patchJson('/api/admin/users/' + encodeURIComponent(userId), {
                    username: username,
                    email: email
                });
            }
            if (password) {
                await App.patchJson('/api/admin/users/' + encodeURIComponent(userId) + '/password', {
                    password: password
                });
            }
            App.toast('User updated', 'success');
            close();
            await loadUsers();
        } catch (error) {
            App.showAlert('#edit-user-alert', error.message, 'error');
        } finally {
            App.busy(submit, false);
        }
    });
}

function openEditDialog(userId) {
    const dialog = App.$('#edit-user-modal');
    if (!dialog) {
        return;
    }
    const user = (window.__usersCache || []).find((item) => String(item.id) === String(userId));
    if (!user) {
        App.toast('Could not find that user', 'error');
        return;
    }

    editSnapshot = { username: user.username, email: user.email };
    App.$('#edit-user-id').value = user.id;
    App.$('#edit-username').value = user.username;
    App.$('#edit-email').value = user.email;
    App.$('#edit-password').value = '';
    App.hideAlert('#edit-user-alert');
    dialog.classList.add('is-open');
    App.$('#edit-username').focus();
}
