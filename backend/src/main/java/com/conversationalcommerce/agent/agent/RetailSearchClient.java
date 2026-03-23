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
    default List<AgentResponse.ProductResult> search(
            String placement,
            String branch,
            String query,
            String visitorId
    ) {
        return search(placement, branch, query, visitorId, null);
    }

    /**
     * Search for products with an optional filter.
     *
     * @param filter optional filter expression (e.g. brands: ANY("Nike") or attributes.brandId: ANY("BHB/NPM"))
     * @return list of product results, or empty list if none
     */
    List<AgentResponse.ProductResult> search(
            String placement,
            String branch,
            String query,
            String visitorId,
            String filter
    );
}
