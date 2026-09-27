package com.jefiro.app247.infra.repository;
import com.jefiro.app247.domain.model.appclient.AppClientePreference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AppClientePreferenceRepository extends JpaRepository<AppClientePreference,String>{
    Optional<AppClientePreference> findByUserIdAndEmpresaId(String userId,String empresaId);
}
