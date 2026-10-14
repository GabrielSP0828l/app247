package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.appclient.CustomerIdentificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface CustomerIdentificationTokenRepository
        extends JpaRepository<CustomerIdentificationToken, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from CustomerIdentificationToken t
            join fetch t.user u
            join fetch t.condominio c
            join fetch t.empresa e
            where t.tokenHash = :hash
            """)
    Optional<CustomerIdentificationToken> findByHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("""
            update CustomerIdentificationToken t set t.invalidatedAt=:now
            where t.user.idUser=:userId and t.consumedAt is null
              and t.invalidatedAt is null and t.expiresAt>:now
            """)
    int invalidateActiveForUser(@Param("userId") String userId, @Param("now") Instant now);
}
