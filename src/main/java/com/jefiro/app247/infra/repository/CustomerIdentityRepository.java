package com.jefiro.app247.infra.repository;

import com.jefiro.app247.domain.model.CustomerIdentity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerIdentityRepository extends JpaRepository<CustomerIdentity, String> {
    Optional<CustomerIdentity> findByEmpresaIdAndCpfHash(String empresaId, String cpfHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from CustomerIdentity i where i.empresa.id=:empresaId and i.cpfHash=:cpfHash")
    Optional<CustomerIdentity> findForClaim(@Param("empresaId") String empresaId,
                                            @Param("cpfHash") String cpfHash);
}
