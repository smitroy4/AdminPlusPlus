package com.smit.taskportal.api.dto;

import com.smit.taskportal.domain.Client;

/**
 * Client record. The contact block only carries values for MANAGER / ADMIN
 * callers — {@link #summary(Client)} blanks it for everybody else, so the
 * server never even serialises the details to an unauthorised role.
 */
public record ClientDto(Long id,
                        String name,
                        String contactName,
                        String email,
                        String phone,
                        String notes) {

    /** Full record — managers and admins. */
    public static ClientDto detailed(Client client) {
        if (client == null) {
            return null;
        }
        return new ClientDto(client.getId(), client.getName(), client.getContactName(),
                client.getEmail(), client.getPhone(), client.getNotes());
    }

    /** Name only — everybody else. */
    public static ClientDto summary(Client client) {
        if (client == null) {
            return null;
        }
        return new ClientDto(client.getId(), client.getName(), null, null, null, null);
    }
}
