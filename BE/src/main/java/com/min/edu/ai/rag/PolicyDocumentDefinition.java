package com.min.edu.ai.rag;

public record PolicyDocumentDefinition(
        PolicyType policyType,
        String documentName,
        int version,
        boolean reasonCodeMapped) {

    public PolicyDocumentDefinition(
            PolicyType policyType,
            String documentName,
            int version) {
        this(policyType, documentName, version, true);
    }
}
