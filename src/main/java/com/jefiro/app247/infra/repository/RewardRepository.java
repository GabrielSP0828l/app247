package com.jefiro.app247.infra.repository;
import com.jefiro.app247.domain.model.appclient.Reward;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RewardRepository extends JpaRepository<Reward, String> {
    @Query("""
            select r from Reward r
             where r.empresa.id = :empresaId
               and r.ativo = true
               and r.validade > :now
               and (r.condominio is null or r.condominio.idCondominio = :condominioId)
             order by r.createdAt desc
            """)
    List<Reward> findAvailableForClient(@Param("empresaId") String empresaId,
                                        @Param("condominioId") String condominioId,
                                        @Param("now") Instant now);

    Optional<Reward> findByIdAndEmpresaId(String id, String empresaId);

    Page<Reward> findByEmpresaId(String empresaId, Pageable pageable);

    Page<Reward> findByEmpresaIdAndAtivo(String empresaId, boolean ativo, Pageable pageable);
}
