package com.min.edu.ai.rag;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.rag", name = "ingestion-enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "ai.rag", name = "enabled", havingValue = "true")
public class PolicyIngestionRunner implements ApplicationRunner {

    private final RagProperties properties;
    private final PolicyDocumentLoader documentLoader;
    private final VectorStore vectorStore;

    public PolicyIngestionRunner(
            RagProperties properties,
            PolicyDocumentLoader documentLoader,
            VectorStore vectorStore) {
        this.properties = properties;
        this.documentLoader = documentLoader;
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("RAG policy ingestion skipped. ragEnabled=false");
            return;
        }
        long startedAt = System.nanoTime();
        try {
            List<Document> documents = documentLoader.loadAll(properties.policyLocation());
            deleteExistingPolicyDocuments();
            vectorStore.add(documents);
            log.info("RAG policy ingestion completed. documentCount={}, latencyMs={}",
                documents.size(), elapsedMillis(startedAt));
        } catch (RuntimeException exception) {
            log.warn("RAG policy ingestion failed. failureCategory={}, latencyMs={}",
                exception.getClass().getSimpleName(), elapsedMillis(startedAt));
        }
    }

    private void deleteExistingPolicyDocuments() {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        vectorStore.delete(builder.in("policyType", PolicyType.REFUND.name(), PolicyType.ADMISSION.name()).build());
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
