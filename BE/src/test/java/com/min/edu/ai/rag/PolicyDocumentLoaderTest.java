package com.min.edu.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class PolicyDocumentLoaderTest {

    private final PolicyDocumentLoader loader = new PolicyDocumentLoader();

    @Test
    void loadsRefundAndAdmissionPolicySectionsAsDocuments() {
        List<Document> documents = loader.loadAll("classpath:ai/policies/");

        assertThat(documents)
            .extracting(document -> document.getMetadata().get("reasonCode"))
            .contains("EXCHANGE_CODE_ALREADY_REDEEMED", "ALREADY_USED");
        Document refundDocument = documents.stream()
            .filter(document -> "EXCHANGE_CODE_ALREADY_REDEEMED"
                .equals(document.getMetadata().get("reasonCode")))
            .findFirst()
            .orElseThrow();
        assertThat(refundDocument.getText())
            .contains("Refund Policy")
            .contains("reasonCode: EXCHANGE_CODE_ALREADY_REDEEMED")
            .contains("교환 코드");
        assertThat(refundDocument.getMetadata())
            .containsEntry("policyType", "REFUND")
            .containsEntry("section", "EXCHANGE_CODE_ALREADY_REDEEMED")
            .containsEntry("documentName", "refund-policy.md")
            .containsEntry("version", 1);
    }

    @Test
    void loadsGeneralOperationPolicySectionsWithoutReasonCodeMetadata() {
        List<Document> documents = loader.load(
            "classpath:ai/policies/",
            new PolicyDocumentDefinition(
                PolicyType.EXCHANGE_CODE,
                "exchange-code-policy.md",
                1,
                false
            )
        );

        Document statusGuide = documents.stream()
            .filter(document -> "EXCHANGE_CODE_STATUS_GUIDE"
                .equals(document.getMetadata().get("section")))
            .findFirst()
            .orElseThrow();

        assertThat(statusGuide.getMetadata())
            .containsEntry("policyType", "EXCHANGE_CODE")
            .containsEntry("section", "EXCHANGE_CODE_STATUS_GUIDE")
            .containsEntry("documentName", "exchange-code-policy.md")
            .containsEntry("version", 1);
        assertThat(statusGuide.getMetadata()).doesNotContainKey("reasonCode");
        assertThat(statusGuide.getText())
            .contains("Exchange Code Policy")
            .contains("section: EXCHANGE_CODE_STATUS_GUIDE")
            .doesNotContain("reasonCode:");
    }

    @Test
    void defaultDefinitionsIncludeCopilotOperationPolicies() {
        List<Document> documents = loader.loadAll("classpath:ai/policies/");

        assertThat(documents)
            .extracting(document -> document.getMetadata().get("policyType"))
            .contains("REFUND", "ADMISSION", "EXCHANGE_CODE", "TICKET_OPERATION");
        assertThat(documents)
            .extracting(document -> document.getMetadata().get("section"))
            .contains("EXCHANGE_CODE_REDEMPTION_REQUIREMENTS", "CHECK_IN_ELIGIBILITY_SOURCE_OF_TRUTH");
    }

    @Test
    void rejectsInvalidSectionHeading() {
        String markdown = """
            # Policy

            ## invalid-heading
            body
            """;

        assertThatThrownBy(() -> loader.parseSections(markdown, "invalid.md"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid policy section heading");
    }

    @Test
    void rejectsEmptySection() {
        String markdown = """
            # Policy

            ## EMPTY_SECTION

            ## NEXT_SECTION
            body
            """;

        assertThatThrownBy(() -> loader.parseSections(markdown, "empty.md"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Empty policy section");
    }

    @Test
    void rejectsDuplicateReasonCodeSection() {
        String markdown = """
            # Policy

            ## DUPLICATE
            first

            ## DUPLICATE
            second
            """;

        assertThatThrownBy(() -> loader.parseSections(markdown, "duplicate.md"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate policy section");
    }
}
