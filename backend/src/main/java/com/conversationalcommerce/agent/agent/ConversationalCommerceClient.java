package com.conversationalcommerce.agent.agent;

/**
 * Abstraction for the GCP Conversational Commerce API.
 * Enables mocking in tests without requiring real GCP credentials.
 */
public interface ConversationalCommerceClient {

    /**
     * Send a conversational search request and return the aggregated response.
     */
    ConversationalCommerceResult search(ConversationalCommerceRequest request);

    record ConversationalCommerceRequest(
            String placement,
            String branch,
            String query,
            String visitorId,
            String conversationId,
            String imageBase64
    ) {}

    /** Source of the response: "agent" = GCP Conversational Commerce API, "app" = app fallback */
    record ConversationalCommerceResult(
            String text,
            String conversationId,
            String refinedQuery,
            String queryType,
            String source
    ) {}
}
