package com.smit.taskportal.service;

import com.smit.taskportal.api.dto.ClientDto;
import com.smit.taskportal.domain.Client;
import com.smit.taskportal.exception.ResourceNotFoundException;
import com.smit.taskportal.repository.ClientRepository;
import com.smit.taskportal.security.CurrentUserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Client catalogue reads. The name is public to every authenticated role
 * (it is stamped on tasks and used by the All Tasks filter); the contact
 * block is only ever handed back to MANAGER and ADMIN.
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
        boolean full = currentUser.require().isManagerOrAbove();
        return clientRepository.findAllByOrderByNameAsc().stream()
                .map(client -> full ? ClientDto.detailed(client) : ClientDto.summary(client))
                .toList();
    }

    public ClientDto get(Long id) {
        Client client = getEntity(id);
        return currentUser.require().isManagerOrAbove()
                ? ClientDto.detailed(client)
                : ClientDto.summary(client);
    }

    public Client getEntity(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Client", id));
    }
}
