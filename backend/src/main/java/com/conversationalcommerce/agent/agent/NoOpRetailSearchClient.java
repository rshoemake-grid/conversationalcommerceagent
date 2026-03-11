package com.conversationalcommerce.agent.agent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * No-op implementation when GCP is disabled. Returns empty product list.
 */
@Component
@ConditionalOnProperty(name = "conversational-commerce.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpRetailSearchClient implements RetailSearchClient {

    @Override
    public List<AgentResponse.ProductResult> search(String placement, String branch, String query, String visitorId) {
        return List.of();
    }
}
