package villanidev.ai.chatbot.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RequestMapping("/api/chat")
@RestController
public class ChatController {
    @Autowired
    private ChatClient chatClient;

    @GetMapping
    public String chat(@RequestParam String question, @RequestParam String chatId) {
        return chatClient
                .prompt()
                .user(question)
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChunkResponseDTO> streamChat(@RequestParam String question, @RequestParam String chatId) {
        return chatClient
                .prompt()
                .user(question)
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content()
                .map(ChunkResponseDTO::new);
    }
}
