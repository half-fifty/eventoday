package com.min.edu.ai.rag;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class PolicyRetrievalService {

    private final RagProperties properties;
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    public PolicyRetrievalService(
            RagProperties properties,
            ObjectProvider<VectorStore> vectorStoreProvider) {
        this.properties = properties;
        this.vectorStoreProvider = vectorStoreProvider;
    }

    public PolicyRetrievalResult retrieve(PolicyRetrievalRequest request) {
        if (!properties.enabled()) {
            return PolicyRetrievalResult.empty();
        }
        if (request == null
                || request.policyType() == null
                || !StringUtils.hasText(request.reasonCode())) {
            return PolicyRetrievalResult.empty();
        }
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            log.warn("RAG policy retrieval skipped. policyType={}, reasonCode={}, failureCategory=VECTOR_STORE_MISSING",
                request.policyType(), request.reasonCode());
            return PolicyRetrievalResult.empty();
        }

        long startedAt = System.nanoTime();
        try {
            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            SearchRequest searchRequest = SearchRequest.builder()
                .query(request.deterministicQuery())
                .topK(properties.topK())
                .similarityThresholdAll()
                .filterExpression(builder.and(
                    builder.eq("policyType", request.policyType().name()),
                    builder.eq("reasonCode", request.reasonCode())
                ).build())
                .build();

            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            String context = toPolicyContext(documents);
            log.info("RAG policy retrieval completed. policyType={}, reasonCode={}, retrievedDocumentCount={}, ragUsed={}, latencyMs={}",
                request.policyType(),
                request.reasonCode(),
                documents.size(),
                !documents.isEmpty(),
                elapsedMillis(startedAt));
            return PolicyRetrievalResult.from(context, documents.size());
        } catch (RuntimeException exception) {
            log.warn("RAG policy retrieval failed. policyType={}, reasonCode={}, failureCategory={}, latencyMs={}",
                request.policyType(),
                request.reasonCode(),
                exception.getClass().getSimpleName(),
                elapsedMillis(startedAt));
            return PolicyRetrievalResult.empty();
        }
    }

    public PolicyRetrievalResult retrieveForCopilot(String safeQuery) {
        if (!properties.enabled()) {
            return PolicyRetrievalResult.empty();
        }
        if (!StringUtils.hasText(safeQuery)) {
            return PolicyRetrievalResult.empty();
        }
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            log.warn("RAG copilot policy retrieval skipped. failureCategory=VECTOR_STORE_MISSING");
            return PolicyRetrievalResult.empty();
        }

        long startedAt = System.nanoTime();
        try {
            SearchRequest searchRequest = SearchRequest.builder()
                .query(safeQuery)
                .topK(properties.copilotTopK())
                .similarityThresholdAll()
                .build();

            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            String context = toCopilotPolicyContext(documents);
            log.info("RAG copilot policy retrieval completed. retrievedDocumentCount={}, ragUsed={}, latencyMs={}",
                documents.size(),
                !documents.isEmpty(),
                elapsedMillis(startedAt));
            return PolicyRetrievalResult.from(context, documents.size());
        } catch (RuntimeException exception) {
            log.warn("RAG copilot policy retrieval failed. failureCategory={}, latencyMs={}",
                exception.getClass().getSimpleName(),
                elapsedMillis(startedAt));
            return PolicyRetrievalResult.empty();
        }
    }

    private String toPolicyContext(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "NONE";
        }
        StringBuilder builder = new StringBuilder();
        for (Document document : documents) {
            builder.append("[")
                .append(document.getMetadata().get("documentName"))
                .append("#")
                .append(document.getMetadata().get("section"))
                .append("]\n")
                .append(document.getText())
                .append("\n\n");
        }
        return builder.toString().trim();
    }

    private String toCopilotPolicyContext(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "NONE";
        }
        StringBuilder builder = new StringBuilder();
        for (Document document : documents) {
            builder.append("Policy Type: ")
                .append(document.getMetadata().get("policyType"))
                .append('\n')
                .append("Section: ")
                .append(document.getMetadata().get("section"))
                .append('\n');
            Object reasonCode = document.getMetadata().get("reasonCode");
            if (reasonCode != null) {
                builder.append("Reason Code: ")
                    .append(reasonCode)
                    .append('\n');
            }
            builder.append("Document: ")
                .append(document.getMetadata().get("documentName"))
                .append('\n')
                .append("Content:\n")
                .append(document.getText())
                .append("\n\n---\n\n");
        }
        return builder.toString().trim();
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
