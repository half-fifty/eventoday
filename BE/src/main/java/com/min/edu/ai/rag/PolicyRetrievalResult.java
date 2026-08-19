package com.min.edu.ai.rag;

public record PolicyRetrievalResult(
        boolean used,
        int retrievedCount,
        String context) {

    public static PolicyRetrievalResult empty() {
        return new PolicyRetrievalResult(false, 0, "NONE");
    }

    public static PolicyRetrievalResult from(String context, int retrievedCount) {
        if (context == null || context.isBlank() || retrievedCount <= 0) {
            return empty();
        }
        return new PolicyRetrievalResult(true, retrievedCount, context);
    }
}
