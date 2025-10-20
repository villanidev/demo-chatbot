package villanidev.ai.chatbot.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.*;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class PgVectorStore implements VectorStoreService {

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;

    public void add(List<Document> documents) {
        try {
            String sql = """
                INSERT INTO document_vectors (document_id, chunk_index, content, embedding, metadata)
                VALUES (?, ?, ?, ?::vector, ?::jsonb)
                ON CONFLICT (document_id, chunk_index) 
                DO UPDATE SET 
                    content = EXCLUDED.content,
                    embedding = EXCLUDED.embedding,
                    metadata = EXCLUDED.metadata
                """;

            int chunkIndex = 0;
            for (Document doc : documents) {
                // Generate embedding for document
                EmbeddingResponse response = embeddingModel.embedForResponse(List.of(doc.getText()));
                if (response.getResults().isEmpty()) {
                    log.warn("No embedding generated for document chunk");
                    continue;
                }

                float[] embedding = response.getResults().get(0).getOutput();
                
                // Convert metadata to JSON
                String metadataJson = convertMetadataToJson(doc.getMetadata());
                
                // Get document ID from metadata
                Long documentId = getDocumentIdFromMetadata(doc.getMetadata());
                
                jdbcTemplate.update(sql, 
                    documentId,
                    chunkIndex++,
                    doc.getText(),
                    arrayToString(embedding),
                    metadataJson
                );
            }

            log.info("Added {} document chunks to PgVector store", documents.size());

        } catch (Exception e) {
            log.error("Error adding documents to PgVector store", e);
            throw new RuntimeException("Failed to store documents", e);
        }
    }

    public List<Document> similaritySearch(String query, int topK, double threshold) {
        try {
            // Generate embedding for query
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(query));
            if (response.getResults().isEmpty()) {
                return Collections.emptyList();
            }

            float[] queryEmbedding = response.getResults().get(0).getOutput();

            String sql = """
                SELECT dv.id, dv.document_id, dv.content, dv.metadata,
                       1 - (dv.embedding <=> ?::vector) as similarity
                FROM document_vectors dv
                WHERE 1 - (dv.embedding <=> ?::vector) >= ?
                ORDER BY dv.embedding <=> ?::vector
                LIMIT ?
                """;

            String embeddingStr = arrayToString(queryEmbedding);

            return jdbcTemplate.query(sql, 
                (rs, rowNum) -> {
                    Map<String, Object> metadata = parseJsonMetadata(rs.getString("metadata"));
                    metadata.put("similarity", rs.getDouble("similarity"));
                    metadata.put("vector_id", rs.getLong("id"));
                    
                    return new Document(rs.getString("content"), metadata);
                },
                embeddingStr, embeddingStr, threshold, embeddingStr, topK
            );

        } catch (Exception e) {
            log.error("Error performing similarity search", e);
            return Collections.emptyList();
        }
    }

    public void delete(Collection<String> docIds) {
        if (docIds.isEmpty()) {
            return;
        }

        try {
            String placeholders = String.join(",", Collections.nCopies(docIds.size(), "?"));
            String sql = "DELETE FROM document_vectors WHERE document_id IN (" + placeholders + ")";
            
            Object[] params = docIds.toArray();
            int deleted = jdbcTemplate.update(sql, params);
            
            log.info("Deleted {} vector entries for document IDs: {}", deleted, docIds);
            
        } catch (Exception e) {
            log.error("Error deleting document vectors", e);
            throw new RuntimeException("Failed to delete documents", e);
        }
    }

    public void clear() {
        try {
            int deleted = jdbcTemplate.update("DELETE FROM document_vectors");
            log.info("Cleared {} vectors from PgVector store", deleted);
            
        } catch (Exception e) {
            log.error("Error clearing PgVector store", e);
            throw new RuntimeException("Failed to clear vector store", e);
        }
    }

    public long count() {
        try {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_vectors", Long.class);
        } catch (Exception e) {
            log.error("Error counting vectors", e);
            return 0;
        }
    }

    // Utility methods
    private String arrayToString(float[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private String convertMetadataToJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(escape(entry.getKey())).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(escape(value.toString())).append("\"");
            } else if (value instanceof Number) {
                json.append(value.toString());
            } else {
                json.append("\"").append(escape(value.toString())).append("\"");
            }
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    private Map<String, Object> parseJsonMetadata(String json) {
        Map<String, Object> metadata = new HashMap<>();
        if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) {
            return metadata;
        }

        try {
            // Simple JSON parsing - in production, use Jackson or similar
            json = json.trim();
            if (json.startsWith("{") && json.endsWith("}")) {
                json = json.substring(1, json.length() - 1);
                String[] pairs = json.split(",");
                for (String pair : pairs) {
                    String[] keyValue = pair.split(":");
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim().replaceAll("\"", "");
                        String value = keyValue[1].trim().replaceAll("\"", "");
                        metadata.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse metadata JSON: {}", json, e);
        }

        return metadata;
    }

    private String escape(String str) {
        return str.replace("\"", "\\\"").replace("\\", "\\\\");
    }

    private Long getDocumentIdFromMetadata(Map<String, Object> metadata) {
        Object docId = metadata.get("document_id");
        if (docId instanceof String) {
            try {
                return Long.parseLong((String) docId);
            } catch (NumberFormatException e) {
                log.warn("Invalid document_id format: {}", docId);
                return 1L; // Default fallback
            }
        } else if (docId instanceof Long) {
            return (Long) docId;
        } else if (docId instanceof Integer) {
            return ((Integer) docId).longValue();
        }
        
        log.warn("No valid document_id found in metadata, using default");
        return 1L; // Default fallback
    }
}