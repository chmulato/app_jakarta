-- Seed data for Phase 1 local operation

INSERT INTO usuarios (nome, email, senha, perfil)
VALUES
    ('Admin Sistema', 'admin@meuapp.com', '$2a$10$rF1YS1T.8QVXnpTlI.JY5u5Kz7x8TXDQ9Y2c3M4z6N8x.wJ4sA2G6', 'ADMIN'),
    ('Supervisor Balcao', 'supervisor@meuapp.com', '$2a$10$rF1YS1T.8QVXnpTlI.JY5u5Kz7x8TXDQ9Y2c3M4z6N8x.wJ4sA2G6', 'USUARIO'),
    ('Operador 01', 'operador01@meuapp.com', '$2a$10$rF1YS1T.8QVXnpTlI.JY5u5Kz7x8TXDQ9Y2c3M4z6N8x.wJ4sA2G6', 'USUARIO')
ON CONFLICT (email) DO NOTHING;

INSERT INTO posicao (rua, modulo, nivel, caixa, ocupada)
VALUES
    ('A', '01', '01', '01', false),
    ('A', '01', '01', '02', false),
    ('A', '01', '02', '01', false),
    ('A', '02', '01', '01', false),
    ('B', '01', '01', '01', false),
    ('B', '01', '02', '01', false)
ON CONFLICT DO NOTHING;

INSERT INTO pedido (codigo, canal, destinatario_nome, destinatario_documento, destinatario_telefone, status)
VALUES ('PED-LOCAL-001', 'MANUAL', 'Cliente Exemplo', '11122233344', '11988887777', 'RECEBIDO')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO volume (pedido_id, etiqueta, peso, dimensoes, status)
SELECT p.id, 'PED-LOCAL-001-VOL-01', 2.50, '30x20x15', 'RECEBIDO'
FROM pedido p
WHERE p.codigo = 'PED-LOCAL-001'
ON CONFLICT (etiqueta) DO NOTHING;

INSERT INTO evento (pedido_id, tipo, payload, actor)
SELECT p.id, 'CRIACAO', 'Pedido criado via seed', 'bootstrap'
FROM pedido p
WHERE p.codigo = 'PED-LOCAL-001'
  AND NOT EXISTS (
    SELECT 1 FROM evento e
    WHERE e.pedido_id = p.id AND e.tipo = 'CRIACAO'
  );
