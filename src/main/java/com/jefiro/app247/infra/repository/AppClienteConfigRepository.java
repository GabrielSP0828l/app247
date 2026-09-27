package com.jefiro.app247.infra.repository;
import com.jefiro.app247.domain.model.appclient.AppClienteConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppClienteConfigRepository extends JpaRepository<AppClienteConfig, String> {
}
