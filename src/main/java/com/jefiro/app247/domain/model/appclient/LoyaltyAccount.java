package com.jefiro.app247.domain.model.appclient;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="loyalty_account")
public class LoyaltyAccount {
    @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="id",length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id") private Empresa empresa;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id") private User user;
    @Column(name="points_balance") private long pointsBalance;
    @Column(name="cashback_balance",precision=15,scale=6) private BigDecimal cashbackBalance=BigDecimal.ZERO;
    @Version @Column(name="lock_version") private Long lockVersion;
    @Column(name="created_at") private Instant createdAt;
    @Column(name="updated_at") private Instant updatedAt;
    @PrePersist void create(){Instant n=Instant.now();if(createdAt==null)createdAt=n;updatedAt=n;if(cashbackBalance==null)cashbackBalance=BigDecimal.ZERO;}
    @PreUpdate void update(){updatedAt=Instant.now();}
}
