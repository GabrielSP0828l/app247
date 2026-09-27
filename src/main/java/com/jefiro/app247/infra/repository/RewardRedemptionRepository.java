package com.jefiro.app247.infra.repository;
import com.jefiro.app247.domain.model.appclient.RewardRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface RewardRedemptionRepository extends JpaRepository<RewardRedemption,String>{
    Optional<RewardRedemption> findByUserIdUserAndIdempotencyKey(String userId,String key);
    long countByUserIdUserAndRewardId(String userId,String rewardId);
}
