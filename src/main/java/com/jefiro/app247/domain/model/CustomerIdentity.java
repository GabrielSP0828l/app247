package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customer_identity", uniqueConstraints =
        @UniqueConstraint(name = "uk_customer_identity_empresa_cpf", columnNames = {"empresa_id", "cpf_hash"}))
public class CustomerIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(name = "cpf_hash", nullable = false, length = 64)
    private String cpfHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimed_user_id")
    private User user;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
