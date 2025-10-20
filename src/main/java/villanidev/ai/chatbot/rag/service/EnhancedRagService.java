package villanidev.ai.chatbot.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import villanidev.ai.chatbot.rag.dto.*;
import villanidev.ai.chatbot.rag.model.DocumentMetadata;
import villanidev.ai.chatbot.rag.repository.DocumentMetadataRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedRagService {

    private final AdvancedDocumentProcessorService documentProcessorService;
    private final VectorStoreService vectorStore;
    private final ChatClient chatClient;
    private final DocumentMetadataRepository documentMetadataRepository;

    // Configuration
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.3;

    public DocumentUploadResponse uploadDocument(MultipartFile file) {
        try {
            log.info("Starting document upload: {}", file.getOriginalFilename());
            
            validateFile(file);
            
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .filename(file.getOriginalFilename())
                    .originalFilename(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .status("PROCESSING")
                    .build();

            metadata = documentMetadataRepository.save(metadata);
            
            List<Document> documents = documentProcessorService.processDocument(file, metadata);
            documents = enhanceDocumentsMetadata(documents, metadata);
            vectorStore.add(documents);
            
            String summary = generateIntelligentSummary(documents);
            metadata.markAsCompleted(documents.size(), summary);
            documentMetadataRepository.save(metadata);

            log.info("Document processed successfully: {} chunks", documents.size());

            return DocumentUploadResponse.builder()
                    .documentId(metadata.getId())
                    .filename(metadata.getFilename())
                    .status("COMPLETED")
                    .chunkCount(documents.size())
                    .message("Document processed successfully")
                    .build();

        } catch (Exception e) {
            log.error("Error processing document", e);
            throw new RuntimeException("Document processing failed: " + e.getMessage());
        }
    }

    public RagQueryResponse query(RagQueryRequest request) {
        try {
            log.info("Processing RAG query: {}", request.getQuestion());
            long startTime = System.currentTimeMillis();

            validateQuery(request);

            int topK = request.getTopK() != null ? request.getTopK() : DEFAULT_TOP_K;
            
            List<Document> relevantDocs = vectorStore.similaritySearch(
                request.getQuestion(), 
                topK, 
                DEFAULT_SIMILARITY_THRESHOLD
            );

            if (relevantDocs.isEmpty()) {
                return createEmptyResponse(request.getQuestion());
            }

            String context = buildSimpleContext(relevantDocs);
            String answer = generateResponseWithAdvisor(request.getQuestion(), context);
            List<CitationDTO> citations = createDetailedCitations(relevantDocs);

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Query processed in {}ms with {} documents", processingTime, relevantDocs.size());

            return RagQueryResponse.builder()
                    .answer(answer)
                    .question(request.getQuestion())
                    .citations(citations)
                    .build();

        } catch (Exception e) {
            log.error("Error processing RAG query", e);
            return RagQueryResponse.builder()
                    .answer("Desculpe, ocorreu um erro ao processar sua pergunta. Tente novamente.")
                    .question(request.getQuestion())
                    .citations(Collections.emptyList())
                    .build();
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > 100 * 1024 * 1024) {
            throw new IllegalArgumentException("File too large (max 100MB)");
        }
        String contentType = file.getContentType();
        if (!documentProcessorService.isFileTypeSupported(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }
    }

    private void validateQuery(RagQueryRequest request) {
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }
        if (request.getQuestion().length() > 1000) {
            throw new IllegalArgumentException("Query too long (max 1000 characters)");
        }
    }

    private List<Document> enhanceDocumentsMetadata(List<Document> documents, DocumentMetadata metadata) {
        return documents.stream().map(doc -> {
            Map<String, Object> enhancedMetadata = new HashMap<>(doc.getMetadata());
            
            // CRITICAL: Add document_id for proper deletion tracking
            enhancedMetadata.put("document_id", metadata.getId().toString());
            
            enhancedMetadata.put("processed_at", LocalDateTime.now().toString());
            enhancedMetadata.put("file_size", metadata.getFileSize());
            enhancedMetadata.put("upload_time", metadata.getUploadedAt().toString());
            enhancedMetadata.put("chunk_length", doc.getText().length());
            enhancedMetadata.put("word_count", doc.getText().split("\\s+").length);
            return new Document(doc.getText(), enhancedMetadata);
        }).collect(Collectors.toList());
    }

    private String buildSimpleContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            context.append("Document ").append(i + 1).append(":\n");
            context.append(doc.getText()).append("\n\n");
        }
        return context.toString();
    }

    private String generateResponseWithAdvisor(String question, String context) {
        String systemPrompt = """
            You are a helpful AI assistant. Use the provided context to answer the user's question.
            If the context doesn't contain relevant information, say so clearly.
            Provide accurate and concise responses based on the context.
            """;

        String userPrompt = String.format("""
            Question: %s
            
            Context:
            %s
            """, question, context);

        try {
            return chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .advisors(new SimpleLoggerAdvisor())
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("Error generating response", e);
            return "Desculpe, ocorreu um erro ao gerar a resposta.";
        }
    }

    private List<CitationDTO> createDetailedCitations(List<Document> documents) {
        return documents.stream().map(doc -> {
            String source = (String) doc.getMetadata().getOrDefault("source", "Unknown Document");
            String page = (String) doc.getMetadata().get("page");
            String contentType = (String) doc.getMetadata().getOrDefault("content_type", "unknown");
            
            StringBuilder sourceBuilder = new StringBuilder(source);
            if (page != null) {
                sourceBuilder.append(" (Page ").append(page).append(")");
            }
            if (!"unknown".equals(contentType)) {
                sourceBuilder.append(" [").append(contentType.toUpperCase()).append("]");
            }
            
            String content = doc.getText();
            if (content.length() > 300) {
                content = content.substring(0, 297) + "...";
            }
            
            return CitationDTO.builder()
                    .source(sourceBuilder.toString())
                    .content(content)
                    .page(page)
                    .relevance(0.8)
                    .build();
        }).collect(Collectors.toList());
    }

    private String generateIntelligentSummary(List<Document> documents) {
        if (documents.isEmpty()) {
            return "Empty document";
        }

        StringBuilder sampleContent = new StringBuilder();
        int contentLength = 0;
        
        for (Document doc : documents.stream().limit(5).collect(Collectors.toList())) {
            String text = doc.getText();
            if (contentLength + text.length() > 1000) {
                int remaining = 1000 - contentLength;
                if (remaining > 50) {
                    sampleContent.append(text, 0, remaining).append("...");
                }
                break;
            }
            sampleContent.append(text).append(" ");
            contentLength += text.length();
        }

        try {
            String prompt = String.format(
                "Analyze the following document content and provide a concise summary in 2-3 sentences. Keep it under 800 characters and focus on the main topics and key information:\n\n%s",
                sampleContent.toString()
            );

            String summary = chatClient.prompt(prompt).call().content();
            
            if (summary != null && summary.length() > 950) {
                summary = summary.substring(0, 947) + "...";
            }
            
            return summary;
        } catch (Exception e) {
            log.warn("Failed to generate AI summary, using fallback", e);
            return "Document containing " + documents.size() + " sections with various content types";
        }
    }

    private RagQueryResponse createEmptyResponse(String question) {
        return RagQueryResponse.builder()
                .answer("I couldn't find relevant information in the uploaded documents to answer your question. Please make sure you have uploaded relevant documents or try rephrasing your question.")
                .question(question)
                .citations(Collections.emptyList())
                .build();
    }

    // Delegate methods
    public List<DocumentDTO> listDocuments() {
        return documentMetadataRepository.findByOrderByUploadedAtDesc()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public void deleteDocument(Long documentId) {
        DocumentMetadata metadata = documentMetadataRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        vectorStore.delete(Collections.singletonList(documentId.toString()));
        documentMetadataRepository.delete(metadata);
        log.info("Document deleted: {}", metadata.getFilename());
    }

    public void clearAll() {
        documentMetadataRepository.deleteAll();
        vectorStore.clear();
        log.info("All documents cleared from system");
    }

    private DocumentDTO convertToDTO(DocumentMetadata metadata) {
        return DocumentDTO.builder()
                .id(metadata.getId())
                .filename(metadata.getFilename())
                .contentType(metadata.getContentType())
                .fileSize(metadata.getFileSize())
                .status(metadata.getStatus())
                .uploadedAt(metadata.getUploadedAt())
                .processedAt(metadata.getProcessedAt())
                .chunkCount(metadata.getChunkCount())
                .summary(metadata.getSummary())
                .errorMessage(metadata.getErrorMessage())
                .build();
    }
}