package com.smit.taskportal.service;

import com.smit.taskportal.api.dto.ClientDto;
import com.smit.taskportal.domain.Client;
import com.smit.taskportal.exception.ResourceNotFoundException;
import com.smit.taskportal.repository.ClientRepository;
import com.smit.taskportal.security.AppUserPrincipal;
import com.smit.taskportal.security.CurrentUserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Client catalogue reads. The name is public to every staff role (it is
 * stamped on tasks and used by the All Tasks filter); the contact block is
 * only ever handed back to MANAGER and ADMIN.
 *
 * <p>A CLIENT account is the exception: it is bound to exactly one customer,
 * so the catalogue collapses to that single entry and any other id answers
 * with the same 404 an unknown id would — the platform never confirms that
 * another customer's record exists.
 */
@Service
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clientRepository;
    private final CurrentUserHolder currentUser;

    public ClientService(ClientRepository clientRepository, CurrentUserHolder currentUser) {
        this.clientRepository = clientRepository;
        this.currentUser = currentUser;
    }

    public List<ClientDto> list() {
        AppUserPrincipal actor = currentUser.require();
        boolean full = actor.isManagerOrAbove();
        if (actor.isClient()) {
            return ownCustomer(actor).stream()
                    .map(client -> full ? ClientDto.detailed(client) : ClientDto.summary(client))
                    .toList();
        }
        return clientRepository.findAllByOrderByNameAsc().stream()
                .map(client -> full ? ClientDto.detailed(client) : ClientDto.summary(client))
                .toList();
    }

    public ClientDto get(Long id) {
        AppUserPrincipal actor = currentUser.require();
        Client client = getEntity(id);
        if (actor.isClient() && !Objects.equals(client.getId(), actor.clientId())) {
            throw ResourceNotFoundException.of("Client", id);
        }
        return actor.isManagerOrAbove() ? ClientDto.detailed(client) : ClientDto.summary(client);
    }

    public Client getEntity(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Client", id));
    }

    /** The single customer a client account is linked to, if any. */
    private List<Client> ownCustomer(AppUserPrincipal actor) {
        if (actor.clientId() == null) {
            return List.of();
        }
        return clientRepository.findById(actor.clientId()).map(List::of).orElse(List.of());
    }
}
