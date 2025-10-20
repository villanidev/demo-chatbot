package villanidev.ai.chatbot.rag.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResponse {
    private Long documentId;
    private String filename;
    private String status;
    private Integer chunkCount;
    private String message;
}

