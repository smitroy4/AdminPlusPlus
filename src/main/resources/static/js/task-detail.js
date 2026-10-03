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
        await Promise.all([wireStatus(detail), wireAssign(detail, user), wireComposer(detail, user),
            wireEscalation()]);
    }).catch((error) => App.showAlert('#detail-error', error.message, 'error'));
});

/* ------------------------------------------------------------------- load */

let state = {
    task: null, messages: [], canManage: false, isClient: false, currentUserId: null,
    escalations: [], escalated: false, escalationParticipant: false
};

async function loadTask(taskId, user) {
    state.currentUserId = user.id;
    state.canManage = Auth.isManagerOrAbove();
    state.isClient = Auth.isClient();
    state.isAdmin = Auth.isAdmin();
    state.userRole = user.role;
    state.taskId = taskId;
    try {
        const response = await App.getJson('/api/task/' + encodeURIComponent(taskId));
        state.task = response.data.task;
        state.messages = response.data.messages || [];
        state.escalations = response.data.escalations || [];
        state.escalated = !!response.data.escalated;
        state.escalationParticipant = !!response.data.escalationParticipant;
        renderHeader(state.task);
        renderClientDetails(response.data.clientDetails);
        renderThread(state.messages);
        renderMessageCount(state.task.id, state.messages.length);
        renderEscalation();
        await loadSubmissions(taskId);
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
    renderDescription(task);
    App.$('#detail-status-badge').innerHTML = App.statusBadge(task.status);
    App.$('#detail-priority-badge').innerHTML = App.priorityBadge(task.priority);

    const escalatedBadge = App.$('#detail-escalated-badge');
    if (escalatedBadge) {
        if (state.escalated) {
            Auth.show(escalatedBadge);
        } else {
            Auth.hide(escalatedBadge);
        }
    }

    App.$('#detail-assignee').innerHTML = App.userCell(task.assignedTo);
    App.$('#detail-client').innerHTML = App.clientCell(task.client);
    App.$('#detail-creator').innerHTML = App.userCell(task.createdBy);
    App.$('#detail-created').textContent = App.formatDate(task.createdAt);
    App.$('#detail-updated').textContent = App.formatDate(task.updatedAt);
    App.$('#detail-updated-meta').textContent = 'Last updated ' + App.formatRelative(task.updatedAt);

    /* Clients may comment, but never change status or take ownership. */
    if (state.isClient) {
        Auth.hide(App.$('#status-controls'));
        Auth.hide(App.$('#assign-to-me-wrap'));
    } else {
        Auth.show(App.$('#status-controls'));
    }
    Auth.show(App.$('#composer-section'));

    const stateSelect = App.$('#status-select');
    if (stateSelect) {
        ['OPEN', 'IN_PROGRESS', 'QUALITY', 'SUBMITTED', 'CLOSED'].forEach((value) => {
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

    /* One composer, one checkbox. Its meaning follows the role: managers and
       admins get "Internal note", client accounts get "Escalate to Manager",
       and the staff in between get no switch at all. */
    const internalToggle = App.$('#message-internal');
    const escalateToggle = App.$('#message-escalate');
    if (internalToggle) {
        internalToggle.checked = false;
    }
    if (escalateToggle) {
        escalateToggle.checked = false;
    }
    toggleOption(App.$('#message-internal-wrap'), state.canManage);
    toggleOption(App.$('#message-escalate-wrap'), state.isClient);
}

function toggleOption(node, visible) {
    if (!node) {
        return;
    }
    if (visible) {
        Auth.show(node);
    } else {
        Auth.hide(node);
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

/* ------------------------------------------------------------- escalation */

/**
 * The escalation card is display-only: the composer above owns the writing, via
 * its "Escalate to Manager" switch. The card therefore appears once — and only
 * once — the conversation exists and the caller is part of it (managers/admins,
 * plus the client who escalated).
 */
function renderEscalation() {
    const section = App.$('#escalation-section');
    if (!section) {
        return;
    }
    if (!state.escalated || !state.escalationParticipant) {
        Auth.hide(section);
        return;
    }
    Auth.show(section);

    App.$('#escalation-title').textContent = 'Escalation conversation';
    App.$('#escalation-intro').textContent = 'Private thread between the escalating client, '
        + 'managers and admins. Post into it by ticking "Escalate to Manager" in the composer above.';
    const count = App.$('#escalation-count');
    count.textContent = state.escalations.length
        + (state.escalations.length === 1 ? ' message' : ' messages');
    renderEscalationThread(state.escalations);
}

function renderEscalationThread(messages) {
    const host = App.$('#escalation-thread');
    if (!host) {
        return;
    }
    if (!messages || messages.length === 0) {
        host.innerHTML = '<div class="thread__empty">No escalation messages yet.</div>';
        return;
    }
    host.innerHTML = messages.map(renderEscalationMessage).join('');
    host.scrollTop = host.scrollHeight;
}

function renderEscalationMessage(message) {
    const mine = message.fromUser && message.fromUser.id === state.currentUserId;
    /* Deleting is an admin power, and only ever an admin power. */
    const canDelete = state.isAdmin;

    return '<div class="msg ' + (mine ? 'msg--mine' : 'msg--other') + ' msg--escalation">'
        + '<div class="msg__head">'
        + '<span class="msg__author">' + App.esc(message.fromUser ? message.fromUser.username : 'Unknown') + '</span>'
        + (message.fromUser ? '<span class="msg__role">' + App.esc(message.fromUser.role) + '</span>' : '')
        + '<span>' + App.esc(App.formatRelative(message.createdAt)) + '</span>'
        + (canDelete ? '<button type="button" class="msg__del" data-delete-escalation="' + App.esc(message.id) + '">delete</button>' : '')
        + '</div>'
        + '<div class="msg__bubble">' + App.escMultiline(message.messageBody) + '</div>'
        + '<div class="msg__tag">&#128274; Escalation</div>'
        + '</div>';
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

    /* The description already sits under the title, so the message that seeded
       the thread with it is dropped rather than printed twice. */
    const first = messages[0];
    const isSeededDescription = !!state.task.description
        && first.messageBody === state.task.description && !first.internal;

    host.innerHTML = messages.map((message, index) =>
        renderMessage(message, index === 0 && isSeededDescription)).join('');

    host.scrollTop = host.scrollHeight;
}

/**
 * The task brief, rendered straight under the title and before the badges.
 * Multiline descriptions keep their line breaks.
 */
function renderDescription(task) {
    const node = App.$('#detail-description');
    if (!node) {
        return;
    }
    if (!task.description) {
        Auth.hide(node);
        return;
    }
    Auth.show(node);
    node.textContent = task.description;
}

function renderMessage(message, skip) {
    if (skip) {
        return '';
    }
    const mine = message.fromUser && message.fromUser.id === state.currentUserId;
    /* Deleting is an admin power — the server refuses everybody else. */
    const canDelete = state.isAdmin;

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
        const escalateToggle = App.$('#message-escalate');
        const internal = !!(internalToggle && internalToggle.checked);
        const escalate = !!(escalateToggle && escalateToggle.checked);
        /* Associates never talk in the thread: their update becomes a submission. */
        const isAssociate = state.userRole === 'ASSOCIATE';

        App.busy(submit, true,
            escalate ? 'Escalating…' : (isAssociate ? 'Submitting…' : 'Posting…'));
        try {
            if (isAssociate) {
                await App.postJson('/api/task/' + detail.task.id + '/associate-submission',
                    { content: body });
                textarea.value = '';
                await loadSubmissions(detail.task.id);
                await refreshStatus(detail);
                App.toast('Update submitted for review', 'success');
                return;
            }

            const response = escalate
                ? await App.postJson('/api/task/' + detail.task.id + '/escalate', { messageBody: body })
                : await App.postJson('/api/task/' + detail.task.id + '/message',
                    { messageBody: body, internal: internal });
            textarea.value = '';
            if (internalToggle) {
                internalToggle.checked = false;
            }
            if (escalateToggle) {
                escalateToggle.checked = false;
            }

            if (escalate) {
                recordEscalation(response.data);
            } else {
                state.messages.push(response.data);
                renderThread(state.messages);
                renderMessageCount(detail.task.id, state.messages.length);
                /* A reviewer's reply may have advanced the workflow (Quality → Submitted). */
                await refreshStatus(detail);
            }
            App.toast(escalate ? 'Escalated to a manager' : 'Message posted', 'success');
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

/* ----------------------------------------------------------- escalation IO */

/** Applies a fresh escalation message to the page state and re-renders the card. */
function recordEscalation(message) {
    const first = !state.escalated;
    if (first) {
        state.escalated = true;
        state.escalationParticipant = true;
        if (state.task && state.task.priority !== 'URGENT') {
            state.task.priority = 'URGENT';
            App.$('#detail-priority-badge').innerHTML = App.priorityBadge('URGENT');
        }
        Auth.show(App.$('#detail-escalated-badge'));
    }
    state.escalations.push(message);
    renderEscalation();
}

function wireEscalation() {
    /* Delete only — writing happens in the shared composer. The handler is
       delegated, because the rows are re-rendered after every post. */
    const thread = App.$('#escalation-thread');
    if (!thread) {
        return;
    }
    thread.addEventListener('click', async (event) => {
        const button = event.target.closest('[data-delete-escalation]');
        if (!button) {
            return;
        }
        if (!window.confirm('Delete this escalation message? This cannot be undone.')) {
            return;
        }
        const messageId = button.dataset.deleteEscalation;
        button.disabled = true;
        try {
            await App.delJson('/api/task/' + state.task.id + '/message/' + encodeURIComponent(messageId));
            state.escalations = state.escalations.filter(
                (message) => String(message.id) !== String(messageId));
            renderEscalation();
            App.toast('Message deleted', 'success');
        } catch (error) {
            button.disabled = false;
            App.toast(error.message, 'error');
        }
    });
}

/* ----------------------------------------------------------- submissions */

/**
 * The accordions in their own "Submitted for Quality Approval" section, below
 * the composer. Every internal role reads exactly the same list — newest on
 * top, nothing ever pruned — so the review workflow looks the same for
 * associates, coordinators, managers and admins alike. Client accounts never
 * see internal review traffic, so their section stays hidden.
 */
async function loadSubmissions(taskId) {
    const section = App.$('#submissions-section');
    const container = App.$('#submissions-list');
    if (!section || !container) {
        return;
    }
    if (state.isClient) {
        container.innerHTML = '';
        Auth.hide(section);
        return;
    }
    try {
        const response = await App.getJson('/api/task/' + encodeURIComponent(taskId) + '/associate-submissions');
        const submissions = response.data || [];
        container.innerHTML = submissions.length
            ? submissions.map((sub) => renderSubmissionAccordion(sub)).join('')
            : App.emptyState('Nothing waiting for quality approval',
                'Updates posted by an associate from the composer above land here.');
        Auth.show(section);
        container.querySelectorAll('[data-accordion-toggle]').forEach((head) => {
            head.addEventListener('click', () => toggleSubmissionAccordion(head));
        });
    } catch (error) {
        container.innerHTML = '';
        Auth.hide(section);
    }
}

/** Opens / closes one accordion; the others keep whatever state they were in. */
function toggleSubmissionAccordion(head) {
    const body = head.nextElementSibling;
    if (!body) {
        return;
    }
    const wasOpen = body.style.display !== 'none';
    body.style.display = wasOpen ? 'none' : 'block';
    const caret = head.querySelector('[data-accordion-caret]');
    if (caret) {
        caret.textContent = wasOpen ? '\u25B8' : '\u25BE';
    }
}

function renderSubmissionAccordion(sub) {
    const employeeName = sub.employee ? (sub.employee.displayName || sub.employee.username) : 'Employee';
    const time = sub.createdAt ? App.formatRelative(sub.createdAt) : '';
    const title = 'Submitted by ' + App.esc(employeeName) + ' | ' + App.esc(time);
    return '<div class="card" style="margin-bottom:12px">'
        + '<div class="card__head" style="cursor:pointer" data-accordion-toggle>'
        + '<h4 style="margin:0">' + title
        + '<span class="muted" style="margin-left:8px" data-accordion-caret>&#9656;</span></h4>'
        + '</div>'
        + '<div class="card__body" style="display:none">'
        + '<div style="white-space:pre-wrap">' + App.escMultiline(sub.content) + '</div>'
        + '</div>'
        + '</div>';
}

/**
 * Re-reads the task after a post: the backend moves the workflow on by itself
 * (Quality after an associate's update, Submitted after a reviewer's reply), so
 * the badge and the status selector are re-synced from the server.
 */
async function refreshStatus(detail) {
    try {
        const response = await App.getJson('/api/task/' + encodeURIComponent(detail.task.id));
        state.task = response.data.task;
        detail.task = response.data.task;
        App.$('#detail-status-badge').innerHTML = App.statusBadge(state.task.status);
        const select = App.$('#status-select');
        if (select) {
            select.value = state.task.status;
        }
    } catch (error) {
        /* Nothing to resync - the header simply keeps showing what it had. */
    }
}
