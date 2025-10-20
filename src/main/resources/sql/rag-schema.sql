-- Tabela para metadata dos documentos RAG
CREATE TABLE IF NOT EXISTS rag_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT,
    status VARCHAR(50) NOT NULL,
    total_chunks INTEGER,
    uploaded_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    metadata TEXT,
    error_message TEXT
);

CREATE INDEX idx_rag_documents_status ON rag_documents(status);
CREATE INDEX idx_rag_documents_uploaded_at ON rag_documents(uploaded_at DESC);

