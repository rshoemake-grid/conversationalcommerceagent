package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.conversationalcommerce.agent.gemini.ClarifyingQuestionGenerator;
import com.conversationalcommerce.agent.orchestration.ContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter that wraps the GCP Conversational Commerce API as a ConversationalAgent.
 */
@Component
public class ConversationalCommerceAdapter implements ConversationalAgent {

    public static final String AGENT_ID = "conversational-commerce";
    private static final String FALLBACK_CLARIFY = "I found many %s matching your search. To narrow it down, could you tell me more? " +
            "For example: What type or form? (e.g. raw, cooked, peeled — or style, color) Any size or price range?";

    private static final Logger log = LoggerFactory.getLogger(ConversationalCommerceAdapter.class);

    private final ConversationalCommerceClient client;
    private final RetailSearchClient searchClient;
    private final ConversationalCommerceConfig config;
    private final Optional<ClarifyingQuestionGenerator> clarifyingGenerator;

    public ConversationalCommerceAdapter(
            ConversationalCommerceClient client,
            RetailSearchClient searchClient,
            ConversationalCommerceConfig config,
            Optional<ClarifyingQuestionGenerator> clarifyingGenerator) {
        this.client = client;
        this.searchClient = searchClient;
        this.config = config;
        this.clarifyingGenerator = clarifyingGenerator != null ? clarifyingGenerator : Optional.empty();
    }

    @Override
    public AgentResponse sendMessage(String conversationId, String message, Map<String, Object> context) {
        String visitorId = getVisitorId(context);
        String imageBase64 = (String) context.get("imageBase64");
        String query = (message != null && !message.isBlank()) ? message
                : (imageBase64 != null ? "Find products similar to this image" : "");

        var request = new ConversationalCommerceClient.ConversationalCommerceRequest(
                config.placement(),
                config.branch(),
                query,
                visitorId,
                conversationId != null ? conversationId : "",
                imageBase64
        );

        var result = client.search(request);

        List<AgentResponse.ProductResult> products = List.of();
        if (result.refinedQuery() != null && !result.refinedQuery().isEmpty()) {
            try {
                products = searchClient.search(
                        config.placement(),
                        config.branch(),
                        result.refinedQuery(),
                        visitorId
                );
            } catch (Exception e) {
                log.warn("Product search failed (may use gRPC; try transport=rest for full REST): {}", e.getMessage());
            }
        }

        String text = result.text() != null ? result.text() : "";
        List<AgentResponse.ProductResult> productsToReturn = products;
        int productCountThreshold = 8;
        String refinedQuery = result.refinedQuery();
        boolean isSearchingFallback = text.startsWith("Searching for:");
        boolean useConvoCommerceOnly = "convo_commerce".equals(context.get("orchestrationMode"));
        boolean hasRefinedQuery = refinedQuery != null && !refinedQuery.isEmpty();

        String responseSource = result.source() != null ? result.source() : "agent";
        if (useConvoCommerceOnly) {
            // Approach A: Pass through agent response as-is; no app overrides
            if (!products.isEmpty() && products.size() > productCountThreshold) {
                productsToReturn = List.of();
            } else if (!products.isEmpty() && isSearchingFallback) {
                // Replace "Searching for: X" placeholder with a proper count when we have products
                int n = products.size();
                text = n == 1
                        ? "I found 1 product matching your request."
                        : "I found " + n + " products matching your request.";
                responseSource = "app";
            } else if (products.isEmpty() && hasRefinedQuery) {
                text = "No products found.";
                responseSource = "app";
            }
        } else {
            // Approach B: Use our clarifying logic when many products
            if (!products.isEmpty() && products.size() > productCountThreshold) {
                String categoryHint = refinedQuery != null ? refinedQuery : "products";
                int productCount = products.size();
                text = clarifyingGenerator
                        .map(gen -> gen.generate(categoryHint, productCount))
                        .filter(t -> t != null && !t.isBlank())
                        .orElse(FALLBACK_CLARIFY.formatted(categoryHint));
                productsToReturn = List.of();
                responseSource = "app";
            } else if (!products.isEmpty()) {
                int n = products.size();
                String countPhrase = n == 1
                        ? "I found 1 product matching your request."
                        : "I found " + n + " products matching your request.";
                text = (text.isEmpty() || isSearchingFallback) ? countPhrase : countPhrase + "\n\n" + text;
                responseSource = "app";
            } else if (products.isEmpty() && hasRefinedQuery) {
                text = "No products found.";
                responseSource = "app";
            }
        }

        return AgentResponse.builder()
                .text(text)
                .conversationId(result.conversationId())
                .refinedQuery(result.refinedQuery())
                .products(productsToReturn)
                .queryType(result.queryType())
                .source(responseSource)
                .build();
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    private String getVisitorId(Map<String, Object> context) {
        return ContextUtils.getVisitorId(context, config.defaultVisitorId());
    }
}
