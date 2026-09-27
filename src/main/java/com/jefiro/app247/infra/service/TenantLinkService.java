package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.appclient.TenantLinkInvitation;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.appclient.TenantLinkContracts.ConsumeInvitationResponse;
import com.jefiro.app247.domain.model.dto.appclient.TenantLinkContracts.CreateInvitationResponse;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.CondominioRepository;
import com.jefiro.app247.infra.repository.TenantLinkInvitationRepository;
import com.jefiro.app247.infra.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class TenantLinkService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantLinkInvitationRepository invitations;
    private final UserRepository users;
    private final CondominioRepository condominios;
    private final CpfService cpfs;
    private final CustomerIdentityHashService identities;
    private final HistoricalPurchaseOwnershipService historicalPurchases;
    private final TokenService tokens;
    private final Duration invitationTtl;

    public TenantLinkService(TenantLinkInvitationRepository invitations,
                             UserRepository users,
                             CondominioRepository condominios,
                             CpfService cpfs,
                             CustomerIdentityHashService identities,
                             HistoricalPurchaseOwnershipService historicalPurchases,
                             TokenService tokens,
                             @Value("${app247.tenant-link.invitation-ttl:PT72H}") Duration invitationTtl) {
        this.invitations = invitations;
        this.users = users;
        this.condominios = condominios;
        this.cpfs = cpfs;
        this.identities = identities;
        this.historicalPurchases = historicalPurchases;
        this.tokens = tokens;
        if (invitationTtl.isZero() || invitationTtl.isNegative()) {
            throw new IllegalStateException("app247.tenant-link.invitation-ttl deve ser positivo");
        }
        this.invitationTtl = invitationTtl;
    }

    @Transactional
    public CreateInvitationResponse createInvitation(String rawCpf, String condominioId) {
        User manager = authenticatedUser();
        String empresaId = EmpresaContext.require();
        if (manager.getEmpresa() == null || !empresaId.equals(manager.getEmpresa().getId())) {
            throw forbidden("TENANT_MISMATCH", "Gestor não pertence à empresa autenticada");
        }
        Condominio condominio = condominios.findByIdCondominioAndEmpresaId(condominioId, empresaId)
                .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                .orElseThrow(() -> new ApiBusinessException(HttpStatus.NOT_FOUND,
                        "CONDOMINIUM_NOT_FOUND", "Condomínio não encontrado"));

        String token = randomToken();
        Instant expiresAt = Instant.now().plus(invitationTtl);
        TenantLinkInvitation invitation = new TenantLinkInvitation();
        invitation.setEmpresa(manager.getEmpresa());
        invitation.setCondominio(condominio);
        invitation.setCpfHash(identities.hashCpf(cpfs.requireValid(rawCpf)));
        invitation.setTokenHash(sha256(token));
        invitation.setCreatedBy(manager);
        invitation.setExpiresAt(expiresAt);
        invitations.save(invitation);
        return new CreateInvitationResponse(token, expiresAt, condominio.getIdCondominio());
    }

    @Transactional
    public ConsumeInvitationResponse consumeInvitation(String rawToken) {
        User principal = authenticatedUser();
        User user = users.findByIdForUpdate(principal.getIdUser())
                .orElseThrow(() -> new ApiBusinessException(HttpStatus.UNAUTHORIZED,
                        "AUTH_REQUIRED", "Usuário autenticado não encontrado"));

        TenantLinkInvitation invitation = invitations.findByTokenHashForUpdate(sha256(rawToken))
                .orElseThrow(TenantLinkService::invalidInvitation);
        Instant now = Instant.now();

        if (invitation.getUsedAt() != null) {
            if (invitation.getUsedBy() != null
                    && invitation.getUsedBy().getIdUser().equals(user.getIdUser())
                    && user.getEmpresa() != null && user.getCondominio() != null) {
                return response(user, historicalPurchases.claimVerified(user));
            }
            throw invalidInvitation();
        }
        if (!invitation.getExpiresAt().isAfter(now)) throw invalidInvitation();
        if (!Boolean.TRUE.equals(invitation.getEmpresa().getAtivo())
                || invitation.getEmpresa().getEncerradaEm() != null
                || !Boolean.TRUE.equals(invitation.getCondominio().getAtivo())) {
            throw invalidInvitation();
        }
        if (user.getEmpresa() != null || user.getCondominio() != null) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "TENANT_LINK_ALREADY_EXISTS",
                    "A conta já possui vínculo de tenant");
        }
        String userCpfHash = identities.hashCpf(cpfs.requireValid(user.getCpf()));
        if (!MessageDigest.isEqual(userCpfHash.getBytes(StandardCharsets.US_ASCII),
                invitation.getCpfHash().getBytes(StandardCharsets.US_ASCII))) {
            throw invalidInvitation();
        }

        user.setEmpresa(invitation.getEmpresa());
        user.setCondominio(invitation.getCondominio());
        if (user.getCpfVerifiedAt() == null) user.setCpfVerifiedAt(now);
        users.saveAndFlush(user);

        invitation.setUsedBy(user);
        invitation.setUsedAt(now);
        invitations.save(invitation);

        boolean claimed = historicalPurchases.claimVerified(user);
        return response(user, claimed);
    }

    private ConsumeInvitationResponse response(User user, boolean claimed) {
        return new ConsumeInvitationResponse("LINKED", tokens.generateToken(user),
                user.getEmpresa().getId(), user.getCondominio().getIdCondominio(),
                "VERIFIED", claimed);
    }

    private static User authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new ApiBusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Autenticação obrigatória");
        }
        return user;
    }

    private static ApiBusinessException invalidInvitation() {
        return new ApiBusinessException(HttpStatus.NOT_FOUND, "TENANT_INVITATION_INVALID",
                "Convite de vínculo inválido ou expirado");
    }

    private static ApiBusinessException forbidden(String code, String message) {
        return new ApiBusinessException(HttpStatus.FORBIDDEN, code, message);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        if (value == null || value.isBlank()) throw invalidInvitation();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }
}
