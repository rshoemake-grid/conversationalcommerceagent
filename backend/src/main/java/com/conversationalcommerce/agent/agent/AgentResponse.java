package com.conversationalcommerce.agent.agent;

import java.util.List;

/**
 * Response from a conversational agent.
 */
public record AgentResponse(
        String text,
        String conversationId,
        String refinedQuery,
        List<ProductResult> products,
        String queryType
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String text;
        private String conversationId;
        private String refinedQuery;
        private List<ProductResult> products;
        private String queryType;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder refinedQuery(String refinedQuery) {
            this.refinedQuery = refinedQuery;
            return this;
        }

        public Builder products(List<ProductResult> products) {
            this.products = products;
            return this;
        }

        public Builder queryType(String queryType) {
            this.queryType = queryType;
            return this;
        }

        public AgentResponse build() {
            return new AgentResponse(text, conversationId, refinedQuery, products, queryType);
        }
    }

    public record ProductResult(String id, String title, String description, String price, String imageUri) {}
}
