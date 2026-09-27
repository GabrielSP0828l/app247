package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.appclient.TenantLinkInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TenantLinkInvitationRepository extends JpaRepository<TenantLinkInvitation, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from TenantLinkInvitation i
            join fetch i.empresa
            join fetch i.condominio
            left join fetch i.usedBy
            where i.tokenHash=:tokenHash
            """)
    Optional<TenantLinkInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
