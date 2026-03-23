package com.conversationalcommerce.agent.web;

import com.conversationalcommerce.agent.agent.AgentResponse;

import java.util.List;
import java.util.Map;

public record ChatResponse(
        String text,
        String conversationId,
        String refinedQuery,
        List<ProductDto> products,
        String source,
        String queryType,
        String rawResponse,
        List<SuggestedAnswerDto> suggestedAnswers
) {
    public static ChatResponse from(AgentResponse r) {
        if (r == null) {
            return new ChatResponse("", "", null, List.of(), "agent", null, null, List.<SuggestedAnswerDto>of());
        }
        List<ProductDto> products = r.products() != null
                ? r.products().stream()
                .map(p -> new ProductDto(
                        nullToEmpty(p.id()),
                        nullToEmpty(p.title()),
                        nullToEmpty(p.description()),
                        nullToEmpty(p.price()),
                        p.imageUri(),
                        p.gtin(),
                        p.productId(),
                        p.categories(),
                        p.brands(),
                        p.uri(),
                        p.availability(),
                        p.sizes(),
                        p.materials(),
                        p.attributes()))
                .toList()
                : List.of();
        return new ChatResponse(
                r.text() != null ? r.text() : "",
                r.conversationId() != null ? r.conversationId() : "",
                r.refinedQuery(),
                products,
                r.source() != null ? r.source() : "agent",
                r.queryType(),
                r.rawResponse(),
                r.suggestedAnswers() != null
                        ? r.suggestedAnswers().stream()
                                .map(sa -> new SuggestedAnswerDto(sa.displayText(), sa.value()))
                                .toList()
                        : List.of()
        );
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    public record ProductDto(
            String id,
            String title,
            String description,
            String price,
            String imageUri,
            String gtin,
            String productId,
            List<String> categories,
            List<String> brands,
            String uri,
            String availability,
            List<String> sizes,
            List<String> materials,
            Map<String, Object> attributes
    ) {}

    /** Display text for UI; value is sent to API when user selects */
    public record SuggestedAnswerDto(String displayText, String value) {}
}
