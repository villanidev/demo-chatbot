package villanidev.ai.chatbot.rag.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryRequest {
    private String question;
    private Integer topK;
    private String conversationId;
    
    public Integer getTopK() {
        return topK != null && topK > 0 ? topK : 5;
    }
}
