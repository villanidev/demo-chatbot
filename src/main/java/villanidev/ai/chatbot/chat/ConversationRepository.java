package villanidev.ai.chatbot.chat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ConversationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ConversationSummaryDTO> findAllConversations() {
        String sql = """
            SELECT
                conversation_id,
                MIN("timestamp") as first_message_time,
                MAX("timestamp") as last_message_time,
                (SELECT content FROM SPRING_AI_CHAT_MEMORY 
                 WHERE conversation_id = main.conversation_id 
                 AND type = 'USER' 
                 ORDER BY "timestamp" 
                 LIMIT 1) as first_user_message
            FROM SPRING_AI_CHAT_MEMORY main
            GROUP BY conversation_id
            ORDER BY last_message_time DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String firstMessage = rs.getString("first_user_message");
            String title = firstMessage != null && !firstMessage.isEmpty()
                ? (firstMessage.length() > 50 ? firstMessage.substring(0, 50) + "..." : firstMessage)
                : "New Chat";

            return new ConversationSummaryDTO(
                rs.getString("conversation_id"),
                title,
                rs.getTimestamp("first_message_time"),
                rs.getTimestamp("last_message_time")
            );
        });
    }
}
