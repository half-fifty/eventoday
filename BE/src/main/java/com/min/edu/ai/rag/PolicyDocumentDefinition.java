package com.min.edu.ai.rag;

public record PolicyDocumentDefinition(
        PolicyType policyType,
        String documentName,
        int version) {
}
