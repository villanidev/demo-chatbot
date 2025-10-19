package villanidev.ai.chatbot.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RequestMapping("/api/chat")
@RestController
public class ChatController {

    private final ChatClient chatClient;
    private final ChatService chatService;

    public ChatController(ChatClient chatClient, ChatService chatService) {
        this.chatClient = chatClient;
        this.chatService = chatService;
    }

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

    @GetMapping("/generate-chat-id")
    public String generateChatId() {
        return chatService.generateChatId();
    }

    @GetMapping("/{chatId}/messages")
    public List<MessageResponseDTO> getConversationByChatId(@PathVariable String chatId) {
        return chatService.getConversationMessages(chatId);
    }

    @DeleteMapping("/{chatId}/messages")
    public void deleteConversationByChatId(@PathVariable String chatId) {
        chatService.deleteConversation(chatId);
    }

    @GetMapping("/conversations")
    public List<ConversationSummaryDTO> getAllConversations() {
        return chatService.getAllConversations();
    }
}
