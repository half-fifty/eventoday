package com.min.edu.ai.rag;

public record PolicyRetrievalRequest(
        PolicyType policyType,
        String reasonCode) {

    public String deterministicQuery() {
        return policyType.name() + " " + reasonCode + " policy";
    }
}
