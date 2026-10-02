/* ==========================================================================
   task-detail.js - task header, status/assignment controls and the message
   thread for a single task (?id=<taskId>).
   ========================================================================== */
'use strict';

document.addEventListener('DOMContentLoaded', () => {
    Auth.init(async (user) => {
        const taskId = new URLSearchParams(window.location.search).get('id');
        if (!taskId || !/^\d+$/.test(taskId)) {
            App.showAlert('#detail-error', 'No valid task id was supplied in the URL.', 'error');
            return;
        }
        const detail = await loadTask(taskId, user);
        if (!detail) {
            return;
        }
        await Promise.all([wireStatus(detail), wireAssign(detail, user), wireComposer(detail, user)]);
    }).catch((error) => App.showAlert('#detail-error', error.message, 'error'));
});

/* ------------------------------------------------------------------- load */

let state = { task: null, messages: [], canManage: false, isClient: false, currentUserId: null };

async function loadTask(taskId, user) {
    state.currentUserId = user.id;
    state.canManage = Auth.isManagerOrAbove();
    state.isClient = Auth.isClient();
    try {
        const response = await App.getJson('/api/task/' + encodeURIComponent(taskId));
        state.task = response.data.task;
        state.messages = response.data.messages || [];
        renderHeader(state.task);
        renderClientDetails(response.data.clientDetails);
        renderThread(state.messages);
        renderMessageCount(state.task.id, state.messages.length);
        return state;
    } catch (error) {
        const node = App.$('#detail-error');
        if (node) {
            App.showAlert('#detail-error',
                error.status === 404 ? 'That task does not exist.' : error.message, 'error');
        }
        const content = App.$('#detail-content');
        if (content) {
            content.classList.add('is-hidden');
        }
        return null;
    }
}

function renderHeader(task) {
    document.title = task.taskNo + ' - ' + task.title + ' | Admin++';

    App.$('#detail-taskno').textContent = task.taskNo;
    App.$('#detail-title').textContent = task.title;
    App.$('#detail-status-badge').innerHTML = App.statusBadge(task.status);
    App.$('#detail-priority-badge').innerHTML = App.priorityBadge(task.priority);
    App.$('#detail-assignee').innerHTML = App.userCell(task.assignedTo);
    App.$('#detail-client').innerHTML = App.clientCell(task.client);
    App.$('#detail-creator').innerHTML = App.userCell(task.createdBy);
    App.$('#detail-created').textContent = App.formatDate(task.createdAt);
    App.$('#detail-updated').textContent = App.formatDate(task.updatedAt);
    App.$('#detail-updated-meta').textContent = 'Last updated ' + App.formatRelative(task.updatedAt);

    /* Client accounts are read-only: no status changes, no claiming. */
    if (state.isClient) {
        Auth.hide(App.$('#status-controls'));
        Auth.hide(App.$('#assign-to-me-wrap'));
        Auth.hide(App.$('#composer-section'));
    } else {
        Auth.show(App.$('#status-controls'));
        Auth.show(App.$('#composer-section'));
    }

    const stateSelect = App.$('#status-select');
    if (stateSelect) {
        ['OPEN', 'IN_PROGRESS', 'COMPLETED', 'CLOSED'].forEach((value) => {
            const option = document.createElement('option');
            option.value = value;
            option.textContent = App.statusLabel(value);
            stateSelect.appendChild(option);
        });
        stateSelect.value = task.status;
    }

    Auth.hide(App.$('#assign-to-me-wrap'));
    if (!state.isClient && (!task.assignedTo || task.assignedTo.id !== state.currentUserId)) {
        Auth.show(App.$('#assign-to-me-wrap'));
        App.$('#assign-to-me').disabled = !state.canManage && !!task.assignedTo;
        if (!state.canManage && task.assignedTo) {
            App.$('#assign-to-me').title = 'This task already belongs to ' + task.assignedTo.username;
        }
    }

    if (state.canManage) {
        Auth.show(App.$('#assign-manager-wrap'));
        loadAssignableUsers();
        if (App.$('#assign-select')) {
            App.$('#assign-select').value = task.assignedTo ? String(task.assignedTo.id) : '';
        }
        Auth.show(App.$('#unassign-btn'));
    } else {
        Auth.hide(App.$('#assign-manager-wrap'));
        Auth.hide(App.$('#unassign-btn'));
    }

    const internalToggle = App.$('#message-internal');
    if (internalToggle) {
        if (state.canManage) {
            internalToggle.checked = false;
        } else {
            internalToggle.closest('.checkbox').classList.add('is-hidden');
        }
    }
}

function renderMessageCount(taskId, count) {
    const node = App.$('#message-count');
    if (node) {
        node.textContent = count + (count === 1 ? ' message' : ' messages');
    }
}

/** Contact block: only ever delivered to managers/admins by the API. */
function renderClientDetails(details) {
    const card = App.$('#client-details-card');
    if (!card) {
        return;
    }
    if (!details) {
        Auth.hide(card);
        return;
    }
    App.$('#client-contact').textContent = details.contactName || '—';
    App.$('#client-email').textContent = details.email || '—';
    App.$('#client-phone').textContent = details.phone || '—';
    App.$('#client-notes').textContent = details.notes || '—';
    Auth.show(card);
}

/* ----------------------------------------------------------------- thread */

function renderThread(messages) {
    const host = App.$('#thread');
    if (!host) {
        return;
    }
    if (!messages || messages.length === 0) {
        host.innerHTML = '<div class="thread__empty">No messages yet. Start the conversation below.</div>';
        return;
    }

    const first = messages[0];
    const description = state.task.description;
    const showDescriptionCard = !!description && messages.length > 0
        && first.messageBody === description && !first.internal;

    host.innerHTML = (showDescriptionCard ? renderDescriptionCard() : '')
        + messages.map((message, index) => renderMessage(message, index === 0 && showDescriptionCard)).join('');

    host.scrollTop = host.scrollHeight;
}

function renderDescriptionCard() {
    return '<div class="msg msg--other msg--original">'
        + '<div class="msg__head"><span class="msg__author">'
        + App.esc(state.task.createdBy ? state.task.createdBy.username : 'Unknown')
        + '</span><span>created this task</span><span>'
        + App.esc(App.formatDate(state.task.createdAt)) + '</span></div>'
        + '<div class="msg__bubble">' + App.escMultiline(state.task.description) + '</div>'
        + '</div>';
}

function renderMessage(message, skip) {
    if (skip) {
        return '';
    }
    const mine = message.fromUser && message.fromUser.id === state.currentUserId;
    const canDelete = mine || state.canManage;

    return '<div class="msg ' + (mine ? 'msg--mine' : 'msg--other')
        + (message.internal ? ' msg--internal' : '') + '">'
        + '<div class="msg__head">'
        + '<span class="msg__author">' + App.esc(message.fromUser ? message.fromUser.username : 'Unknown') + '</span>'
        + (message.fromUser ? '<span class="msg__role">' + App.esc(message.fromUser.role) + '</span>' : '')
        + '<span>' + App.esc(App.formatRelative(message.createdAt)) + '</span>'
        + (canDelete ? '<button type="button" class="msg__del" data-delete-message="' + App.esc(message.id) + '">delete</button>' : '')
        + '</div>'
        + '<div class="msg__bubble">' + App.escMultiline(message.messageBody) + '</div>'
        + (message.internal ? '<div class="msg__tag">&#128274; Internal note</div>' : '')
        + '</div>';
}

/* ----------------------------------------------------------------- status */

function wireStatus(detail) {
    const select = App.$('#status-select');
    const button = App.$('#status-apply');
    if (!select || !button) {
        return;
    }

    select.addEventListener('change', () => {
        App.hideAlert('#status-alert');
    });

    button.addEventListener('click', async () => {
        if (select.value === detail.task.status) {
            return;
        }
        App.hideAlert('#status-alert');
        App.busy(button, true, 'Saving…');
        try {
            const response = await App.patchJson('/api/task/' + detail.task.id + '/status', { status: select.value });
            detail.task = response.data;
            state.task = response.data;
            App.$('#detail-status-badge').innerHTML = App.statusBadge(response.data.status);
            App.toast(response.message || 'Status updated', 'success');
        } catch (error) {
            select.value = detail.task.status;
            App.showAlert('#status-alert', error.message, 'error');
        } finally {
            App.busy(button, false);
        }
    });
}

/* ----------------------------------------------------------------- assign */

async function loadAssignableUsers() {
    const select = App.$('#assign-select');
    if (!select || select.dataset.loaded === 'true') {
        return;
    }
    try {
        const response = await App.getJson('/api/manager/users');
        const me = Auth.current();
        select.innerHTML = '<option value="">— Unassigned —</option>'
            + response.data.filter((user) => !me || user.id !== me.id).map((user) =>
                '<option value="' + App.esc(user.id) + '">' + App.esc(user.username)
                + ' (' + App.esc(user.role) + ')</option>').join('');
        select.dataset.loaded = 'true';
    } catch (error) {
        select.innerHTML = '<option value="">— Unavailable —</option>';
    }
}

function wireAssign(detail, user) {
    const mineButton = App.$('#assign-to-me');
    if (mineButton) {
        mineButton.addEventListener('click', () => assign(detail, user.id, mineButton));
    }

    const applyButton = App.$('#assign-apply');
    const select = App.$('#assign-select');
    if (applyButton && select) {
        applyButton.addEventListener('click', () => {
            if (!select.value) {
                App.showAlert('#assign-alert', 'Pick a user to assign the task to.', 'error');
                return;
            }
            assign(detail, Number(select.value), applyButton);
        });
    }

    const unassign = App.$('#unassign-btn');
    if (unassign) {
        unassign.addEventListener('click', () => unassignTask(detail, unassign));
    }
}

async function assign(detail, userId, button) {
    App.hideAlert('#assign-alert');
    App.busy(button, true, 'Assigning…');
    try {
        const response = await App.postJson('/api/task/' + detail.task.id + '/assign', { userId: userId });
        state.task = response.data;
        detail.task = response.data;
        App.$('#detail-assignee').innerHTML = App.userCell(response.data.assignedTo);
        const select = App.$('#assign-select');
        if (select) {
            select.value = response.data.assignedTo ? String(response.data.assignedTo.id) : '';
        }
        renderHeader(response.data);
        App.toast(response.message || 'Task assigned', 'success');
    } catch (error) {
        App.showAlert('#assign-alert', error.message, 'error');
    } finally {
        App.busy(button, false);
    }
}

async function unassignTask(detail, button) {
    App.hideAlert('#assign-alert');
    App.busy(button, true, '…');
    try {
        const response = await App.delJson('/api/task/' + detail.task.id + '/assign');
        state.task = response.data;
        detail.task = response.data;
        App.$('#detail-assignee').innerHTML = App.userCell(null);
        const select = App.$('#assign-select');
        if (select) {
            select.value = '';
        }
        renderHeader(response.data);
        App.toast(response.message || 'Task unassigned', 'success');
    } catch (error) {
        App.showAlert('#assign-alert', error.message, 'error');
    } finally {
        App.busy(button, false);
    }
}

/* --------------------------------------------------------------- composer */

function wireComposer(detail, user) {
    const form = App.$('#message-form');
    const textarea = App.$('#message-body');
    const submit = App.$('#message-submit');
    if (!form || !textarea || !submit) {
        return;
    }

    /* Ctrl/Cmd + Enter submits. */
    textarea.addEventListener('keydown', (event) => {
        if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
            form.requestSubmit();
        }
    });

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        App.hideAlert('#message-alert');

        const body = textarea.value.trim();
        if (!body) {
            App.showAlert('#message-alert', 'Write something before posting.', 'error');
            return;
        }

        const internalToggle = App.$('#message-internal');
        const internal = !!(internalToggle && internalToggle.checked);

        App.busy(submit, true, 'Posting…');
        try {
            const response = await App.postJson('/api/task/' + detail.task.id + '/message', {
                messageBody: body,
                internal: internal
            });
            textarea.value = '';
            if (internalToggle) {
                internalToggle.checked = false;
            }
            state.messages.push(response.data);
            renderThread(state.messages);
            renderMessageCount(detail.task.id, state.messages.length);
            App.toast('Message posted', 'success');
        } catch (error) {
            App.showAlert('#message-alert', error.message, 'error');
        } finally {
            App.busy(submit, false);
        }
    });

    /* Delete a message (delegated, because rows are re-rendered). */
    const thread = App.$('#thread');
    if (thread) {
        thread.addEventListener('click', async (event) => {
            const button = event.target.closest('[data-delete-message]');
            if (!button) {
                return;
            }
            if (!window.confirm('Delete this message? This cannot be undone.')) {
                return;
            }
            const messageId = button.dataset.deleteMessage;
            button.disabled = true;
            try {
                await App.delJson('/api/task/' + detail.task.id + '/message/' + encodeURIComponent(messageId));
                state.messages = state.messages.filter((message) => String(message.id) !== String(messageId));
                renderThread(state.messages);
                renderMessageCount(detail.task.id, state.messages.length);
                App.toast('Message deleted', 'success');
            } catch (error) {
                button.disabled = false;
                App.toast(error.message, 'error');
            }
        });
    }
}
