package villanidev.ai.chatbot.rag.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDTO {
    private Long id;
    private String filename;
    private String contentType;
    private Long fileSize;
    private String status;
    private LocalDateTime uploadedAt;
    private LocalDateTime processedAt;
    private Integer chunkCount;
    private String summary;
    private String errorMessage;
}
