package com.jefiro.app247.domain.model.appclient;

import com.jefiro.app247.domain.model.Empresa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_cliente_config")
public class AppClienteConfig {
    @Id
    @Column(name = "empresa_id", length = 36)
    private String empresaId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    private boolean catalogo;
    private boolean promocoes;
    private boolean fidelidade;
    private boolean pontos;
    private boolean cashback;
    private boolean recompensas;
    private boolean cupons;
    @Column(name = "qr_identificacao") private boolean qrIdentificacao;
    private boolean preferencias;
    private boolean suporte;

    @Enumerated(EnumType.STRING)
    @Column(name = "loyalty_mode", nullable = false)
    private LoyaltyMode loyaltyMode = LoyaltyMode.NONE;

    @Column(name = "points_per_currency", precision = 15, scale = 6)
    private BigDecimal pointsPerCurrency;
    @Column(name = "cashback_percent", precision = 9, scale = 6)
    private BigDecimal cashbackPercent;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void create() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        synchronizeDerivedFlags();
    }

    @PreUpdate
    void update() {
        updatedAt = Instant.now();
        synchronizeDerivedFlags();
    }

    public void synchronizeDerivedFlags() {
        boolean pointsEnabled = fidelidade && loyaltyMode == LoyaltyMode.POINTS;
        boolean cashbackEnabled = fidelidade && loyaltyMode == LoyaltyMode.CASHBACK;
        pontos = pointsEnabled;
        cashback = cashbackEnabled;
        recompensas = pointsEnabled;
        cupons = pointsEnabled;
    }

    public static AppClienteConfig defaults(Empresa empresa) {
        AppClienteConfig config = new AppClienteConfig();
        config.setEmpresa(empresa);
        config.setLoyaltyMode(LoyaltyMode.NONE);
        return config;
    }

    public enum LoyaltyMode { NONE, POINTS, CASHBACK }
}
