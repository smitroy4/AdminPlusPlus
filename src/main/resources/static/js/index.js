/* ==========================================================================
   index.js - entry point: bounce to the dashboard or the login page.
   ========================================================================== */
'use strict';

document.addEventListener('DOMContentLoaded', async () => {
    try {
        const response = await App.getJson('/api/me', { allowUnauthenticated: true });
        window.location.replace(response.success ? '/dashboard.html' : '/login.html');
    } catch (error) {
        window.location.replace('/login.html');
    }
});
