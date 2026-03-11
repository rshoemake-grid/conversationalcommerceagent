package com.conversationalcommerce.agent.agent;

import com.google.cloud.retail.v2beta.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Real implementation using GCP Retail SearchServiceClient.
 */
@Component
@ConditionalOnProperty(name = "conversational-commerce.enabled", havingValue = "true")
public class RetailSearchClientImpl implements RetailSearchClient {

    private final SearchServiceClient client;

    public RetailSearchClientImpl() throws Exception {
        this.client = SearchServiceClient.create();
    }

    @Override
    public List<AgentResponse.ProductResult> search(String placement, String branch, String query, String visitorId) {
        var req = SearchRequest.newBuilder()
                .setPlacement(placement)
                .setBranch(branch)
                .setQuery(query)
                .setVisitorId(visitorId)
                .setPageSize(20)
                .build();

        SearchServiceClient.SearchPagedResponse pagedResponse = client.search(req);

        List<AgentResponse.ProductResult> results = new ArrayList<>();
        for (SearchResponse.SearchResult sr : pagedResponse.iterateAll()) {
            var product = sr.getProduct();
            String id = product.getName();
            String title = product.getTitle();
            String desc = product.getDescription();
            String price = product.getPriceInfo().getPrice() > 0
                    ? String.format("$%.2f", product.getPriceInfo().getPrice())
                    : "";
            String imageUri = product.getImagesCount() > 0 ? product.getImages(0).getUri() : null;
            results.add(new AgentResponse.ProductResult(id, title, desc, price, imageUri));
        }
        return results;
    }
}
