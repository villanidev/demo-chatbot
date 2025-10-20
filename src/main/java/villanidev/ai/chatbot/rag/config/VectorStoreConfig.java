package villanidev.ai.chatbot.rag.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import villanidev.ai.chatbot.rag.service.InMemoryVectorStore;
import villanidev.ai.chatbot.rag.service.PgVectorStore;

@Configuration
public class VectorStoreConfig {

    @Bean
    @Profile("dev")
    public InMemoryVectorStore inMemoryVectorStore(EmbeddingModel embeddingModel) {
        return new InMemoryVectorStore(embeddingModel);
    }

    @Bean  
    @Profile("prod")
    public PgVectorStore pgVectorStore(EmbeddingModel embeddingModel, JdbcTemplate jdbcTemplate) {
        return new PgVectorStore(embeddingModel, jdbcTemplate);
    }
}
