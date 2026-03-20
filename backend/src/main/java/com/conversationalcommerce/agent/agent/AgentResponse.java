package com.conversationalcommerce.agent.agent;

import java.util.List;

/**
 * Response from a conversational agent.
 * @param source "agent" = Conversational Commerce or ADK agent, "app" = app-generated fallback
 */
public record AgentResponse(
        String text,
        String conversationId,
        String refinedQuery,
        List<ProductResult> products,
        String queryType,
        String source
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
        private String source;

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

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public AgentResponse build() {
            return new AgentResponse(text, conversationId, refinedQuery, products, queryType, source != null ? source : "agent");
        }
    }

    public record ProductResult(String id, String title, String description, String price, String imageUri) {}
}
