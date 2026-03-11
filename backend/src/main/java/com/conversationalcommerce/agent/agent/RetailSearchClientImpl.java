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
            if (product == null) continue;
            String id = product.getName() != null ? product.getName() : "";
            String title = product.getTitle() != null ? product.getTitle() : "";
            String desc = product.getDescription() != null ? product.getDescription() : "";
            String price = "";
            if (product.hasPriceInfo()) {
                double p = product.getPriceInfo().getPrice();
                price = p > 0 ? String.format("$%.2f", p) : "";
            }
            String imageUri = null;
            if (product.getImagesCount() > 0) {
                var img = product.getImages(0);
                imageUri = img != null ? img.getUri() : null;
            }
            results.add(new AgentResponse.ProductResult(id, title, desc, price, imageUri));
        }
        return results;
    }
}
