package villanidev.ai.chatbot.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/chat")
@RestController
public class ChatController {
    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ChatMemory chatMemory;

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
        return UUID.randomUUID().toString();
    }

    @GetMapping("/{chatId}/messages")
    public List<MessageResponseDTO> getConversationByChatId(@PathVariable String chatId) {
        return chatMemory.get(chatId)
                .stream()
                .filter(this::onlyUserOrAssistant)
                .map(this::buildResponseDTO)
                .toList();
    }

    @DeleteMapping("/{chatId}/messages")
    public void deleteConversationByChatId(@PathVariable String chatId) {
        chatMemory.clear(chatId);
    }

    private MessageResponseDTO buildResponseDTO(final Message msg) {
        return msg.getMessageType().equals(MessageType.ASSISTANT) ?
                new MessageResponseDTO("ai", msg.getText()) :
                new MessageResponseDTO(msg.getMessageType().getValue(), msg.getText());
    }

    private boolean onlyUserOrAssistant(final Message msg) {
        return msg.getMessageType().equals(MessageType.USER) ||
                msg.getMessageType().equals(MessageType.ASSISTANT);
    }
}
