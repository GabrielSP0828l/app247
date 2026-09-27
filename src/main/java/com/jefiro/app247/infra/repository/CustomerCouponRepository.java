package com.jefiro.app247.infra.repository;
import com.jefiro.app247.domain.model.appclient.CustomerCoupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface CustomerCouponRepository extends JpaRepository<CustomerCoupon,String>{
    List<CustomerCoupon> findByUserIdUserAndEmpresaIdOrderByCreatedAtDesc(String userId,String empresaId);
    Optional<CustomerCoupon> findByIdAndUserIdUserAndEmpresaId(String id,String userId,String empresaId);
    List<CustomerCoupon> findByReservedOrderIdOrderAndStatus(String orderId, CustomerCoupon.CouponStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CustomerCoupon c where c.reservedOrder.idOrder=:orderId and c.status=:status")
    List<CustomerCoupon> findReservedForOrderUpdate(@Param("orderId") String orderId,
                                                     @Param("status") CustomerCoupon.CouponStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from CustomerCoupon c where c.id=:id and c.empresa.id=:empresaId")
    Optional<CustomerCoupon> findForUpdate(@Param("id") String id,@Param("empresaId") String empresaId);
}
