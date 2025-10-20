package villanidev.ai.chatbot.rag.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import villanidev.ai.chatbot.rag.model.DocumentMetadata;

import java.util.List;

@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, Long> {

    List<DocumentMetadata> findByStatus(String status);

    List<DocumentMetadata> findByOrderByUploadedAtDesc();
}

