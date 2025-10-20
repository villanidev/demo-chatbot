package villanidev.ai.chatbot.rag.service;

import org.springframework.ai.document.Document;
import java.util.Collection;
import java.util.List;

/**
 * Interface comum para armazenamento de vetores
 * Permite alternar entre InMemoryVectorStore (dev) e PgVectorStore (prod)
 */
public interface VectorStoreService {
    
    /**
     * Adiciona documentos ao vector store
     */
    void add(List<Document> documents);
    
    /**
     * Busca por similaridade
     */
    List<Document> similaritySearch(String query, int topK, double threshold);
    
    /**
     * Remove documentos por ID
     */
    void delete(Collection<String> docIds);
    
    /**
     * Limpa todos os vetores
     */
    void clear();
    
    /**
     * Conta total de vetores
     */
    long count();
}