package com.smit.taskportal.controller;

import com.smit.taskportal.api.dto.ApiResponse;
import com.smit.taskportal.api.dto.ClientDto;
import com.smit.taskportal.service.ClientService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Client catalogue. The name is public to every authenticated role; the
 * contact block is stripped for anyone below MANAGER (see {@link ClientService}).
 */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    /** All clients — name only for most roles, full details for managers/admins. */
    @GetMapping
    public ApiResponse<List<ClientDto>> list() {
        return ApiResponse.ok(clientService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<ClientDto> get(@PathVariable Long id) {
        return ApiResponse.ok(clientService.get(id));
    }
}
