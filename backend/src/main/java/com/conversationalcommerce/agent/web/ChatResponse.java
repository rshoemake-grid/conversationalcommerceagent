package com.conversationalcommerce.agent.web;

import com.conversationalcommerce.agent.agent.AgentResponse;

import java.util.List;

public record ChatResponse(
        String text,
        String conversationId,
        String refinedQuery,
        List<ProductDto> products,
        String source
) {
    public static ChatResponse from(AgentResponse r) {
        if (r == null) {
            return new ChatResponse("", "", null, List.of(), "agent");
        }
        List<ProductDto> products = r.products() != null
                ? r.products().stream()
                .map(p -> new ProductDto(
                        nullToEmpty(p.id()),
                        nullToEmpty(p.title()),
                        nullToEmpty(p.description()),
                        nullToEmpty(p.price()),
                        p.imageUri()))
                .toList()
                : List.of();
        return new ChatResponse(
                r.text() != null ? r.text() : "",
                r.conversationId() != null ? r.conversationId() : "",
                r.refinedQuery(),
                products,
                r.source() != null ? r.source() : "agent"
        );
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    public record ProductDto(String id, String title, String description, String price, String imageUri) {}
}
