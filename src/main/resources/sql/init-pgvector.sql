-- Inicialização do PostgreSQL com pgvector para RAG
-- Este script é executado quando o container PostgreSQL é criado

-- Ativar extensão pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- Tabela para metadados de documentos (já existe no código Java)
-- Esta será criada automaticamente pelo Spring JPA

-- Tabela para armazenar os vetores dos documentos
CREATE TABLE IF NOT EXISTS document_vectors (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding vector(768), -- Dimensão típica do nomic-embed-text
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(document_id, chunk_index)
);

-- Índices para otimizar buscas vetoriais
CREATE INDEX IF NOT EXISTS idx_document_vectors_embedding 
    ON document_vectors USING ivfflat (embedding vector_cosine_ops) 
    WITH (lists = 100);

CREATE INDEX IF NOT EXISTS idx_document_vectors_document_id 
    ON document_vectors (document_id);

CREATE INDEX IF NOT EXISTS idx_document_vectors_metadata 
    ON document_vectors USING gin (metadata);

-- Função para busca de similaridade
CREATE OR REPLACE FUNCTION similarity_search(
    query_embedding vector(768),
    similarity_threshold float DEFAULT 0.7,
    max_results integer DEFAULT 10
)
RETURNS TABLE (
    id BIGINT,
    document_id BIGINT,
    content TEXT,
    similarity FLOAT,
    metadata JSONB
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        dv.id,
        dv.document_id,
        dv.content,
        1 - (dv.embedding <=> query_embedding) AS similarity,
        dv.metadata
    FROM document_vectors dv
    WHERE 1 - (dv.embedding <=> query_embedding) >= similarity_threshold
    ORDER BY dv.embedding <=> query_embedding
    LIMIT max_results;
END;
$$ LANGUAGE plpgsql;

-- Configurações de performance para pgvector
ALTER DATABASE ragdb SET maintenance_work_mem = '512MB';

-- Usuário para a aplicação (já criado no environment)
-- Garantir que o usuário tenha as permissões necessárias
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO raguser;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO raguser;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO raguser;

-- Configurar permissões para futuras tabelas
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO raguser;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO raguser;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO raguser;

-- Log de inicialização
DO $$
BEGIN
    RAISE NOTICE 'RAG Database initialized successfully with pgvector extension';
    RAISE NOTICE 'Vector dimension: 768 (nomic-embed-text compatible)';
    RAISE NOTICE 'Similarity threshold: 0.7 (default)';
END $$;