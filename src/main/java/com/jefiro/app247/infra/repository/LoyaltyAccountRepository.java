package com.jefiro.app247.infra.repository;
import com.jefiro.app247.domain.model.appclient.LoyaltyAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount,String>{
    Optional<LoyaltyAccount> findByUserIdUserAndEmpresaId(String userId,String empresaId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from LoyaltyAccount a where a.user.idUser=:userId and a.empresa.id=:empresaId")
    Optional<LoyaltyAccount> findForUpdate(@Param("userId") String userId,@Param("empresaId") String empresaId);
}
