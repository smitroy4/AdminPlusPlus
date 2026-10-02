/* ==========================================================================
   clients.js - manager/admin catalogue of client companies and their
   contact details (GET /api/clients returns the full record for these roles).
   ========================================================================== */
'use strict';

document.addEventListener('DOMContentLoaded', () => {
    Auth.init(async () => {
        if (!Auth.isManagerOrAbove()) {
            App.showAlert('#clients-error', 'This page is restricted to managers and administrators.', 'error');
            Auth.hide(App.$('#clients-content'));
            return;
        }
        await loadClients();
    }).catch((error) => App.showAlert('#clients-error', error.message, 'error'));
});

async function loadClients() {
    const tbody = App.$('#clients-body');
    try {
        const response = await App.getJson('/api/clients');
        render(response.data || []);
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="5">' + App.emptyState('Could not load clients', error.message) + '</td></tr>';
    }
}

function render(clients) {
    const tbody = App.$('#clients-body');
    if (!clients.length) {
        tbody.innerHTML = '<tr><td colspan="5">' + App.emptyState('No clients yet') + '</td></tr>';
        setCount(0);
        return;
    }

    App.makeSortable(App.$('#clients-table'), clients,
        (sorted) => {
            tbody.innerHTML = sorted.map(renderRow).join('');
            setCount(sorted.length);
        },
        {
            name: (client) => client.name,
            contactName: (client) => client.contactName || '',
            email: (client) => client.email || '',
            phone: (client) => client.phone || ''
        },
        'name', 'asc');
}

function renderRow(client) {
    return '<tr>'
        + '<td class="cell-title">' + App.esc(client.name) + '</td>'
        + '<td>' + valueOrDash(client.contactName) + '</td>'
        + '<td>' + valueOrDash(client.email) + '</td>'
        + '<td>' + valueOrDash(client.phone) + '</td>'
        + '<td class="cell-sub">' + valueOrDash(client.notes) + '</td>'
        + '</tr>';
}

function valueOrDash(text) {
    return text ? App.esc(text) : '<span class="muted">&ndash;</span>';
}

function setCount(total) {
    const node = App.$('#clients-count');
    if (node) {
        node.textContent = total + (total === 1 ? ' client' : ' clients');
    }
}
