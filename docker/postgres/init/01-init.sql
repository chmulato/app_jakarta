-- Script de inicialização do banco de dados
-- Executado automaticamente na primeira inicialização do container

-- Criar tabela de usuários
CREATE TABLE IF NOT EXISTS usuarios (
    id SERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    senha VARCHAR(255) NOT NULL,
    ativo BOOLEAN DEFAULT true,
    perfil VARCHAR(20) DEFAULT 'USUARIO' CHECK (perfil IN ('ADMIN', 'USUARIO')),
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Criar tabela de produtos
CREATE TABLE IF NOT EXISTS produtos (
    id SERIAL PRIMARY KEY,
    nome VARCHAR(200) NOT NULL,
    descricao TEXT,
    preco DECIMAL(10,2) NOT NULL,
    categoria VARCHAR(50),
    estoque INTEGER DEFAULT 0,
    ativo BOOLEAN DEFAULT true,
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Inserir dados de exemplo
INSERT INTO usuarios (nome, email, senha, perfil) VALUES 
    ('Admin Sistema', 'admin@meuapp.com', '$2a$10$rF1YS1T.8QVXnpTlI.JY5u5Kz7x8TXDQ9Y2c3M4z6N8x.wJ4sA2G6', 'ADMIN'),
    ('Admin Exemplo', 'admin@exemplo.com', '$2a$10$rF1YS1T.8QVXnpTlI.JY5u5Kz7x8TXDQ9Y2c3M4z6N8x.wJ4sA2G6', 'ADMIN'),
    ('João Silva', 'joao@exemplo.com', '$2a$10$rF1YS1T.8QVXnpTlI.JY5u5Kz7x8TXDQ9Y2c3M4z6N8x.wJ4sA2G6', 'USUARIO'),
    ('Maria Santos', 'maria@exemplo.com', '$2a$10$rF1YS1T.8QVXnpTlI.JY5u5Kz7x8TXDQ9Y2c3M4z6N8x.wJ4sA2G6', 'USUARIO')
ON CONFLICT (email) DO NOTHING;

INSERT INTO produtos (nome, descricao, preco, categoria, estoque) VALUES 
    ('Notebook Dell', 'Notebook Dell Inspiron 15 Intel i5', 2500.00, 'Eletrônicos', 10),
    ('Mouse Logitech', 'Mouse óptico sem fio', 85.00, 'Acessórios', 50),
    ('Teclado Mecânico', 'Teclado mecânico RGB', 350.00, 'Acessórios', 25),
    ('Monitor LG 24"', 'Monitor Full HD 24 polegadas', 750.00, 'Eletrônicos', 15),
    ('Webcam HD', 'Webcam 1080p com microfone', 180.00, 'Acessórios', 30)
ON CONFLICT DO NOTHING;

-- Criar índices para performance
CREATE INDEX IF NOT EXISTS idx_usuarios_email ON usuarios(email);
CREATE INDEX IF NOT EXISTS idx_produtos_categoria ON produtos(categoria);
CREATE INDEX IF NOT EXISTS idx_produtos_ativo ON produtos(ativo);

-- Comentários nas tabelas
COMMENT ON TABLE usuarios IS 'Tabela de usuários do sistema';
COMMENT ON TABLE produtos IS 'Tabela de produtos do catálogo';

-- Usuário default para primeiro acesso
-- Credenciais iniciais (NÃO usar em produção):
--   email: admin@meuapp.com
--   senha (bcrypt hash abaixo corresponde por padrão a: Admin@123 )
-- Para gerar novo hash: usar uma ferramenta bcrypt (custo 10) e atualizar campo "senha".
-- O bloco abaixo é idempotente: só insere se não existir nenhum usuário ADMIN.
DO $$
DECLARE
    v_admin_count integer;
BEGIN
    SELECT COUNT(*) INTO v_admin_count FROM usuarios WHERE perfil = 'ADMIN';
    IF v_admin_count = 0 THEN
        INSERT INTO usuarios (nome, email, senha, perfil)
        VALUES ('Administrador', 'admin@meuapp.com', '$2a$10$rF1YS1T.8QVXnpTlI.JY5u5Kz7x8TXDQ9Y2c3M4z6N8x.wJ4sA2G6', 'ADMIN');
        RAISE NOTICE 'Usuário ADMIN default criado (admin@meuapp.com). Altere a senha após o primeiro login.';
    ELSE
        RAISE NOTICE 'Usuário ADMIN já existente: nenhum novo usuário default criado.';
    END IF;
END $$;

-- Mostrar estatísticas de criação
DO $$ 
BEGIN 
    RAISE NOTICE 'Base de dados inicializada com sucesso!';
    RAISE NOTICE 'Usuários criados: %', (SELECT COUNT(*) FROM usuarios);
    RAISE NOTICE 'Produtos criados: %', (SELECT COUNT(*) FROM produtos);
END $$;

-- Recomendações de segurança:
-- 1. Alterar a senha do usuário admin default imediatamente em ambientes de teste/produção.
-- 2. Definir política de expiração e complexidade de senha na aplicação.
-- 3. Remover este usuário de bootstrap ou desabilitar após criação de contas definitivas.
-- 4. Não versionar hashes reais de produção neste arquivo.