package com.conversationalcommerce.agent.agent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub implementation when GCP Conversational Commerce is disabled.
 * Returns placeholder responses for development/testing without GCP credentials.
 */
@Component
@ConditionalOnProperty(name = "conversational-commerce.enabled", havingValue = "false", matchIfMissing = true)
public class StubConversationalCommerceClient implements ConversationalCommerceClient {

    @Override
    public ConversationalCommerceResult search(ConversationalCommerceRequest request) {
        return new ConversationalCommerceResult(
                "I'm the Conversational Commerce agent (stub mode). Configure GCP to enable real product search. You asked: " + request.query(),
                "stub-conv-" + System.currentTimeMillis(),
                request.query(),
                "UNKNOWN"
        );
    }
}
