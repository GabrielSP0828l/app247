package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolve o ator humano pelo Principal e aplica self/role/tenant sem confiar no path. */
@Service
public class HumanRequestAuthorizationService {
    @Autowired private UserRepository users;

    @Transactional(readOnly = true)
    public User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User principal)) {
            throw new ApiBusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED",
                    "Autenticação obrigatória");
        }
        return users.findByIdWithTenant(principal.getIdUser()).orElseThrow(() ->
                new ApiBusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED",
                        "Usuário autenticado não encontrado"));
    }

    @Transactional(readOnly = true)
    public User requireSelf(String requestedUserId) {
        User actor = currentUser();
        if (!actor.getIdUser().equals(requestedUserId)) throw hiddenUser();
        return actor;
    }

    @Transactional(readOnly = true)
    public User requireSelfOrTenantManager(String requestedUserId) {
        User actor = currentUser();
        if (actor.getIdUser().equals(requestedUserId)) return actor;
        if (actor.getRole() != RoleUser.ADMIN && actor.getRole() != RoleUser.GERENTE) throw hiddenUser();
        String tenant = EmpresaContext.require();
        return users.findByIdUserAndEmpresaId(requestedUserId, tenant).orElseThrow(this::hiddenUser);
    }

    private ApiBusinessException hiddenUser() {
        return new ApiBusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuário não encontrado");
    }
}
