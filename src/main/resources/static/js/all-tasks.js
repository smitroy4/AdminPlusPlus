/* ==========================================================================
   all-tasks.js - the full portal backlog for every role, with client-side
   sorting (any column) and filtering (text / client / agent / status).
   ========================================================================== */
'use strict';

const PRIORITY_ORDER = { URGENT: 2, NORMAL: 1 };
const STATUS_ORDER = { OPEN: 4, IN_PROGRESS: 3, COMPLETED: 2, CLOSED: 1 };

const filters = { text: '', client: '', agent: '', status: '' };

document.addEventListener('DOMContentLoaded', () => {
    Auth.init(load).catch((error) => App.showAlert('#all-tasks-error', error.message, 'error'));
});

async function load() {
    const table = App.$('#all-tasks-table');
    const tbody = App.$('#all-tasks-body');
    const isClient = Auth.isClient();
    wireRowClicks(tbody);

    try {
        const [tasks, clients] = await Promise.all([
            App.getJson('/api/tasks/all'),
            /* A client has no use for a list of other customers, so the
               picker stays out of the way entirely. */
            isClient ? Promise.resolve({ data: [] })
                     : App.getJson('/api/clients').catch(() => ({ data: [] }))
        ]);
        const rows = tasks.data;

        fillClientFilter(clients.data || [], rows, isClient);
        fillAgentFilter(rows);

        const sortable = App.makeSortable(table, rows,
            (sorted) => {
                const visible = sorted.filter(matches);
                tbody.innerHTML = renderRows(visible, App.columnCount(table));
                setCount(visible.length, rows.length);
            },
            accessors(), 'createdAt', 'desc');
        refreshTable = () => sortable.refresh();

        wireFilters();
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="' + App.columnCount(table) + '">'
            + App.emptyState('Could not load the backlog', error.message) + '</td></tr>';
    }
}

/* --------------------------------------------------------------- filters */

function matches(task) {
    if (filters.status && task.status !== filters.status) {
        return false;
    }
    if (filters.client) {
        const clientId = task.client ? String(task.client.id) : 'none';
        if (clientId !== filters.client) {
            return false;
        }
    }
    if (filters.agent) {
        const agentId = task.assignedTo ? String(task.assignedTo.id) : 'unassigned';
        if (agentId !== filters.agent) {
            return false;
        }
    }
    if (filters.text) {
        const haystack = [
            task.taskNo,
            task.title,
            task.client ? task.client.name : '',
            task.assignedTo ? task.assignedTo.username : '',
            task.createdBy ? task.createdBy.username : ''
        ].join(' ').toLowerCase();
        if (haystack.indexOf(filters.text) === -1) {
            return false;
        }
    }
    return true;
}

function fillClientFilter(clients, rows, isClient) {
    const row = App.$('#filters-row');
    if (isClient) {
        /* The field is already `is-hidden`, so the 5-track inline grid needs
           one track less or the Clear button lands in a dead column. */
        if (row) {
            row.style.gridTemplateColumns = '2fr 1fr 1fr auto';
        }
        return;
    }
    const select = App.$('#filter-client');
    if (!select || !clients.length) {
        return;
    }
    const options = clients.map((client) =>
        '<option value="' + App.esc(client.id) + '">' + App.esc(client.name) + '</option>');
    if (rows.some((task) => !task.client)) {
        options.push('<option value="none">No client (internal)</option>');
    }
    select.innerHTML = '<option value="">All clients</option>' + options.join('');
}

function fillAgentFilter(rows) {
    const select = App.$('#filter-agent');
    if (!select) {
        return;
    }
    const seen = new Map();
    rows.forEach((task) => {
        if (task.assignedTo) {
            seen.set(String(task.assignedTo.id), task.assignedTo.username);
        }
    });
    const options = Array.from(seen.entries())
        .sort((a, b) => a[1].localeCompare(b[1]))
        .map(([id, username]) =>
            '<option value="' + App.esc(id) + '">' + App.esc(username) + '</option>');
    if (rows.some((task) => !task.assignedTo)) {
        options.push('<option value="unassigned">Unassigned</option>');
    }
    select.innerHTML = '<option value="">All agents</option>' + options.join('');
}

function wireFilters() {
    const search = App.$('#filter-search');
    const client = App.$('#filter-client');
    const agent = App.$('#filter-agent');
    const status = App.$('#filter-status');
    const clear = App.$('#filter-clear');

    const apply = () => {
        filters.text = search.value.trim().toLowerCase();
        filters.client = client.value;
        filters.agent = agent.value;
        filters.status = status.value;
        refreshTable();
    };

    search.addEventListener('input', apply);
    client.addEventListener('change', apply);
    agent.addEventListener('change', apply);
    status.addEventListener('change', apply);

    clear.addEventListener('click', () => {
        search.value = '';
        client.value = '';
        agent.value = '';
        status.value = '';
        apply();
    });
}

/* The sortable owns sorting; filters simply re-run its render with a narrower
   slice of rows, so a filter change only needs a refresh. */
let refreshTable = () => {};

/* ---------------------------------------------------------------- render */

const SORT_CONFIG = {
    taskNo: (task) => task.taskNo,
    title: (task) => task.title,
    client: (task) => (task.client ? task.client.name : '~internal'),
    status: (task) => STATUS_ORDER[task.status] || 0,
    priority: (task) => PRIORITY_ORDER[task.priority] || 0,
    assignedTo: (task) => (task.assignedTo ? task.assignedTo.username : '~unassigned'),
    createdBy: (task) => (task.createdBy ? task.createdBy.username : '~unassigned'),
    createdAt: (task) => new Date(task.createdAt).getTime() || 0,
    updatedAt: (task) => new Date(task.updatedAt).getTime() || 0
};

function accessors() {
    return SORT_CONFIG;
}

function renderRows(tasks, columns) {
    if (!tasks.length) {
        return '<tr><td colspan="' + (columns || 9) + '">'
            + App.emptyState('No tasks match', 'Adjust the filters above to widen the search.')
            + '</td></tr>';
    }
    /* The Client column is dropped for client accounts — every row on this
       page already belongs to their own customer. */
    const showClient = !Auth.isClient();
    return tasks.map((task) =>
        '<tr class="is-clickable" data-task-id="' + App.esc(task.id) + '" tabindex="0">'
        + '<td class="cell-taskno">' + App.esc(task.taskNo) + '</td>'
        + '<td class="cell-title">' + App.esc(task.title) + '</td>'
        + (showClient ? '<td>' + App.clientCell(task.client) + '</td>' : '')
        + '<td>' + App.statusBadge(task.status) + '</td>'
        + '<td>' + App.priorityBadge(task.priority) + '</td>'
        + '<td>' + App.userCell(task.assignedTo) + '</td>'
        + '<td>' + App.userCell(task.createdBy) + '</td>'
        + '<td class="cell-date" title="' + App.esc(App.formatDate(task.createdAt)) + '">'
        + App.esc(App.formatDate(task.createdAt)) + '</td>'
        + '<td class="cell-date" title="' + App.esc(App.formatDate(task.updatedAt)) + '">'
        + App.esc(App.formatDate(task.updatedAt)) + '</td>'
        + '</tr>').join('');
}

function setCount(visible, total) {
    const node = App.$('#all-tasks-count');
    if (node) {
        node.textContent = visible === total
            ? total + (total === 1 ? ' task' : ' tasks')
            : visible + ' of ' + total + ' tasks';
    }
}

function wireRowClicks(tbody) {
    const open = (row) => {
        window.location.href = '/task-detail.html?id=' + encodeURIComponent(row.dataset.taskId);
    };
    tbody.addEventListener('click', (event) => {
        const row = event.target.closest('tr[data-task-id]');
        if (row) {
            open(row);
        }
    });
    tbody.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter' && event.key !== ' ') {
            return;
        }
        const row = event.target.closest('tr[data-task-id]');
        if (row) {
            event.preventDefault();
            open(row);
        }
    });
}
