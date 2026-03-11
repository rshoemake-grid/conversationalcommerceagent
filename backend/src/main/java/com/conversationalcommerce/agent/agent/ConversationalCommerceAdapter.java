package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Adapter that wraps the GCP Conversational Commerce API as a ConversationalAgent.
 */
@Component
public class ConversationalCommerceAdapter implements ConversationalAgent {

    public static final String AGENT_ID = "conversational-commerce";

    private final ConversationalCommerceClient client;
    private final RetailSearchClient searchClient;
    private final ConversationalCommerceConfig config;

    public ConversationalCommerceAdapter(
            ConversationalCommerceClient client,
            RetailSearchClient searchClient,
            ConversationalCommerceConfig config) {
        this.client = client;
        this.searchClient = searchClient;
        this.config = config;
    }

    @Override
    public AgentResponse sendMessage(String conversationId, String message, Map<String, Object> context) {
        String visitorId = getVisitorId(context);

        var request = new ConversationalCommerceClient.ConversationalCommerceRequest(
                config.placement(),
                config.branch(),
                message,
                visitorId,
                conversationId != null ? conversationId : ""
        );

        var result = client.search(request);

        List<AgentResponse.ProductResult> products = List.of();
        if (result.refinedQuery() != null && !result.refinedQuery().isEmpty()) {
            products = searchClient.search(
                    config.placement(),
                    config.branch(),
                    result.refinedQuery(),
                    visitorId
            );
        }

        return AgentResponse.builder()
                .text(result.text())
                .conversationId(result.conversationId())
                .refinedQuery(result.refinedQuery())
                .products(products)
                .queryType(result.queryType())
                .build();
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    private String getVisitorId(Map<String, Object> context) {
        if (context != null && context.containsKey("visitorId")) {
            Object v = context.get("visitorId");
            return v != null ? v.toString() : config.defaultVisitorId();
        }
        return config.defaultVisitorId();
    }
}
