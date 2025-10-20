package villanidev.ai.chatbot.rag.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryResponse {
    private String answer;
    private String question;
    private List<CitationDTO> citations;
    private String conversationId;
}

