package com.jefiro.app247.infra.repository;
import com.jefiro.app247.domain.model.appclient.LoyaltyMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface LoyaltyMovementRepository extends JpaRepository<LoyaltyMovement,String>{
    List<LoyaltyMovement> findTop50ByAccountIdOrderByCreatedAtDesc(String accountId);
    boolean existsByIdempotencyKey(String key);
    Optional<LoyaltyMovement> findByIdempotencyKey(String key);
}
