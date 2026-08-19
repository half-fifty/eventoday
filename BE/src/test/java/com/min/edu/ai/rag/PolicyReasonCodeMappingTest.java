package com.min.edu.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.min.edu.admission.policy.AdmissionEligibilityReasonCode;
import com.min.edu.payment.policy.RefundEligibilityReasonCode;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PolicyReasonCodeMappingTest {

    private final PolicyDocumentLoader loader = new PolicyDocumentLoader();

    @Test
    void refundFailureReasonCodesHavePolicySections() {
        Set<String> sections = loader.load(
                "classpath:ai/policies/",
                new PolicyDocumentDefinition(PolicyType.REFUND, "refund-policy.md", 1)
            )
            .stream()
            .map(document -> (String) document.getMetadata().get("reasonCode"))
            .collect(Collectors.toSet());

        Set<String> expected = Arrays.stream(RefundEligibilityReasonCode.values())
            .filter(reasonCode -> reasonCode != RefundEligibilityReasonCode.ELIGIBLE)
            .map(Enum::name)
            .collect(Collectors.toSet());

        assertThat(sections).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void admissionFailureReasonCodesHavePolicySections() {
        Set<String> sections = loader.load(
                "classpath:ai/policies/",
                new PolicyDocumentDefinition(PolicyType.ADMISSION, "admission-policy.md", 1)
            )
            .stream()
            .map(document -> (String) document.getMetadata().get("reasonCode"))
            .collect(Collectors.toSet());

        Set<String> expected = Arrays.stream(AdmissionEligibilityReasonCode.values())
            .filter(reasonCode -> reasonCode != AdmissionEligibilityReasonCode.ELIGIBLE)
            .map(Enum::name)
            .collect(Collectors.toSet());

        assertThat(sections).containsExactlyInAnyOrderElementsOf(expected);
    }
}
