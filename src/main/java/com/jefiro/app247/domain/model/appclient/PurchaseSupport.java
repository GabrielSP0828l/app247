package com.jefiro.app247.domain.model.appclient;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="purchase_support")
public class PurchaseSupport {
    @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="id",length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id") private Empresa empresa;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id") private User user;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id") private Order order;
    @Column(nullable=false,length=40) private String protocolo;
    @Column(nullable=false,length=50) private String categoria;
    @Column(nullable=false,length=2000) private String mensagem;
    @Column(nullable=false,length=20) private String status="OPEN";
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @PrePersist void create(){if(createdAt==null)createdAt=Instant.now();}
}
