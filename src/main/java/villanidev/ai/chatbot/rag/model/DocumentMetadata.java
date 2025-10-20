package villanidev.ai.chatbot.rag.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "document_metadata")
public class DocumentMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 50)
    private String status; // PROCESSING, COMPLETED, ERROR

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Column
    private LocalDateTime processedAt;

    @Column(length = 500)
    private String errorMessage;

    @Column
    private Integer chunkCount;

    @Column(length = 1000)
    private String summary;

    @Column(length = 2000)
    private String metadata; // JSON string for additional metadata

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
        if (status == null) {
            status = "PROCESSING";
        }
    }

    public void markAsCompleted(int chunkCount, String summary) {
        this.status = "COMPLETED";
        this.processedAt = LocalDateTime.now();
        this.chunkCount = chunkCount;
        this.summary = truncateIfNeeded(summary, 1000);
        this.errorMessage = null;
    }

    public void markAsError(String errorMessage) {
        this.status = "ERROR";
        this.processedAt = LocalDateTime.now();
        this.errorMessage = truncateIfNeeded(errorMessage, 500);
    }
    
    /**
     * Utility method to safely truncate strings to fit database column limits
     */
    private static String truncateIfNeeded(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
