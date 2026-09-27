package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.CustomerIdentity;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.CustomerIdentityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class HistoricalPurchaseOwnershipService {
    private final CustomerIdentityRepository identities;
    private final CustomerIdentityHashService hashes;

    public HistoricalPurchaseOwnershipService(CustomerIdentityRepository identities,
                                              CustomerIdentityHashService hashes) {
        this.identities = identities;
        this.hashes = hashes;
    }

    @Transactional
    public boolean claimVerified(User user) {
        if (user.getCpfVerifiedAt() == null) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "CPF_NOT_VERIFIED",
                    "O CPF precisa estar verificado para reivindicar compras anteriores.");
        }
        if (user.getEmpresa() == null) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "TENANT_LINK_INVALID",
                    "A conta ainda não possui vínculo autorizado com uma empresa.");
        }
        String empresaId = user.getEmpresa().getId();
        String cpfHash = hashes.hashCpf(user.getCpf());
        CustomerIdentity identity = identities.findForClaim(empresaId, cpfHash).orElse(null);
        if (identity == null) return false;
        if (identity.getUser() != null && !identity.getUser().getIdUser().equals(user.getIdUser())) {
            throw new ApiBusinessException(HttpStatus.CONFLICT, "IDENTITY_ALREADY_CLAIMED",
                    "Esta identidade já foi reivindicada.");
        }
        if (identity.getUser() == null) {
            identity.setUser(user);
            identity.setClaimedAt(Instant.now());
        }
        return true;
    }
}
