package com.jefiro.app247.domain.model.dto.appclient;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class TenantLinkContracts {
    private TenantLinkContracts() {}

    public record CreateInvitationRequest(
            @NotBlank String cpf,
            @NotBlank String condominioId
    ) {}

    public record CreateInvitationResponse(
            String token,
            Instant expiresAt,
            String condominioId
    ) {}

    public record ConsumeInvitationRequest(
            @NotBlank @Size(max = 200) String token
    ) {}

    public record ConsumeInvitationResponse(
            String status,
            String accessToken,
            String empresaId,
            String condominioId,
            String cpfVerificationStatus,
            boolean historicalPurchasesClaimed
    ) {}
}
