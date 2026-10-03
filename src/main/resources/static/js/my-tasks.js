/* ==========================================================================
   my-tasks.js - every task assigned to the signed-in user, any status.
   ========================================================================== */
'use strict';

document.addEventListener('DOMContentLoaded', () => {
    Auth.init(load).catch((error) => App.showAlert('#my-tasks-error', error.message, 'error'));
});

const PRIORITY_ORDER = { URGENT: 2, NORMAL: 1 };
const STATUS_ORDER = { OPEN: 5, IN_PROGRESS: 4, QUALITY: 3, SUBMITTED: 2, CLOSED: 1 };

async function load() {
    const table = App.$('#mine-table');
    const tbody = App.$('#mine-body');
    tbody.addEventListener('click', (event) => {
        /* A client name in the row is its own link; don't fight it. */
        if (event.target.closest('a')) {
            return;
        }
        const row = event.target.closest('tr[data-task-id]');
        if (row) {
            window.location.href = App.taskDetailUrl(row.dataset.taskId);
        }
    });

    try {
        const response = await App.getJson('/api/tasks/mine');
        const tasks = response.data;
        const count = App.$('#mine-count');
        count.textContent = tasks.length + (tasks.length === 1 ? ' task' : ' tasks');

        App.makeSortable(table, tasks,
            (rows) => { tbody.innerHTML = renderRows(rows, App.columnCount(table)); },
            {
                taskNo: (task) => task.taskNo,
                title: (task) => task.title,
                status: (task) => STATUS_ORDER[task.status] || 0,
                priority: (task) => PRIORITY_ORDER[task.priority] || 0,
                client: (task) => (task.client ? task.client.name : '~internal'),
                createdBy: (task) => (task.createdBy ? task.createdBy.username : '~unknown'),
                createdAt: (task) => new Date(task.createdAt).getTime() || 0,
                updatedAt: (task) => new Date(task.updatedAt).getTime() || 0
            },
            'priority');
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="' + App.columnCount(table) + '">'
            + App.emptyState('Could not load your tasks', error.message) + '</td></tr>';
    }
}

function renderRows(tasks, columns) {
    if (!tasks.length) {
        return '<tr><td colspan="' + (columns || 8) + '">' + App.emptyState('Nothing assigned to you',
            'Tasks will show up here as soon as a manager assigns them.') + '</td></tr>';
    }
    /* The Client column is dropped for client accounts. */
    const showClient = !Auth.isClient();
    return tasks.map((task) =>
        '<tr class="is-clickable" data-task-id="' + App.esc(task.id) + '" tabindex="0">'
        + '<td class="cell-taskno">' + App.esc(task.taskNo) + '</td>'
        + '<td class="cell-title">' + App.esc(task.title) + '</td>'
        + '<td>' + App.statusBadge(task.status) + '</td>'
        + '<td>' + App.priorityBadge(task.priority) + '</td>'
        + (showClient ? '<td>' + App.clientCell(task.client) + '</td>' : '')
        + '<td>' + App.userCell(task.createdBy) + '</td>'
        + '<td class="cell-date" title="' + App.esc(App.formatDate(task.createdAt)) + '">'
        + App.esc(App.formatDate(task.createdAt)) + '</td>'
        + '<td class="cell-date" title="' + App.esc(App.formatDate(task.updatedAt)) + '">'
        + App.esc(App.formatDate(task.updatedAt)) + '</td>'
        + '</tr>').join('');
}
