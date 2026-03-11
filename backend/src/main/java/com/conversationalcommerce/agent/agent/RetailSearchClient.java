package com.conversationalcommerce.agent.agent;

import java.util.List;

/**
 * Abstraction for the GCP Retail Search API (product search).
 */
public interface RetailSearchClient {

    /**
     * Search for products and return results.
     *
     * @return list of product results, or empty list if none
     */
    List<AgentResponse.ProductResult> search(
            String placement,
            String branch,
            String query,
            String visitorId
    );
}
