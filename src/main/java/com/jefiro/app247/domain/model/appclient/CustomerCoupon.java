package com.jefiro.app247.domain.model.appclient;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="customer_coupon")
public class CustomerCoupon {
    @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="id",length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id") private Empresa empresa;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id") private User user;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reward_id") private Reward reward;
    @Column(nullable=false,length=150) private String nome;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private RewardDiscountType tipo;
    @Column(nullable=false,precision=15,scale=6) private BigDecimal valor;
    @Column(name="valor_minimo_compra",nullable=false,precision=15,scale=6) private BigDecimal valorMinimoCompra;
    @Column(name="desconto_maximo",precision=15,scale=6) private BigDecimal descontoMaximo;
    @Column(nullable=false) private Instant validade;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private CouponStatus status=CouponStatus.AVAILABLE;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reserved_order_id") private Order reservedOrder;
    @Version @Column(name="lock_version",nullable=false) private Long lockVersion;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @PrePersist void create(){Instant n=Instant.now();if(createdAt==null)createdAt=n;updatedAt=n;}
    @PreUpdate void update(){updatedAt=Instant.now();}
    public enum CouponStatus { AVAILABLE, RESERVED, USED, EXPIRED, CANCELLED }
}
