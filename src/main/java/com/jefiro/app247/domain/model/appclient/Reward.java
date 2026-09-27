package com.jefiro.app247.domain.model.appclient;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="reward")
public class Reward {
    @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="id",length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id") private Empresa empresa;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="condominio_id") private Condominio condominio;
    @Column(nullable=false,length=150) private String nome;
    @Column(length=1000) private String descricao;
    @Enumerated(EnumType.STRING) @Column(name="tipo_desconto",nullable=false,length=30) private RewardDiscountType tipoDesconto;
    @Column(name="valor_desconto",nullable=false,precision=15,scale=6) private BigDecimal valorDesconto;
    @Column(name="custo_pontos",nullable=false) private long custoPontos;
    @Column(name="valor_minimo_compra",nullable=false,precision=15,scale=6) private BigDecimal valorMinimoCompra;
    @Column(name="desconto_maximo",precision=15,scale=6) private BigDecimal descontoMaximo;
    @Column(nullable=false) private Instant validade;
    @Column(name="validade_dias",nullable=false) private int validadeDias;
    @Column(name="limite_uso_por_usuario",nullable=false) private int limiteUsoPorUsuario=1;
    @Column(nullable=false) private boolean ativo=true;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @PrePersist void create(){Instant now=Instant.now();if(createdAt==null)createdAt=now;if(validade==null&&validadeDias>0)validade=now.plusSeconds(validadeDias*86400L);updatedAt=now;}
    @PreUpdate void update(){updatedAt=Instant.now();}
}
