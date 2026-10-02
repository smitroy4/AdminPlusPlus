package com.smit.taskportal.api.dto;

import com.smit.taskportal.domain.Client;

/** Compact client projection embedded in task payloads — name only. */
public record ClientSummaryDto(Long id, String name) {

    public static ClientSummaryDto from(Client client) {
        return client == null ? null : new ClientSummaryDto(client.getId(), client.getName());
    }
}
