package villanidev.ai.chatbot.rag.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import villanidev.ai.chatbot.rag.dto.DocumentDTO;
import villanidev.ai.chatbot.rag.dto.DocumentUploadResponse;
import villanidev.ai.chatbot.rag.dto.RagQueryRequest;
import villanidev.ai.chatbot.rag.dto.RagQueryResponse;
import villanidev.ai.chatbot.rag.service.EnhancedRagService;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final EnhancedRagService ragService;

    public RagController(EnhancedRagService ragService) {
        this.ragService = ragService;
    }

    /**
     * Upload de documento (PDF, TXT, etc)
     */
    @PostMapping("/documents/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
        @RequestParam("file") MultipartFile file
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(DocumentUploadResponse.builder()
                    .documentId(null)
                    .filename("")
                    .status("FAILED")
                    .chunkCount(0)
                    .message("Arquivo vazio")
                    .build());
        }

        DocumentUploadResponse response = ragService.uploadDocument(file);

        if ("FAILED".equals(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Faz uma query RAG com citações
     */
    @PostMapping("/query")
    public ResponseEntity<RagQueryResponse> query(@RequestBody RagQueryRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        RagQueryResponse response = ragService.query(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Lista todos os documentos processados
     */
    @GetMapping("/documents")
    public ResponseEntity<List<DocumentDTO>> listDocuments() {
        List<DocumentDTO> documents = ragService.listDocuments();
        return ResponseEntity.ok(documents);
    }

    /**
     * Deleta um documento específico
     */
    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long documentId) {
        ragService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Limpa todos os documentos
     */
    @DeleteMapping("/documents")
    public ResponseEntity<Void> clearAllDocuments() {
        ragService.clearAll();
        return ResponseEntity.noContent().build();
    }
}

