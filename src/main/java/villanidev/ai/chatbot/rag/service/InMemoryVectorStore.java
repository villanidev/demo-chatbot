package villanidev.ai.chatbot.rag.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class InMemoryVectorStore implements VectorStoreService {

    private final EmbeddingModel embeddingModel;
    private final Map<String, Document> documents = new HashMap<>();
    private final Map<String, float[]> embeddings = new HashMap<>();

    public InMemoryVectorStore(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public void add(List<Document> documents) {
        for (Document doc : documents) {
            // Use document_id from metadata if available, otherwise generate UUID
            String id = (String) doc.getMetadata().get("document_id");
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            
            this.documents.put(id, doc);
            
            // Generate embedding for document content
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(doc.getText()));
            if (!response.getResults().isEmpty()) {
                Embedding embedding = response.getResults().get(0);
                this.embeddings.put(id, embedding.getOutput());
            }
        }
    }

    public List<Document> similaritySearch(String query, int topK, double threshold) {
        if (documents.isEmpty()) {
            return Collections.emptyList();
        }

        // Generate embedding for query
        EmbeddingResponse queryResponse = embeddingModel.embedForResponse(List.of(query));
        if (queryResponse.getResults().isEmpty()) {
            return Collections.emptyList();
        }

        float[] queryEmbedding = queryResponse.getResults().get(0).getOutput();

        // Calculate similarities
        List<SimilarityResult> similarities = new ArrayList<>();
        for (Map.Entry<String, float[]> entry : embeddings.entrySet()) {
            String docId = entry.getKey();
            float[] docEmbedding = entry.getValue();
            
            double similarity = cosineSimilarity(queryEmbedding, docEmbedding);
            
            if (similarity >= threshold) {
                similarities.add(new SimilarityResult(docId, similarity));
            }
        }

        // Sort by similarity and return top K
        return similarities.stream()
                .sorted(Comparator.comparingDouble(SimilarityResult::similarity).reversed())
                .limit(topK)
                .map(result -> documents.get(result.docId()))
                .collect(Collectors.toList());
    }

    public void delete(Collection<String> docIds) {
        for (String id : docIds) {
            documents.remove(id);
            embeddings.remove(id);
        }
    }

    public void clear() {
        documents.clear();
        embeddings.clear();
    }

    public long count() {
        return documents.size();
    }

    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record SimilarityResult(String docId, double similarity) {}
}