package dev.xiaohanglyu.incidentcopilot.knowledge;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeConfig {

    /**
     * In-memory index, rebuilt on every launch. Swapping in pgvector later touches
     * only this method — provided the embedding model, and therefore the vector
     * dimension, stays the same.
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
