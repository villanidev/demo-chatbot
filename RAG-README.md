# 📚 RAG (Retrieval-Augmented Generation) - Módulo de Documentação

## 🎯 Visão Geral

Sistema completo de RAG implementado com **Spring AI + Ollama + Vector Store** para análise inteligente de documentos.

## 🏗️ Arquitetura

```
┌─────────────────────────────────────────────────────────┐
│  Frontend (rag.html)                                     │
│  - Upload drag & drop                                    │
│  - Chat interface                                        │
│  - Citações de fontes                                    │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│  RagController (REST API)                                │
│  - POST /api/rag/documents/upload                        │
│  - POST /api/rag/query                                   │
│  - GET  /api/rag/documents                               │
│  - DELETE /api/rag/documents/{id}                        │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│  RagService (Business Logic)                             │
│  - Orquestra upload e query                              │
│  - Integra VectorStore + ChatClient                      │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────┬──────────────────────────────────┐
│ DocumentProcessor    │  VectorStore                      │
│ - Extrai texto PDF   │  - SimpleVectorStore (dev)        │
│ - Divide em chunks   │  - PgVector (prod - futuro)       │
│ - Adiciona metadata  │  - Embeddings (nomic-embed-text)  │
└──────────────────────┴──────────────────────────────────┘
```

## 🚀 Como Usar

### 1️⃣ Preparar Ollama

```bash
# Baixar modelo de chat
docker exec ollama ollama pull llama3.2:1b

# Baixar modelo de embeddings (IMPORTANTE!)
docker exec ollama ollama pull nomic-embed-text
```

### 2️⃣ Iniciar Aplicação

```bash
mvnw.cmd clean install
mvnw.cmd spring-boot:run
```

### 3️⃣ Acessar Interface

Abra no navegador: **https://localhost:8080/rag.html**

### 4️⃣ Upload de Documentos

1. Arraste um arquivo PDF ou TXT para a área de upload
2. Aguarde o processamento (status aparecerá no painel esquerdo)
3. Documento será dividido em chunks e vetorizado

### 5️⃣ Fazer Perguntas

Digite sua pergunta no chat. Exemplos:

- "Qual o principal tema abordado neste documento?"
- "Resuma os pontos principais"
- "Explique o conceito X mencionado no texto"

### 6️⃣ Visualizar Citações

Cada resposta virá com **citações** mostrando:
- Nome do arquivo fonte
- Trecho relevante do documento
- Número da página (quando disponível)

## 📋 Endpoints API

### Upload de Documento
```http
POST /api/rag/documents/upload
Content-Type: multipart/form-data

file: [arquivo.pdf]
```

**Response:**
```json
{
  "documentId": 1,
  "filename": "documento.pdf",
  "status": "COMPLETED",
  "chunksProcessed": 15,
  "message": "Documento processado com sucesso"
}
```

### Query RAG
```http
POST /api/rag/query
Content-Type: application/json

{
  "question": "O que é Spring AI?",
  "topK": 5,
  "conversationId": "uuid-optional"
}
```

**Response:**
```json
{
  "answer": "Spring AI é uma biblioteca...",
  "citations": [
    {
      "source": "spring-docs.pdf",
      "content": "Spring AI provides integration...",
      "pageNumber": 3,
      "relevanceScore": 0.89
    }
  ],
  "conversationId": "uuid"
}
```

### Listar Documentos
```http
GET /api/rag/documents
```

**Response:**
```json
[
  {
    "id": 1,
    "filename": "documento.pdf",
    "contentType": "application/pdf",
    "size": 2048576,
    "status": "COMPLETED",
    "totalChunks": 15,
    "uploadedAt": "2025-01-19T18:30:00"
  }
]
```

## 🔧 Configuração

### application.yaml

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      embedding:
        model: nomic-embed-text  # Modelo de embeddings
  servlet:
    multipart:
      max-file-size: 50MB      # Tamanho máximo de arquivo
      max-request-size: 50MB
```

### Chunk Strategy

Por padrão, documentos são divididos em chunks de:
- **500 tokens** por chunk
- **100 tokens** de overlap

Para alterar, edite `DocumentProcessorService.java`:
```java
this.textSplitter = new TokenTextSplitter(
    500,  // chunk size
    100   // overlap
);
```

## 📊 Tipos de Documentos Suportados

### ✅ Implementado (Nível 1)
- ✅ **PDF com texto extraível**
- ✅ **Arquivos TXT**

### 🚧 Roadmap (Nível 2)
- ⏳ **PDF scaneado (OCR com Tesseract)**
- ⏳ **Tabelas em PDFs (Tabula)**
- ⏳ **Documentos Word (.docx)**
- ⏳ **Markdown (.md)**

### 🔮 Futuro (Nível 3)
- 🔮 **Imagens com OCR**
- 🔮 **Planilhas Excel**
- 🔮 **Apresentações PowerPoint**

## 🧪 Testes Manuais

### Teste 1: Upload de PDF
```bash
curl -X POST https://localhost:8080/api/rag/documents/upload \
  -F "file=@documento.pdf" \
  -k
```

### Teste 2: Query
```bash
curl -X POST https://localhost:8080/api/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Qual o tema principal?",
    "topK": 3
  }' \
  -k
```

## 🎯 QuestionAnswerAdvisor

O sistema usa o **QuestionAnswerAdvisor** do Spring AI que:

1. Recebe a pergunta do usuário
2. Gera embedding da pergunta
3. Busca os top-K documentos mais similares no VectorStore
4. Injeta os documentos no contexto do prompt
5. Envia para o LLM gerar a resposta

Exemplo de prompt gerado automaticamente:
```
Use o contexto abaixo para responder a pergunta.
Se a informação não estiver no contexto, diga que não sabe.

CONTEXTO:
[Chunks relevantes dos documentos]

PERGUNTA: [Pergunta do usuário]

RESPOSTA:
```

## 🗄️ Banco de Dados

### Tabela: rag_documents
```sql
CREATE TABLE rag_documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT,
    status VARCHAR(50),
    total_chunks INTEGER,
    uploaded_at TIMESTAMP,
    processed_at TIMESTAMP,
    metadata TEXT,
    error_message TEXT
);
```

### VectorStore
- **Dev:** SimpleVectorStore (em memória + arquivo JSON)
- **Prod:** PgVector (PostgreSQL com extensão pgvector)

## 🔐 Segurança

- Upload limitado a 50MB por arquivo
- Validação de tipo de arquivo no backend
- Sanitização de nomes de arquivos

## 📈 Performance

### Benchmarks Esperados
- **Upload de PDF (10 páginas):** ~2-5 segundos
- **Query RAG:** ~1-3 segundos
- **Geração de embedding:** ~100ms por chunk

### Otimizações Futuras
- ✅ Cache de embeddings
- ✅ Processamento assíncrono de uploads
- ✅ Batch processing para múltiplos documentos
- ✅ Compressão de chunks

## 🐛 Troubleshooting

### Erro: "Failed to generate embeddings"
**Solução:** Certifique-se que o modelo está instalado:
```bash
docker exec ollama ollama pull nomic-embed-text
```

### Erro: "File size exceeds maximum"
**Solução:** Aumente o limite no `application.yaml`:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
```

### Erro: "No documents found"
**Solução:** Faça upload de pelo menos 1 documento antes de fazer queries.

## 📚 Referências

- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Ollama Embeddings](https://ollama.ai/library/nomic-embed-text)
- [RAG Best Practices](https://www.pinecone.io/learn/retrieval-augmented-generation/)

---

**Desenvolvido com:** Spring Boot 3.4.5 + Spring AI 1.0.0 + Ollama

