package villanidev.ai.chatbot.chat;

import java.sql.Timestamp;

public record ConversationSummaryDTO(
    String conversationId,
    String title,
    Timestamp firstMessageTime,
    Timestamp lastMessageTime
) {}

