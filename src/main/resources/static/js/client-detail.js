/* ==========================================================================
   client-detail.js - the Client Details page, reached from any client name in
   the app (task tables, operational sheets, the client catalogue) and from the
   "My Company" nav link.

   Reached as /client-detail.html?id=3. A client account that lands here without
   an id is sent to its own customer instead of being asked for one.

   The payload is shaped by the server for the caller's role: managers/admins get
   the full record including internal notes, a customer reading itself gets the
   commercial record minus the notes, and everything else gets identity only. The
   page therefore renders only the sections that actually arrived.
   ========================================================================== */
'use strict';

document.addEventListener('DOMContentLoaded', () => {
    Auth.init(async () => {
        const clientId = requestedClientId();
        if (!clientId) {
            window.location.href = '/dashboard.html';
            return;
        }
        const profile = await loadProfile(clientId);
        if (!profile) {
            return;
        }
        renderHeader(profile);
        renderStats(profile.taskStats);
        renderCompany(profile.client);
        renderCommercial(profile.client);
        renderNotes(profile.client);
        renderTasks(profile.recentTasks);
        wireTaskRows();
        wireCreateDialog(profile.client);
    }).catch(showFatal);
});

/** ?id=… , falling back to the customer this account belongs to. */
function requestedClientId() {
    const fromQuery = new URLSearchParams(window.location.search).get('id');
    if (fromQuery) {
        return fromQuery;
    }
    return Auth.isClient() ? Auth.currentUserClientId() : null;
}

async function loadProfile(clientId) {
    try {
        const response = await App.getJson('/api/clients/' + encodeURIComponent(clientId) + '/profile');
        return response.data;
    } catch (error) {
        App.showAlert('#page-error', error.message, 'error');
        const heading = App.$('#client-name');
        if (heading) {
            heading.textContent = 'Client not available';
        }
        setText('#client-subtitle', 'This customer could not be loaded.');
        const tbody = App.$('#client-tasks-body');
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="6">'
                + App.emptyState('Nothing to show', 'The client record could not be loaded.') + '</td></tr>';
        }
        return null;
    }
}

function renderHeader(profile) {
    const client = profile.client;
    setText('#client-name', client.name || 'Unnamed client');
    setText('#crumb-name', client.name || 'Client details');
    document.title = (client.name || 'Client') + ' | Admin++';

    const parts = [];
    if (client.industry) {
        parts.push(client.industry);
    }
    if (client.companySize) {
        parts.push(client.companySize);
    }
    if (client.address) {
        parts.push([client.city, client.state, client.country].filter(Boolean).join(', ') || client.address);
    }
    if (parts.length === 0) {
        setText('#client-subtitle', 'Client record');
    } else {
        setText('#client-subtitle', parts.join(' · '));
    }
}

function renderStats(stats) {
    if (!stats) {
        return;
    }
    setText('#stat-open', String(stats.open));
    setText('#stat-in-progress', String(stats.inProgress));
    setText('#stat-quality', String(stats.quality));
    setText('#stat-submitted', String(stats.submitted));
    setText('#stat-total', String(stats.total));
}

function renderCompany(client) {
    const rows = [
        ['Company', client.name],
        ['Status', client.status],
        ['Contact', [client.contactName, client.contactEmail].filter(Boolean).join(' · ')],
        ['Phone', client.contactPhone],
        ['Industry', client.industry],
        ['Company size', client.companySize],
        ['Website', website(client.website)],
        ['Address', [client.address, client.city, client.state, client.postalCode, client.country]
            .filter(Boolean).join(', ')],
        ['Client since', client.createdAt ? App.formatDate(client.createdAt) : null]
    ];
    /* Drop anything the caller's role did not receive rather than printing
       "undefined" over a layout built for the full record. */
    const html = rows
        .filter((row) => row[1] !== undefined && row[1] !== null && row[1] !== '')
        .map((row) => meta(row[0], row[1]))
        .join('');
    document.getElementById('company-grid').innerHTML = html
        || meta('Client', 'No company details are visible for your role.');
}

function renderCommercial(client) {
    /* Mirrors ClientService.project: only managers/admins and a customer reading
       itself receive the billing block, and the JSON omits it for everyone else. */
    if (!Auth.isManagerOrAbove() && !Auth.isClient()) {
        return;
    }
    Auth.show(App.$('#commercial-section'));
    const rows = [
        ['Payment status', client.paymentStatus],
        ['Billing status', client.billingStatus],
        ['Payment method', client.paymentMethod],
        ['Billing cycle', client.billingCycle],
        ['Last payment', client.lastPaymentAt ? App.formatDate(client.lastPaymentAt) : null],
        ['Last payment ref.', client.lastPaymentReference],
        ['Next payment due', client.nextPaymentDueAt ? App.formatDate(client.nextPaymentDueAt) : null],
        ['Outstanding', money(client.outstandingAmount)],
        ['Total paid', money(client.totalPaid)]
    ];
    const html = rows
        .filter((row) => row[1] !== undefined && row[1] !== null && row[1] !== '')
        .map((row) => meta(row[0], row[1]))
        .join('');
    document.getElementById('commercial-grid').innerHTML = html
        || meta('Billing', 'No billing information is recorded for this customer.');
}

/** Internal notes are never sent to a customer account, only to managers/admins. */
function renderNotes(client) {
    if (!Auth.isManagerOrAbove()) {
        return;
    }
    if (client.notes === undefined || client.notes === null || client.notes === '') {
        return;
    }
    Auth.show(App.$('#notes-section'));
    /* Internal notes can contain line breaks the billing fields never do. */
    document.getElementById('client-notes').textContent = client.notes;
}

function renderTasks(tasks) {
    const tbody = App.$('#client-tasks-body');
    if (!tasks || tasks.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6">'
            + App.emptyState('No tasks yet', 'Tasks raised against this customer will appear here.')
            + '</td></tr>';
        return;
    }
    tbody.innerHTML = tasks.map((task) =>
        '<tr class="is-clickable" data-task-id="' + App.esc(task.id) + '" tabindex="0">'
        + '<td class="cell-taskno">' + App.esc(task.taskNo) + '</td>'
        + '<td class="cell-title">' + App.esc(task.title) + '</td>'
        + '<td>' + App.statusBadge(task.status) + '</td>'
        + '<td>' + App.priorityBadge(task.priority) + '</td>'
        + '<td>' + App.userCell(task.assignedTo) + '</td>'
        + '<td class="cell-date" title="' + App.esc(App.formatDate(task.updatedAt)) + '">'
        + App.esc(App.formatRelative(task.updatedAt)) + '</td>'
        + '</tr>').join('');
}

function wireTaskRows() {
    const tbody = App.$('#client-tasks-body');
    if (!tbody) {
        return;
    }
    const open = (event) => {
        const row = event.target.closest('tr[data-task-id]');
        if (row) {
            window.location.href = App.taskDetailUrl(row.dataset.taskId);
        }
    };
    tbody.addEventListener('click', open);
    tbody.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            open(event);
        }
    });
}

/* --------------------------------------------------------- create dialog */

/**
 * A task raised from this page belongs to the customer being looked at, so the
 * client id is pinned here rather than offered as a choice. Clients get no
 * assignee control and are bound to their own customer by the server.
 */
function wireCreateDialog(client) {
    const dialog = App.$('#create-task-modal');
    const form = App.$('#create-task-form');
    if (!dialog || !form) {
        return;
    }
    if (!Auth.isManagerOrAbove() && !Auth.isClient()) {
        return;
    }

    if (Auth.isClient()) {
        const field = App.$('#field-assignee');
        if (field) {
            field.remove();
        }
        const hint = App.$('#client-create-hint');
        if (hint) {
            hint.style.display = '';
        }
        setText('#client-create-company', client.name || 'your company');
    } else {
        loadAssignableUsers();
    }

    App.$$('[data-open-create]').forEach((button) => {
        Auth.show(button);
        button.addEventListener('click', () => {
            form.reset();
            App.hideAlert('#create-task-alert');
            dialog.classList.add('is-open');
            App.$('#task-title').focus();
        });
    });

    const close = () => dialog.classList.remove('is-open');
    App.on('#create-task-cancel', 'click', close);
    App.on('#create-task-close', 'click', close);
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
        App.hideAlert('#create-task-alert');

        const submit = App.$('#create-task-submit');
        const title = App.$('#task-title').value.trim();
        const description = App.$('#task-description').value.trim();
        const priority = App.$('#task-priority').value;
        const assignee = App.$('#task-assignee');
        const assigneeId = (assignee && assignee.value) ? Number(assignee.value) : null;

        if (!title) {
            App.showAlert('#create-task-alert', 'A title is required.', 'error');
            return;
        }

        const payload = { title: title, priority: priority, clientId: client.id };
        if (description) {
            payload.description = description;
        }
        if (assigneeId) {
            payload.assignedToId = assigneeId;
        }

        App.busy(submit, true, 'Creating…');
        try {
            const response = await App.postJson('/api/task', payload);
            App.toast(response.message || 'Task created', 'success');
            close();
            window.location.href = App.taskDetailUrl(response.data.id);
        } catch (error) {
            App.showAlert('#create-task-alert', error.message, 'error');
        } finally {
            App.busy(submit, false);
        }
    });
}

async function loadAssignableUsers() {
    const select = App.$('#task-assignee');
    if (!select) {
        return;
    }
    try {
        const response = await App.getJson('/api/manager/users');
        select.innerHTML = '<option value="">— Unassigned —</option>'
            + response.data.map((user) =>
                '<option value="' + App.esc(user.id) + '">' + App.esc(user.username)
                + ' (' + App.esc(user.role) + ')</option>').join('');
    } catch (error) {
        select.innerHTML = '<option value="">— Unavailable —</option>';
    }
}

/* ---------------------------------------------------------------- helpers */

function meta(label, value) {
    return '<div class="meta"><div class="meta__label">' + App.esc(label) + '</div>'
        + '<div class="meta__value">' + value + '</div></div>';
}

function website(url) {
    if (!url) {
        return null;
    }
    const href = /^https?:\/\//i.test(url) ? url : 'https://' + url;
    return '<a href="' + App.esc(href) + '" target="_blank" rel="noopener noreferrer">'
        + App.esc(url) + '</a>';
}

/** Billing amounts are rendered as plain numbers; currency is not modelled. */
function money(amount) {
    if (amount === undefined || amount === null || amount === '') {
        return null;
    }
    const value = Number(amount);
    if (!isFinite(value)) {
        return App.esc(amount);
    }
    return App.esc(value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }));
}

function setText(selector, value) {
    const node = App.$(selector);
    if (node) {
        node.textContent = value;
    }
}

function showFatal(error) {
    App.showAlert('#page-error', error.message || 'Unexpected error', 'error');
}