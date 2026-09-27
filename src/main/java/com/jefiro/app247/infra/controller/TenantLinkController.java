package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.appclient.TenantLinkContracts.ConsumeInvitationRequest;
import com.jefiro.app247.domain.model.dto.appclient.TenantLinkContracts.ConsumeInvitationResponse;
import com.jefiro.app247.domain.model.dto.appclient.TenantLinkContracts.CreateInvitationRequest;
import com.jefiro.app247.domain.model.dto.appclient.TenantLinkContracts.CreateInvitationResponse;
import com.jefiro.app247.infra.service.TenantLinkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class TenantLinkController {
    private final TenantLinkService service;

    public TenantLinkController(TenantLinkService service) {
        this.service = service;
    }

    @PostMapping("/admin/app-cliente/tenant-invitations")
    public ResponseEntity<CreateInvitationResponse> create(
            @Valid @RequestBody CreateInvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createInvitation(request.cpf(), request.condominioId()));
    }

    @PostMapping("/app-cliente/tenant-link")
    public ConsumeInvitationResponse consume(@Valid @RequestBody ConsumeInvitationRequest request) {
        return service.consumeInvitation(request.token());
    }
}
