package villanidev.ai.chatbot.chat;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private final ChatMemory chatMemory;
    private final ConversationRepository conversationRepository;

    public ChatService(ChatMemory chatMemory, ConversationRepository conversationRepository) {
        this.chatMemory = chatMemory;
        this.conversationRepository = conversationRepository;
    }

    public String generateChatId() {
        return UUID.randomUUID().toString();
    }

    public List<ConversationSummaryDTO> getAllConversations() {
        return conversationRepository.findAllConversations();
    }

    public List<MessageResponseDTO> getConversationMessages(String chatId) {
        return chatMemory.get(chatId)
                .stream()
                .filter(this::onlyUserOrAssistant)
                .map(this::buildResponseDTO)
                .toList();
    }

    public void deleteConversation(String chatId) {
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

