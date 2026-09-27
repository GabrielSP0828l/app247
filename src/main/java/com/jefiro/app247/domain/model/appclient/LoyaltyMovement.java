package com.jefiro.app247.domain.model.appclient;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Order;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="loyalty_movement")
public class LoyaltyMovement {
    @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="id",length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id") private Empresa empresa;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="account_id") private LoyaltyAccount account;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id") private Order order;
    @Column(nullable=false,length=30) private String type;
    @Column(name="points_delta") private Long pointsDelta;
    @Column(name="cashback_delta",precision=15,scale=6) private BigDecimal cashbackDelta;
    @Column(nullable=false,length=240) private String description;
    @Column(name="idempotency_key",nullable=false,length=180) private String idempotencyKey;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @PrePersist void create(){if(createdAt==null)createdAt=Instant.now();}
}
