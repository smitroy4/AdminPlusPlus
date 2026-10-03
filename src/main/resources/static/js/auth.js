/* ==========================================================================
   auth.js - page bootstrap
   Guards every authenticated page: resolves /api/me, populates the header and
   highlights the active nav link. Redirects to /login.html on 401.
   ========================================================================== */
'use strict';

const Auth = (function () {

    let currentUser = null;

    function isManagerOrAbove() {
        return !!currentUser && (currentUser.role === 'MANAGER' || currentUser.role === 'ADMIN');
    }

    function isAdmin() {
        return !!currentUser && currentUser.role === 'ADMIN';
    }

    /** External customer accounts: scoped to their own customer's tasks. */
    function isClient() {
        return !!currentUser && currentUser.role === 'CLIENT';
    }

    /** @returns the signed-in user or null; never throws. */
    async function resolve(redirectOnFail) {
        try {
            const response = await App.getJson('/api/me', { allowUnauthenticated: true });
            currentUser = response.data;
            return currentUser;
        } catch (error) {
            if (error.status === 401 && redirectOnFail !== false) {
                App.goToLogin();
            }
            return null;
        }
    }

    function current() {
        return currentUser;
    }

    /** The user's role exactly as the header chip shows it. */
    function roleLabel(me) {
        return String((me && me.role) || '').replace('_', ' ');
    }

    /** Fills the shared chrome (header + footer). Safe to call on any authenticated page. */
    function renderHeader(user) {
        const me = user || currentUser;
        if (!me) {
            return;
        }

        const name = App.$('[data-auth-username]');
        if (name) {
            name.textContent = me.username;
        }

        const role = App.$('[data-auth-role]');
        if (role) {
            role.textContent = roleLabel(me);
        }

        const avatar = App.$('[data-auth-avatar]');
        if (avatar) {
            avatar.textContent = App.initials(me.username);
        }

        const email = App.$('[data-auth-email]');
        if (email) {
            email.textContent = me.email;
        }

        const footUser = App.$('[data-auth-footer-user]');
        if (footUser) {
            footUser.textContent = me.username + ' (' + roleLabel(me) + ')';
        }
    }

    function highlightNav() {
        const path = window.location.pathname.replace(/\/+$/, '') || '/index.html';
        App.$$('.nav__link').forEach((link) => {
            const href = link.getAttribute('href');
            if (href === path) {
                link.classList.add('is-active');
                link.setAttribute('aria-current', 'page');
            }
        });
    }

    /** Shows manager/admin-only nav entries. Call after renderHeader. */
    function applyRoleVisibility() {
        if (isManagerOrAbove()) {
            App.$$('[data-role="MANAGER"]').forEach((node) => node.classList.remove('is-hidden'));
        }
        if (isAdmin()) {
            App.$$('[data-role="ADMIN"]').forEach((node) => node.classList.remove('is-hidden'));
        } else {
            App.$$('[data-role="ADMIN"]').forEach((node) => node.classList.add('is-hidden'));
        }
        /* Clients are never assigned anything, so "My Tasks" is noise for them. */
        if (isClient()) {
            App.$$('[data-hide-when-client]').forEach((node) => node.classList.add('is-hidden'));
        }
    }

    function hide(element) {
        if (element) {
            element.classList.add('is-hidden');
        }
    }

    function show(element) {
        if (element) {
            element.classList.remove('is-hidden');
        }
    }

    function wireLogout() {
        const button = App.$('[data-logout]');
        if (!button) {
            return;
        }
        button.addEventListener('click', async (event) => {
            event.preventDefault();
            App.busy(button, true, 'Signing out…');
            try {
                /* Spring Security's logout endpoint only answers POST. */
                await App.postJson('/logout', null, { redirectOnForbidden: true });
            } catch (error) {
                /* even if the call fails, drop the client-side state */
            }
            window.location.href = '/login.html?logout';
        });
    }

    /* ------------------------------------------------------- notifications */

    /* Read state lives in localStorage: ids stay stable across reloads, so the
       badge is simply "feed ids minus read ids". Never sent to the server. */
    const NOTIF_READ_KEY = 'admin++-notif-read';
    let notifItems = [];

    function readNotifSet() {
        try {
            const ids = JSON.parse(window.localStorage.getItem(NOTIF_READ_KEY) || '[]');
            return new Set(Array.isArray(ids) ? ids : []);
        } catch (error) {
            return new Set();
        }
    }

    function saveNotifSet(ids) {
        try {
            window.localStorage.setItem(NOTIF_READ_KEY, JSON.stringify(Array.from(ids).slice(-500)));
        } catch (error) {
            /* private mode or quota — the badge just goes stale */
        }
    }

    function notifTypeLabel(type) {
        if (type === 'ASSIGNED') {
            return 'Assigned';
        }
        if (type === 'ESCALATION') {
            return 'Escalation';
        }
        return 'Message';
    }

    function renderNotifications() {
        const list = App.$('[data-notif-list]');
        const badge = App.$('[data-notif-count]');
        if (!list || !badge) {
            return;
        }

        const read = readNotifSet();
        const unread = notifItems.filter((item) => !read.has(item.id)).length;
        badge.textContent = String(unread);
        badge.classList.toggle('is-hidden', unread === 0);

        if (!notifItems.length) {
            list.innerHTML = '<div class="notif__empty">No notifications yet.</div>';
            return;
        }

        list.innerHTML = notifItems.map((item) => {
            const meta = [item.actor || '', item.createdAt ? App.formatRelative(item.createdAt) : '']
                .filter(Boolean).join(' · ');
            const snippet = item.snippet
                ? '<span class="notif__snippet">' + App.esc(item.snippet) + '</span>'
                : '';
            return '<button type="button" class="notif__item'
                + (read.has(item.id) ? '' : ' notif__item--unread')
                + '" data-notif-id="' + App.esc(item.id) + '" data-task-id="' + App.esc(item.taskId) + '">'
                + '<span class="notif__type notif__type--' + App.esc(item.type) + '">'
                + App.esc(notifTypeLabel(item.type)) + '</span>'
                + '<span class="notif__body">'
                + '<span class="notif__task">' + App.esc(item.taskNo) + ' · ' + App.esc(item.taskTitle) + '</span>'
                + '<span class="notif__summary">' + App.esc(item.summary) + '</span>'
                + snippet
                + (meta ? '<span class="notif__meta">' + App.esc(meta) + '</span>' : '')
                + '</span></button>';
        }).join('');
    }

    async function loadNotifications() {
        try {
            const response = await App.getJson('/api/notifications');
            notifItems = Array.isArray(response.data) ? response.data : [];
        } catch (error) {
            notifItems = [];
        }
        renderNotifications();
    }

    /** Drops the bell + dropdown in the header, immediately before logout. */
    function mountNotifications() {
        const logout = App.$('[data-logout]');
        if (!logout || document.querySelector('[data-notif-toggle]')) {
            return;
        }

        const wrap = document.createElement('div');
        wrap.className = 'notif';
        wrap.innerHTML = '<button type="button" class="btn btn--ghost btn--sm notif__btn"'
            + ' data-notif-toggle aria-haspopup="true" aria-expanded="false" aria-label="Notifications">'
            + '<span class="notif__bell" aria-hidden="true">&#128276;</span>'
            + '<span class="notif__count is-hidden" data-notif-count>0</span>'
            + '</button>'
            + '<div class="notif__panel is-hidden" data-notif-panel role="region" aria-label="Notifications">'
            + '<div class="notif__head"><span>Notifications</span>'
            + '<button type="button" class="notif__mark" data-notif-mark>Mark all read</button></div>'
            + '<div class="notif__list" data-notif-list>'
            + '<div class="notif__empty">Loading…</div></div></div>';
        logout.parentNode.insertBefore(wrap, logout);

        const toggle = wrap.querySelector('[data-notif-toggle]');
        const panel = wrap.querySelector('[data-notif-panel]');

        function close() {
            panel.classList.add('is-hidden');
            toggle.setAttribute('aria-expanded', 'false');
        }

        toggle.addEventListener('click', (event) => {
            event.stopPropagation();
            const willOpen = panel.classList.contains('is-hidden');
            panel.classList.toggle('is-hidden', !willOpen);
            toggle.setAttribute('aria-expanded', String(willOpen));
            if (willOpen) {
                loadNotifications();
            }
        });

        wrap.querySelector('[data-notif-mark]').addEventListener('click', () => {
            saveNotifSet(new Set(notifItems.map((item) => item.id)));
            renderNotifications();
        });

        wrap.querySelector('[data-notif-list]').addEventListener('click', (event) => {
            const item = event.target.closest('[data-notif-id]');
            if (!item) {
                return;
            }
            const ids = readNotifSet();
            ids.add(item.dataset.notifId);
            saveNotifSet(ids);
            const taskId = item.dataset.taskId;
            close();
            if (taskId) {
                window.location.href = '/task-detail.html?id=' + encodeURIComponent(taskId);
            }
        });

        document.addEventListener('click', (event) => {
            if (!wrap.contains(event.target)) {
                close();
            }
        });
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') {
                close();
            }
        });
    }

    /**
     * Entry point for every authenticated page.
     * @param {Function} [onReady] invoked with the user once the shell is ready
     */
    async function init(onReady) {
        const user = await resolve(true);
        if (!user) {
            return null;
        }
        renderHeader(user);
        highlightNav();
        applyRoleVisibility();
        wireLogout();
        mountNotifications();
        loadNotifications();
        if (typeof onReady === 'function') {
            await onReady(user);
        }
        return user;
    }

    return {
        init,
        resolve,
        current,
        renderHeader,
        highlightNav,
        isManagerOrAbove,
        isAdmin,
        isClient,
        hide,
        show,
        wireLogout
    };
})();
