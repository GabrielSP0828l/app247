package com.jefiro.app247.domain.model.appclient;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="reward_redemption")
public class RewardRedemption {
    @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="id",length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id") private Empresa empresa;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id") private User user;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="reward_id") private Reward reward;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="coupon_id") private CustomerCoupon coupon;
    @Column(name="idempotency_key",nullable=false,length=180) private String idempotencyKey;
    @Column(name="points_spent",nullable=false) private long pointsSpent;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @PrePersist void create(){if(createdAt==null)createdAt=Instant.now();}
}
