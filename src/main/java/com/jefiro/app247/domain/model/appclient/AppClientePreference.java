package com.jefiro.app247.domain.model.appclient;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="app_cliente_preference")
public class AppClientePreference {
    @Id @Column(name="user_id",length=36) private String userId;
    @OneToOne(fetch=FetchType.LAZY) @MapsId @JoinColumn(name="user_id") private User user;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="empresa_id") private Empresa empresa;
    @Column(name="auto_email") private boolean autoEmail;
    @Column(name="auto_whatsapp") private boolean autoWhatsapp;
    @Column(name="updated_at") private Instant updatedAt;
    @PrePersist @PreUpdate void touch(){updatedAt=Instant.now();}
}
