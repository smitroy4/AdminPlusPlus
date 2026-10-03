/* ==========================================================================
   dashboard.js - the role-scoped home page.

   Layout is decided by the account's role, and every number comes from the
   server:

     MANAGER / ADMIN  My work, Company-wide, one sheet per level they supervise
     COORDINATOR       My work, Associates sheet
     ASSOCIATE         My work, no management sheets
     CLIENT            My company card, own tasks, New task to raise a request

   An operational sheet row is derived, not stored: a member is ACTIVE while
   they hold an IN_PROGRESS task and IDLE otherwise.
   ========================================================================== */
'use strict';

document.addEventListener('DOMContentLoaded', () => {
    Auth.init(async () => {
        /* The shared backlog is for staff; clients only get their own work
           (loadAllTasks bails out for them). */
        await Promise.all([loadStats(), loadMyTasks(), loadAllTasks(), loadSheets(), loadClientCard()]);
        wireCreateDialog();
    }).catch(showFatal);
});

/* ------------------------------------------------------------------ stats */

async function loadStats() {
    try {
        const response = await App.getJson('/api/dashboard/stats');
        const stats = response.data;
        setTile('#tile-my-open', stats.myOpen);
        setTile('#tile-my-progress', stats.myInProgress);
        setTile('#tile-my-quality', stats.myQuality);
        setTile('#tile-my-total', stats.myTotal);
        if (Auth.isClient()) {
            /* Clients are never the assignee: the tiles count their customer's tasks. */
            const totalLabel = App.$('#tile-my-total-label');
            if (totalLabel) {
                totalLabel.textContent = 'Total';
            }
            /* …and the same holds for the table below it. */
            setText('#my-tasks-heading', 'Our open tasks');
        }

        if (stats.canViewAll) {
            Auth.show(App.$('#all-tiles-section'));
            setTile('#tile-all-open', stats.allOpen);
            setTile('#tile-all-progress', stats.allInProgress);
            setTile('#tile-all-quality', stats.allQuality);
            setTile('#tile-all-total', stats.allTotal);
        }

        /* The server scopes the breakdowns: own tasks for associates,
           associate tasks for coordinators, everything for managers/admins. */
        renderBreakdown('#status-breakdown', stats.byStatus, 'status');
        renderBreakdown('#priority-breakdown', stats.byPriority, 'priority');
    } catch (error) {
        App.showAlert('#stats-error', error.message, 'error');
    }
}

function setTile(selector, value) {
    const node = App.$(selector);
    if (node) {
        node.textContent = value === null || value === undefined ? '0' : String(value);
    }
}

function renderBreakdown(selector, buckets, kind) {
    const node = App.$(selector);
    if (!node || !buckets) {
        return;
    }
    const max = Math.max(1, ...buckets.map((bucket) => bucket.count));
    node.innerHTML = buckets.map((bucket) => {
        const badge = kind === 'status' ? App.statusBadge(bucket.label) : App.priorityBadge(bucket.label);
        const width = Math.round((bucket.count / max) * 100);
        return '<div class="bar-row">'
            + '<div class="bar-row__head">' + badge + '<span class="bar-row__value">' + App.esc(bucket.count) + '</span></div>'
            + '<div class="bar-track"><div class="bar-fill bar-fill--' + App.esc(bucket.label.toLowerCase()) + '" style="width:' + width + '%"></div></div>'
            + '</div>';
    }).join('');
}

/* -------------------------------------------------------- operational sheets */

/**
 * Which sheets this role gets. It mirrors the server's authorisation in
 * TeamService — the UI only ever shows what the endpoint would accept, and the
 * endpoint re-checks it regardless.
 */
function sheetPlan() {
    if (Auth.isAdmin()) {
        return [
            { role: 'MANAGER', section: '#sheet-managers-section', body: '#sheet-managers-body' },
            { role: 'COORDINATOR', section: '#sheet-coordinators-section', body: '#sheet-coordinators-body' },
            { role: 'ASSOCIATE', section: '#sheet-associates-section', body: '#sheet-associates-body' }
        ];
    }
    if (Auth.isManagerOrAbove()) {
        return [
            { role: 'COORDINATOR', section: '#sheet-coordinators-section', body: '#sheet-coordinators-body' },
            { role: 'ASSOCIATE', section: '#sheet-associates-section', body: '#sheet-associates-body' }
        ];
    }
    if (Auth.isCoordinatorOrAbove()) {
        return [{ role: 'ASSOCIATE', section: '#sheet-associates-section', body: '#sheet-associates-body' }];
    }
    return [];
}

async function loadSheets() {
    const plan = sheetPlan();
    if (plan.length === 0) {
        return;
    }
    plan.forEach((sheet) => Auth.show(App.$(sheet.section)));

    await Promise.all(plan.map(async (sheet) => {
        const tbody = App.$(sheet.body);
        try {
            const response = await App.getJson('/api/dashboard/team?role=' + encodeURIComponent(sheet.role));
            renderSheet(tbody, response.data);
        } catch (error) {
            tbody.innerHTML = '<tr><td colspan="4">'
                + App.emptyState('Could not load the sheet', error.message) + '</td></tr>';
        }
    }));
}

function renderSheet(tbody, members) {
    if (!tbody) {
        return;
    }
    if (!members || members.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4">'
            + App.emptyState('Nobody to show yet', 'Accounts with this role will appear here.')
            + '</td></tr>';
        return;
    }
    const now = Date.now();
    tbody.innerHTML = members.map((member) => {
        /* `since` is the current task's last update; an idle member carries none. */
        const since = member.since
            ? '<span class="cell-date" title="' + App.esc(App.formatDate(member.since)) + '">'
                + App.esc(elapsed(member.since, now)) + '</span>'
            : '<span class="cell-unassigned">&mdash;</span>';
        return '<tr>'
            + '<td>' + App.userCell(member.user) + '</td>'
            + '<td>' + App.presenceBadge(member.presence) + '</td>'
            + '<td>' + App.taskRefCell(member.currentTask) + '</td>'
            + '<td>' + since + '</td>'
            + '</tr>';
    }).join('');
}

/** "2h 15m" / "3d" — how long a member has been on the current task. */
function elapsed(from, now) {
    const millis = now - new Date(from).getTime();
    if (!isFinite(millis) || millis < 0) {
        return 'just now';
    }
    const minutes = Math.floor(millis / 60000);
    if (minutes < 1) {
        return 'just now';
    }
    if (minutes < 60) {
        return minutes + 'm';
    }
    const hours = Math.floor(minutes / 60);
    if (hours < 24) {
        return hours + 'h ' + (minutes % 60) + 'm';
    }
    return Math.floor(hours / 24) + 'd';
}

/* ------------------------------------------------------------------ tables */

const PRIORITY_ORDER = { URGENT: 2, NORMAL: 1 };
const STATUS_ORDER = { OPEN: 5, IN_PROGRESS: 4, QUALITY: 3, SUBMITTED: 2, CLOSED: 1 };

function accessors(withCreator) {
    const map = {
        taskNo: (task) => task.taskNo,
        title: (task) => task.title,
        status: (task) => STATUS_ORDER[task.status] || 0,
        priority: (task) => PRIORITY_ORDER[task.priority] || 0,
        assignedTo: (task) => (task.assignedTo ? task.assignedTo.username : '~unassigned'),
        client: (task) => (task.client ? task.client.name : '~internal'),
        updatedAt: (task) => new Date(task.updatedAt).getTime() || 0
    };
    if (withCreator) {
        map.createdBy = (task) => (task.createdBy ? task.createdBy.username : '~unassigned');
    }
    return map;
}

function renderRows(tasks, withCreator, columns) {
    if (!tasks || tasks.length === 0) {
        return '<tr><td colspan="' + (columns || (withCreator ? 8 : 7)) + '">'
            + App.emptyState('No tasks to show', 'Tasks matching this view will appear here.')
            + '</td></tr>';
    }
    /* The Client column is dropped for client accounts: every row here is
       their own customer, so the column would be a constant. */
    const showClient = !Auth.isClient();
    return tasks.map((task) => {
        const titleCell = '<td class="cell-title">'
            + App.esc(task.title)
            + (task.description ? '<span class="cell-sub">' + App.esc(truncate(task.description, 90)) + '</span>' : '')
            + '</td>';
        const creatorCell = withCreator ? '<td>' + App.userCell(task.createdBy) + '</td>' : '';
        const clientCell = showClient ? '<td>' + App.clientCell(task.client) + '</td>' : '';
        return '<tr class="is-clickable" data-task-id="' + App.esc(task.id) + '" tabindex="0">'
            + '<td class="cell-taskno">' + App.esc(task.taskNo) + '</td>'
            + titleCell
            + '<td>' + App.statusBadge(task.status) + '</td>'
            + '<td>' + App.priorityBadge(task.priority) + '</td>'
            + '<td>' + App.userCell(task.assignedTo) + '</td>'
            + clientCell
            + creatorCell
            + '<td class="cell-date" title="' + App.esc(App.formatDate(task.updatedAt)) + '">'
            + App.esc(App.formatRelative(task.updatedAt)) + '</td>'
            + '</tr>';
    }).join('');
}

function truncate(text, max) {
    const value = String(text || '').replace(/\s+/g, ' ').trim();
    return value.length > max ? value.substring(0, max - 1) + '…' : value;
}

function wireRowClicks(tbody) {
    tbody.addEventListener('click', (event) => {
        /* Links inside a row (client name, task number) keep their own target. */
        if (event.target.closest('a')) {
            return;
        }
        const row = event.target.closest('tr[data-task-id]');
        if (row) {
            window.location.href = App.taskDetailUrl(row.dataset.taskId);
        }
    });
    tbody.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter' && event.key !== ' ') {
            return;
        }
        const row = event.target.closest('tr[data-task-id]');
        if (row) {
            event.preventDefault();
            window.location.href = App.taskDetailUrl(row.dataset.taskId);
        }
    });
}

async function loadMyTasks() {
    const tbody = App.$('#my-tasks-body');
    const table = App.$('#my-tasks-table');
    wireRowClicks(tbody);
    try {
        const response = await App.getJson('/api/tasks/my-open');
        App.makeSortable(table, response.data,
            (rows) => { tbody.innerHTML = renderRows(rows, false, App.columnCount(table)); },
            accessors(false), 'priority');
        setCount('#my-tasks-count', response.data.length);
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="' + App.columnCount(table) + '">'
            + App.emptyState('Could not load your tasks', error.message) + '</td></tr>';
    }
}

async function loadAllTasks() {
    /* The shared backlog is hidden for clients — they only ever have their
       own customer's work, which the "My open tasks" table already shows. */
    if (Auth.isClient()) {
        return;
    }
    const tbody = App.$('#all-tasks-body');
    const table = App.$('#all-tasks-table');
    wireRowClicks(tbody);
    try {
        const response = await App.getJson('/api/tasks/all-open');
        App.makeSortable(table, response.data,
            (rows) => { tbody.innerHTML = renderRows(rows, true, App.columnCount(table)); },
            accessors(true), 'priority');
        setCount('#all-tasks-count', response.data.length);
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="' + App.columnCount(table) + '">'
            + App.emptyState('Could not load the backlog', error.message) + '</td></tr>';
    }
}

function setCount(selector, value) {
    const node = App.$(selector);
    if (node) {
        node.textContent = value + (value === 1 ? ' task' : ' tasks');
    }
}

/* -------------------------------------------------------------- client card */

/** A customer lands here straight after login: this is the company it belongs to. */
async function loadClientCard() {
    if (!Auth.isClient()) {
        return;
    }
    const clientId = Auth.currentUserClientId();
    if (!clientId) {
        return;
    }
    try {
        const response = await App.getJson('/api/clients/' + encodeURIComponent(clientId) + '/profile');
        const profile = response.data;
        setText('#client-card-name', profile.client.name);
        setText('#client-card-contact', [profile.client.contactName, profile.client.contactEmail]
            .filter(Boolean).join(' · ') || '—');
        setText('#client-card-open', String(profile.taskStats.open));
        setText('#client-card-progress', String(profile.taskStats.inProgress));
        const link = App.$('#client-card-link');
        if (link) {
            link.setAttribute('href', App.clientDetailUrl(clientId));
        }
    } catch (error) {
        App.showAlert('#stats-error', error.message, 'error');
    }
}

function setText(selector, value) {
    const node = App.$(selector);
    if (node) {
        node.textContent = value;
    }
}

/* --------------------------------------------------------- create dialog */

/**
 * Wired for every role that may raise work: MANAGER, ADMIN and CLIENT.
 * A client's request is forced onto its own customer and left unassigned —
 * the server rejects anything else, this only keeps the form honest.
 */
function wireCreateDialog() {
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

        const payload = { title: title, priority: priority };
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

function showFatal(error) {
    App.showAlert('#stats-error', error.message || 'Unexpected error', 'error');
}