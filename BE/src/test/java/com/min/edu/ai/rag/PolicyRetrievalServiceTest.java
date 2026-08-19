package com.min.edu.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

class PolicyRetrievalServiceTest {

    private final VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    private final ObjectProvider<VectorStore> vectorStoreProvider =
        org.mockito.Mockito.mock(ObjectProvider.class);

    @Test
    void skipsRetrievalWhenRagDisabled() {
        PolicyRetrievalService service = new PolicyRetrievalService(properties(false, 1), vectorStoreProvider);

        PolicyRetrievalResult result = service.retrieve(
            new PolicyRetrievalRequest(PolicyType.REFUND, "PAYMENT_NOT_PAID")
        );

        assertThat(result.used()).isFalse();
        verify(vectorStoreProvider, never()).getIfAvailable();
    }

    @Test
    void searchesWithPolicyTypeAndReasonCodeFilter() {
        given(vectorStoreProvider.getIfAvailable()).willReturn(vectorStore);
        given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of(
            new Document(
                "content",
                Map.of(
                    "policyType", "REFUND",
                    "reasonCode", "EXCHANGE_CODE_ALREADY_REDEEMED",
                    "section", "EXCHANGE_CODE_ALREADY_REDEEMED",
                    "documentName", "refund-policy.md",
                    "version", 1
                )
            )
        ));
        PolicyRetrievalService service = new PolicyRetrievalService(properties(true, 1), vectorStoreProvider);

        PolicyRetrievalResult result = service.retrieve(
            new PolicyRetrievalRequest(PolicyType.REFUND, "EXCHANGE_CODE_ALREADY_REDEEMED")
        );

        assertThat(result.used()).isTrue();
        assertThat(result.retrievedCount()).isEqualTo(1);
        assertThat(result.context()).contains("refund-policy.md#EXCHANGE_CODE_ALREADY_REDEEMED");
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getQuery())
            .isEqualTo("REFUND EXCHANGE_CODE_ALREADY_REDEEMED policy");
        assertThat(captor.getValue().getTopK()).isEqualTo(1);
        assertThat(captor.getValue().getFilterExpression().toString())
            .contains("policyType")
            .contains("REFUND")
            .contains("reasonCode")
            .contains("EXCHANGE_CODE_ALREADY_REDEEMED");
    }

    @Test
    void returnsEmptyResultWhenNoDocumentsFound() {
        given(vectorStoreProvider.getIfAvailable()).willReturn(vectorStore);
        given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of());
        PolicyRetrievalService service = new PolicyRetrievalService(properties(true, 1), vectorStoreProvider);

        PolicyRetrievalResult result = service.retrieve(
            new PolicyRetrievalRequest(PolicyType.ADMISSION, "ALREADY_USED")
        );

        assertThat(result.used()).isFalse();
        assertThat(result.context()).isEqualTo("NONE");
    }

    @Test
    void returnsEmptyResultWhenVectorStoreThrows() {
        given(vectorStoreProvider.getIfAvailable()).willReturn(vectorStore);
        given(vectorStore.similaritySearch(any(SearchRequest.class)))
            .willThrow(new IllegalStateException("pgvector down"));
        PolicyRetrievalService service = new PolicyRetrievalService(properties(true, 1), vectorStoreProvider);

        PolicyRetrievalResult result = service.retrieve(
            new PolicyRetrievalRequest(PolicyType.ADMISSION, "ALREADY_USED")
        );

        assertThat(result.used()).isFalse();
        assertThat(result.retrievedCount()).isZero();
    }

    private RagProperties properties(boolean enabled, int topK) {
        return new RagProperties(
            enabled,
            "rag-key",
            "gemini-embedding-2",
            768,
            topK,
            "classpath:ai/policies/",
            "eventoday_policy_vector",
            false
        );
    }
}
