package com.conversationalcommerce.agent.agent;

import java.util.List;
import java.util.Map;

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
        String source,
        String rawResponse,
        List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers
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
        private String rawResponse;
        private List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers;

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

        public Builder rawResponse(String rawResponse) {
            this.rawResponse = rawResponse;
            return this;
        }

        public Builder suggestedAnswers(List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers) {
            this.suggestedAnswers = suggestedAnswers;
            return this;
        }

        public AgentResponse build() {
            return new AgentResponse(text, conversationId, refinedQuery, products, queryType, source != null ? source : "agent", rawResponse, suggestedAnswers);
        }
    }

    /** Product from search. id=resource name; productId=short id; gtin=UPC/GTIN. detailsFetched=true when filled via Product.Get. */
    public record ProductResult(
            String id,
            String title,
            String description,
            String price,
            String imageUri,
            String gtin,
            String productId,
            List<String> categories,
            List<String> brands,
            String uri,
            String availability,
            List<String> sizes,
            List<String> materials,
            Map<String, Object> attributes,
            boolean detailsFetched
    ) {
        public static ProductResult of(String id, String title, String description, String price, String imageUri) {
            return new ProductResult(id, title, description, price, imageUri, null, null, null, null, null, null, null, null, null, false);
        }
    }
}
