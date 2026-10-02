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

    /** External customer accounts: read-only, scoped to their own tasks. */
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

    /** Fills the shared header chrome. Safe to call on any authenticated page. */
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
            role.textContent = String(me.role || '').replace('_', ' ');
        }

        const avatar = App.$('[data-auth-avatar]');
        if (avatar) {
            avatar.textContent = App.initials(me.username);
        }

        const email = App.$('[data-auth-email]');
        if (email) {
            email.textContent = me.email;
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
