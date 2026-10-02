/* ==========================================================================
   dashboard.js - stats tiles, "My Open Tasks" table, the shared "All Open
   Tasks" table plus the create-task dialog.
   ========================================================================== */
'use strict';

document.addEventListener('DOMContentLoaded', () => {
    Auth.init(async () => {
        /* The open backlog is readable by every role; only the counters and
           the create button are restricted. */
        await Promise.all([loadStats(), loadMyTasks(), loadAllTasks()]);
        if (Auth.isManagerOrAbove()) {
            wireCreateDialog();
        }
    }).catch(showFatal);
});

/* ------------------------------------------------------------------ stats */

async function loadStats() {
    try {
        const response = await App.getJson('/api/dashboard/stats');
        const stats = response.data;
        setTile('#tile-my-open', stats.myOpen);
        setTile('#tile-my-progress', stats.myInProgress);
        setTile('#tile-my-completed', stats.myCompleted);
        setTile('#tile-my-total', stats.myTotal);

        if (stats.canViewTeam) {
            Auth.show(App.$('#team-tiles-section'));
            setTile('#tile-team-open', stats.teamOpen);
            setTile('#tile-team-progress', stats.teamInProgress);
            setTile('#tile-team-completed', stats.teamCompleted);
            setTile('#tile-team-total', stats.teamTotal);
        }

        if (stats.canViewAll) {
            Auth.show(App.$('#all-tiles-section'));
            setTile('#tile-all-open', stats.allOpen);
            setTile('#tile-all-progress', stats.allInProgress);
            setTile('#tile-all-completed', stats.allCompleted);
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
        const label = kind === 'status' ? App.statusLabel(bucket.label) : App.priorityLabel(bucket.label);
        const badge = kind === 'status' ? App.statusBadge(bucket.label) : App.priorityBadge(bucket.label);
        const width = Math.round((bucket.count / max) * 100);
        return '<div class="bar-row">'
            + '<div class="bar-row__head">' + badge + '<span class="bar-row__value">' + App.esc(bucket.count) + '</span></div>'
            + '<div class="bar-track"><div class="bar-fill bar-fill--' + App.esc(bucket.label.toLowerCase()) + '" style="width:' + width + '%"></div></div>'
            + '<div class="visually-hidden">' + App.esc(label) + '</div>'
            + '</div>';
    }).join('');
}

/* ------------------------------------------------------------------ tables */

const PRIORITY_ORDER = { URGENT: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };
const STATUS_ORDER = { OPEN: 4, IN_PROGRESS: 3, COMPLETED: 2, CLOSED: 1 };

function accessors(withCreator) {
    const map = {
        taskNo: (task) => task.taskNo,
        title: (task) => task.title,
        status: (task) => STATUS_ORDER[task.status] || 0,
        priority: (task) => PRIORITY_ORDER[task.priority] || 0,
        assignedTo: (task) => (task.assignedTo ? task.assignedTo.username : '~unassigned'),
        client: (task) => (task.client ? task.client.name : '~internal'),
        createdAt: (task) => new Date(task.createdAt).getTime() || 0
    };
    if (withCreator) {
        map.createdBy = (task) => (task.createdBy ? task.createdBy.username : '~unassigned');
    }
    return map;
}

function renderRows(tasks, withCreator) {
    if (!tasks || tasks.length === 0) {
        return '<tr><td colspan="' + (withCreator ? 8 : 7) + '">'
            + App.emptyState('No tasks to show', 'Tasks matching this view will appear here.')
            + '</td></tr>';
    }
    return tasks.map((task) => {
        const titleCell = '<td class="cell-title">'
            + App.esc(task.title)
            + (task.description ? '<span class="cell-sub">' + App.esc(truncate(task.description, 90)) + '</span>' : '')
            + '</td>';
        const creatorCell = withCreator ? '<td>' + App.userCell(task.createdBy) + '</td>' : '';
        return '<tr class="is-clickable" data-task-id="' + App.esc(task.id) + '" tabindex="0">'
            + '<td class="cell-taskno">' + App.esc(task.taskNo) + '</td>'
            + titleCell
            + '<td>' + App.statusBadge(task.status) + '</td>'
            + '<td>' + App.priorityBadge(task.priority) + '</td>'
            + '<td>' + App.userCell(task.assignedTo) + '</td>'
            + '<td>' + App.clientCell(task.client) + '</td>'
            + creatorCell
            + '<td class="cell-date" title="' + App.esc(App.formatDate(task.createdAt)) + '">'
            + App.esc(App.formatDate(task.createdAt)) + '</td>'
            + '</tr>';
    }).join('');
}

function truncate(text, max) {
    const value = String(text || '').replace(/\s+/g, ' ').trim();
    return value.length > max ? value.substring(0, max - 1) + '…' : value;
}

function wireRowClicks(tbody) {
    tbody.addEventListener('click', (event) => {
        const row = event.target.closest('tr[data-task-id]');
        if (row) {
            window.location.href = '/task-detail.html?id=' + encodeURIComponent(row.dataset.taskId);
        }
    });
    tbody.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter' && event.key !== ' ') {
            return;
        }
        const row = event.target.closest('tr[data-task-id]');
        if (row) {
            event.preventDefault();
            window.location.href = '/task-detail.html?id=' + encodeURIComponent(row.dataset.taskId);
        }
    });
}

async function loadMyTasks() {
    const tbody = App.$('#my-tasks-body');
    wireRowClicks(tbody);
    try {
        const response = await App.getJson('/api/tasks/my-open');
        App.makeSortable(App.$('#my-tasks-table'), response.data,
            (rows) => { tbody.innerHTML = renderRows(rows, false); },
            accessors(false), 'priority');
        setCount('#my-tasks-count', response.data.length);
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="7">' + App.emptyState('Could not load your tasks', error.message) + '</td></tr>';
    }
}

async function loadAllTasks() {
    const tbody = App.$('#all-tasks-body');
    wireRowClicks(tbody);
    try {
        const response = await App.getJson('/api/tasks/all-open');
        App.makeSortable(App.$('#all-tasks-table'), response.data,
            (rows) => { tbody.innerHTML = renderRows(rows, true); },
            accessors(true), 'priority');
        setCount('#all-tasks-count', response.data.length);
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="8">' + App.emptyState('Could not load the backlog', error.message) + '</td></tr>';
    }
}

function setCount(selector, value) {
    const node = App.$(selector);
    if (node) {
        node.textContent = value + (value === 1 ? ' task' : ' tasks');
    }
}

/* --------------------------------------------------------- create dialog */

/* Only wired for MANAGER / ADMIN: associates, coordinators and clients have
   no "New task" button. */
function wireCreateDialog() {
    const dialog = App.$('#create-task-modal');
    const form = App.$('#create-task-form');
    if (!dialog || !form) {
        return;
    }

    loadAssignableUsers();

    App.$$('[data-open-create]').forEach((button) => {
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
            window.location.href = '/task-detail.html?id=' + encodeURIComponent(response.data.id);
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
    const node = App.$('#stats-error');
    if (node) {
        App.showAlert('#stats-error', error.message || 'Unexpected error', 'error');
    }
}
