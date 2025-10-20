package villanidev.ai.chatbot.rag.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationDTO {
    private String source;
    private String content;
    private String page;
    private Double relevance;
}

