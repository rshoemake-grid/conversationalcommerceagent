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
    private final ProductEnrichmentService enrichmentService;
    private final ConversationalCommerceConfig config;
    private final Optional<ClarifyingQuestionGenerator> clarifyingGenerator;

    public ConversationalCommerceAdapter(
            ConversationalCommerceClient client,
            RetailSearchClient searchClient,
            ProductEnrichmentService enrichmentService,
            ConversationalCommerceConfig config,
            Optional<ClarifyingQuestionGenerator> clarifyingGenerator) {
        this.client = client;
        this.searchClient = searchClient;
        this.enrichmentService = enrichmentService;
        this.config = config;
        this.clarifyingGenerator = clarifyingGenerator != null ? clarifyingGenerator : Optional.empty();
    }

    @Override
    public AgentResponse sendMessage(String conversationId, String message, Map<String, Object> context) {
        String visitorId = getVisitorId(context);
        String imageBase64 = (String) context.get("imageBase64");
        String userInput = (message != null && !message.isBlank()) ? message.trim()
                : (imageBase64 != null ? "Find products similar to this image" : "");
        final String query = expandShortAttributeValue(userInput, context);
        final String originalUserInput = userInput;

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
        String searchQuery = result.refinedQuery();
        boolean usedNoPreferenceRecovery = false;

        // When RETAIL_IRRELEVANT and user said "Any"/"no preference", use previous refined query to search
        if ("RETAIL_IRRELEVANT".equals(result.queryType()) && isNoPreference(query)
                && (searchQuery == null || searchQuery.isEmpty())) {
            String prevRefined = (String) context.get("previousRefinedQuery");
            if (prevRefined != null && !prevRefined.isBlank()) {
                searchQuery = prevRefined.trim();
                usedNoPreferenceRecovery = true;
                log.debug("RETAIL_IRRELEVANT + no-preference: using previousRefinedQuery \"{}\"", searchQuery);
            }
        }

        if (searchQuery != null && !searchQuery.isEmpty()) {
            try {
                String filter = buildBrandFilterWhenApplicable(query, result.suggestedAnswers(), context);
                products = searchClient.search(
                        config.placement(),
                        config.branch(),
                        searchQuery,
                        visitorId,
                        filter
                );
                products = enrichmentService.enrich(products);
            } catch (Exception e) {
                log.warn("Product search failed (may use gRPC; try transport=rest for full REST): {}", e.getMessage());
            }
        }

        String text = result.text() != null ? result.text() : "";
        if (usedNoPreferenceRecovery) {
            if (!products.isEmpty()) {
                int n = products.size();
                text = n == 1
                        ? "I found 1 product matching your request."
                        : "I found " + n + " products matching your request.";
            } else {
                text = "No products found.";
            }
        }
        List<AgentResponse.ProductResult> productsToReturn = products;
        int productCountThreshold = 8;
        if (products.isEmpty()) {
            log.info("Products empty; userQueryType={}", result.queryType() != null ? result.queryType() : "(none)");
        }
        String refinedQuery = (usedNoPreferenceRecovery && searchQuery != null) ? searchQuery : result.refinedQuery();
        boolean isSearchingFallback = text.startsWith("Searching for:");
        boolean useConvoCommerceOnly = "convo_commerce".equals(context.get("orchestrationMode"));
        boolean hasRefinedQuery = refinedQuery != null && !refinedQuery.isEmpty();

        List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers = result.suggestedAnswers() != null ? result.suggestedAnswers() : List.of();
        String responseSource = result.source() != null ? result.source() : "agent";

        // When SIMPLE_PRODUCT_SEARCH, no products, no follow-up: show previous agent response with suggested answers minus the one just tried (if we have that context)
        boolean simpleProductSearchNoProducts = "SIMPLE_PRODUCT_SEARCH".equals(result.queryType())
                && products.isEmpty()
                && (text == null || text.isEmpty() || text.startsWith("Searching for:"));
        boolean usedPreviousAgentFallback = false;
        if (simpleProductSearchNoProducts) {
            String prevText = (String) context.get("previousAssistantText");
            if (prevText != null && !prevText.isBlank()) {
                text = prevText;
                @SuppressWarnings("unchecked")
                var prevList = (List<Map<String, String>>) context.get("previousSuggestedAnswers");
                if (prevList != null && !prevList.isEmpty()) {
                    suggestedAnswers = prevList.stream()
                            .filter(m -> !originalUserInput.trim().equals(m.getOrDefault("value", "").trim()))
                            .map(m -> new ConversationalCommerceClient.SuggestedAnswer(
                                    m.getOrDefault("displayText", m.getOrDefault("value", "")),
                                    m.getOrDefault("value", "")))
                            .toList();
                } else if (!suggestedAnswers.isEmpty()) {
                    suggestedAnswers = suggestedAnswers.stream()
                            .filter(sa -> !originalUserInput.trim().equals(sa.value() != null ? sa.value().trim() : ""))
                            .toList();
                }
                responseSource = "app";
                usedPreviousAgentFallback = true;
            }
        }
        if (useConvoCommerceOnly) {
            // Approach A: Pass through agent response as-is; no app overrides
            if (!products.isEmpty() && products.size() > productCountThreshold && !usedNoPreferenceRecovery) {
                productsToReturn = List.of();
            } else if (!products.isEmpty() && isSearchingFallback) {
                // Replace "Searching for: X" placeholder with a proper count when we have products
                int n = products.size();
                text = n == 1
                        ? "I found 1 product matching your request."
                        : "I found " + n + " products matching your request.";
                responseSource = "app";
            } else if (products.isEmpty() && hasRefinedQuery && !usedPreviousAgentFallback) {
                // Always show "No products found" when search returns empty. Include agent context if meaningful (not placeholder).
                // Skip if we already handled via previous-agent fallback above.
                boolean hasMeaningfulAgentText = text != null && !text.isEmpty() && !text.startsWith("Searching for:");
                text = (hasMeaningfulAgentText ? text + "\n\n" : "") + "No products found.";
                responseSource = "app";
            }
        }
        if (!useConvoCommerceOnly) {
            // Approach B: Use our clarifying logic when many products
            if (!products.isEmpty() && products.size() > productCountThreshold && !usedNoPreferenceRecovery) {
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
            } else if (products.isEmpty() && hasRefinedQuery && !usedPreviousAgentFallback) {
                // Always show "No products found" when search returns empty. Include agent context if meaningful (not placeholder).
                boolean hasMeaningfulAgentText = text != null && !text.isEmpty() && !text.startsWith("Searching for:");
                text = (hasMeaningfulAgentText ? text + "\n\n" : "") + "No products found.";
                responseSource = "app";
            }
        }

        if (suggestedAnswers.isEmpty() && text != null && text.contains("?")) {
            suggestedAnswers = List.of(new ConversationalCommerceClient.SuggestedAnswer("Any", "ANY"));
        }
        return AgentResponse.builder()
                .text(text)
                .conversationId(result.conversationId())
                .refinedQuery(refinedQuery)
                .products(productsToReturn)
                .queryType(result.queryType())
                .source(responseSource)
                .rawResponse(result.rawResponse())
                .suggestedAnswers(suggestedAnswers)
                .build();
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    private String getVisitorId(Map<String, Object> context) {
        return ContextUtils.getVisitorId(context, config.defaultVisitorId());
    }

    /** Expand short codes (e.g. F, C) to canonical values before sending to GCP to avoid RETAIL_IRRELEVANT. */
    private String expandShortAttributeValue(String query, Map<String, Object> context) {
        if (query == null || query.isBlank()) return query;
        String trimmed = query.trim();

        // 1. Try previousSuggestedAnswers: if user input matches displayText, use value (handles display text from click)
        @SuppressWarnings("unchecked")
        var prevList = (List<Map<String, String>>) context.get("previousSuggestedAnswers");
        String resolved = trimmed;
        if (prevList != null) {
            for (var m : prevList) {
                String displayText = m.getOrDefault("displayText", "").trim();
                String value = m.getOrDefault("value", "").trim();
                if (!displayText.isEmpty() && trimmed.equalsIgnoreCase(displayText) && !value.equals(trimmed)) {
                    log.debug("Expanding user input \"{}\" to value \"{}\" from previous suggested answer", trimmed, value);
                    resolved = value;
                    break;
                }
            }
        }

        // 2. Run through attribute-value-expansion (e.g. C->REFRIGERATED, brands with spaces) - chain so short codes from step 1 get canonical
        var expansion = config.getAttributeValueExpansion();
        if (expansion != null) {
            for (var attrEntry : expansion.entrySet()) {
                if (attrEntry.getValue() == null) continue;
                String canonical = findExpansionMatch(attrEntry.getValue(), resolved);
                if (canonical != null && !canonical.equals(resolved)) {
                    log.debug("Expanding \"{}\" to \"{}\" for attribute {}", resolved, canonical, attrEntry.getKey());
                    return canonical;
                }
            }
        }

        return resolved;
    }

    private static String findExpansionMatch(java.util.Map<String, String> mapping, String input) {
        if (input == null || mapping == null) return null;
        if (mapping.containsKey(input)) return mapping.get(input);
        for (var e : mapping.entrySet()) {
            if (e.getKey().equalsIgnoreCase(input)) return e.getValue();
        }
        return null;
    }

    private static final java.util.Set<String> NON_BRAND_VALUES = java.util.Set.of(
            "FROZEN", "REFRIGERATED", "AMBIENT", "DRY_STORAGE", "COLD", "F", "C", "ANY"
    );

    private static final java.util.Set<String> NO_PREFERENCE_PHRASES = java.util.Set.of(
            "ANY", "NO", "NONE", "NOPREFERENCE", "DON'T CARE", "DONT CARE", "DOESN'T MATTER",
            "DOESNT MATTER", "WHATEVER", "I DON'T CARE", "IDC"
    );

    private static boolean isNoPreference(String input) {
        if (input == null || input.isBlank()) return false;
        String n = input.trim().toUpperCase().replaceAll("\\s+", " ");
        if (NO_PREFERENCE_PHRASES.contains(n)) return true;
        if (n.replace("'", "").replace(" ", "").equals("NOPREFERENCE")) return true;
        return n.equals("NO PREFERENCE") || n.startsWith("NO PREFERENCE");
    }

    /** Add brand filter only when user selected a suggested answer that looks like a brand (not storage type, not a search term like "shrimp"). */
    private String buildBrandFilterWhenApplicable(String canonicalValue, List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers, Map<String, Object> context) {
        if (canonicalValue == null || canonicalValue.isBlank()) return null;
        String trimmed = canonicalValue.trim();
        if (trimmed.length() < 2 || trimmed.length() > 50) return null;
        if (NON_BRAND_VALUES.contains(trimmed.toUpperCase())) return null;
        boolean fromSelection = false;
        @SuppressWarnings("unchecked")
        var prevList = (List<Map<String, String>>) context.get("previousSuggestedAnswers");
        if (prevList != null) {
            for (var m : prevList) {
                if (trimmed.equals(m.getOrDefault("value", "").trim()) || trimmed.equalsIgnoreCase(m.getOrDefault("displayText", "").trim())) {
                    fromSelection = true;
                    break;
                }
            }
        }
        boolean matchesCurrentSuggested = suggestedAnswers != null && suggestedAnswers.stream().anyMatch(sa -> trimmed.equals(sa.value()));
        if ((fromSelection || matchesCurrentSuggested)) {
            String escaped = trimmed.replace("\\", "\\\\").replace("\"", "\\\"");
            return "brands: ANY(\"" + escaped + "\")";
        }
        return null;
    }
}
