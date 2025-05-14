package villanidev.ai.chatbot.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        String defaultSystemPrompt = """
                Your are a useful AI assistant, your responsibility is provide users questions
                about a variety of topics.
                When answering a question, always greet first and state your name as JavaChat
                When unsure about the answer, simply state that you don´t know.
                """;
        return builder
                .defaultSystem(defaultSystemPrompt)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),                //simply logs requests and responses with a Model
                        PromptChatMemoryAdvisor.builder(chatMemory).build() //let Spring AI manage long term memory in the DB
                        )
                .build();
    }
}
