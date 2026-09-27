CREATE TABLE tenant_link_invitation (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    condominio_id CHAR(36) NOT NULL,
    cpf_hash CHAR(64) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    created_by_user_id CHAR(36) NOT NULL,
    used_by_user_id CHAR(36) NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tenant_link_invitation_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_tenant_link_invitation_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_tenant_link_invitation_condominio_tenant
        FOREIGN KEY (condominio_id, empresa_id) REFERENCES condominio (id, empresa_id),
    CONSTRAINT fk_tenant_link_invitation_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_tenant_link_invitation_used_by FOREIGN KEY (used_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_tenant_link_invitation_usage
        CHECK ((used_at IS NULL AND used_by_user_id IS NULL)
            OR (used_at IS NOT NULL AND used_by_user_id IS NOT NULL))
) ENGINE=InnoDB;

CREATE INDEX idx_tenant_link_invitation_cpf_tenant
    ON tenant_link_invitation (empresa_id, condominio_id, cpf_hash, expires_at);
CREATE INDEX idx_tenant_link_invitation_used_by
    ON tenant_link_invitation (used_by_user_id, used_at);
