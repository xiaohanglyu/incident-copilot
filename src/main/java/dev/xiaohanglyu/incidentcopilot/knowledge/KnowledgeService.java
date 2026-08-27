package dev.xiaohanglyu.incidentcopilot.knowledge;

import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

    private static final String SOURCE = "source";

    private final VectorStore vectorStore;

    public KnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void index(String source, String content) {
        vectorStore.add(List.of(new Document(content, Map.of(SOURCE, source))));
    }

    public List<KnowledgeSnippet> search(String query, int topK) {
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());

        if (hits == null) {
            return List.of();
        }
        return hits.stream()
                .map(hit -> new KnowledgeSnippet(
                        String.valueOf(hit.getMetadata().getOrDefault(SOURCE, "unknown")),
                        hit.getText()))
                .toList();
    }
}
