SET @now = UTC_TIMESTAMP(6);
SET @empresa = '10000000-0000-0000-0000-000000000001';
SET @condominio = '20000000-0000-0000-0000-000000000001';
SET @endereco = '21000000-0000-0000-0000-000000000001';
SET @terminal = '30000000-0000-0000-0000-000000000001';
SET @produto = '40000000-0000-0000-0000-000000000001';
SET @barcode = '41000000-0000-0000-0000-000000000001';
SET @estoque = '50000000-0000-0000-0000-000000000001';
SET @mp_conta = '60000000-0000-0000-0000-000000000001';

INSERT INTO empresa (
  id, tenant_id, razao_social, nome_fantasia, cnpj, email, ativo,
  created_at, updated_at, cor_principal, cor_secundaria, cor_destaque
) VALUES (
  @empresa, '11000000-0000-0000-0000-000000000001', 'Empresa E2E Ltda',
  'Empresa E2E', '00000000000191', 'e2e@example.invalid', TRUE,
  @now, @now, '#169DFF', '#62C8FF', '#00D084'
);

INSERT INTO endereco (
  id, empresa_id, rua, numero, bairro, cidade, estado, cep, created_at, updated_at
) VALUES (
  @endereco, @empresa, 'Rua E2E', '247', 'Centro', 'Salvador', 'BA', '40000-000', @now, @now
);

INSERT INTO condominio (
  id, empresa_id, endereco_id, nome, cnpj, ativo, created_at, updated_at
) VALUES (
  @condominio, @empresa, @endereco, 'Condominio E2E', '00000000000272', TRUE, @now, @now
);

INSERT INTO terminal (
  id, condominio_id, nome, codigo, serial_number, mac_address, ip_address,
  versao_software, mercado_pago_terminal_id, ativo, status, lifecycle_state,
  created_at, updated_at
) VALUES (
  @terminal, @condominio, 'Terminal E2E', 'TERM-E2E-001', 'SERIAL-E2E-001',
  '02:00:00:00:00:01', '127.0.0.1', 'e2e', 'POINT-E2E-001', TRUE,
  'OFFLINE', 'ACTIVE', @now, @now
);

INSERT INTO produto (
  id, empresa_id, codigo_interno, nome, descricao, preco_venda, categoria,
  unidade_medida, ativo, created_at, updated_at
) VALUES (
  @produto, @empresa, 'SKU-E2E-001', 'Produto E2E', 'Fixture E2E', 12.340000,
  'ALIMENTOS', 'UN', TRUE, @now, @now
);

INSERT INTO produto_codigo_barras (
  id, empresa_id, produto_id, codigo_barras, tipo, principal, ativo,
  created_at, updated_at
) VALUES (
  @barcode, @empresa, @produto, '7890000000001', 'EAN13', TRUE, TRUE, @now, @now
);

INSERT INTO estoque_condominio (
  id, empresa_id, condominio_id, produto_id, quantidade, ativo, lock_version,
  created_at, updated_at
) VALUES (
  @estoque, @empresa, @condominio, @produto, 100.000, TRUE, 0, @now, @now
);

INSERT INTO mercado_pago_conta (
  id, empresa_id, access_token, refresh_token, mp_user_id, status, linked_at,
  token_type, live_mode, token_created_at, token_expires_at, created_at, updated_at
) VALUES (
  @mp_conta, @empresa, @mp_access, @mp_refresh, 'e2e-mp-user',
  'ACTIVE', @now, 'Bearer', FALSE, @now, DATE_ADD(@now, INTERVAL 1 DAY), @now, @now
);

INSERT INTO mercado_pago_conta_ativa (
  mp_user_id, empresa_id, mercado_pago_conta_id, created_at
) VALUES ('e2e-mp-user', @empresa, @mp_conta, @now);

INSERT INTO terminal_point_binding (
  id, empresa_id, terminal_id, mercado_pago_conta_id, mercado_pago_terminal_id,
  status, linked_at, created_at, updated_at
) VALUES (
  '61000000-0000-0000-0000-000000000001', @empresa, @terminal, @mp_conta,
  'POINT-E2E-001', 'ACTIVE', @now, @now, @now
);
