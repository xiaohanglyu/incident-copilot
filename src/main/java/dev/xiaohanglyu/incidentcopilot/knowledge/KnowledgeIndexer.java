package dev.xiaohanglyu.incidentcopilot.knowledge;

import dev.xiaohanglyu.incidentcopilot.shared.config.CopilotProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Reads every Markdown file under the configured knowledge location and feeds it to
 * {@link KnowledgeService} at startup, so that directory is the single source of truth.
 */
@Component
public class KnowledgeIndexer implements ApplicationRunner {

    private static final Log logger = LogFactory.getLog(KnowledgeIndexer.class);

    private final KnowledgeService knowledgeService;
    private final CopilotProperties properties;

    public KnowledgeIndexer(KnowledgeService knowledgeService, CopilotProperties properties) {
        this.knowledgeService = knowledgeService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources(properties.knowledgeLocation());

        int chunks = 0;
        for (Resource resource : resources) {
            String source = resource.getFilename();
            String markdown = resource.getContentAsString(StandardCharsets.UTF_8);
            for (String chunk : chunk(markdown)) {
                knowledgeService.index(source, chunk);
                chunks++;
            }
        }
        logger.info("Indexed %d chunks from %d knowledge files".formatted(chunks, resources.length));
    }

    /**
     * Splits on level-two headings and repeats the document title in every chunk, so a
     * retrieved section still says what it is about. Crude, but runbooks are written as
     * exactly this shape.
     */
    private List<String> chunk(String markdown) {
        String[] sections = markdown.split("(?m)^## ");
        String title = sections[0].strip();

        if (sections.length == 1) {
            return List.of(title);
        }
        List<String> chunks = new ArrayList<>();
        for (int i = 1; i < sections.length; i++) {
            chunks.add(title + "\n\n## " + sections[i].strip());
        }
        return chunks;
    }
}
