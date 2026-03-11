package com.conversationalcommerce.agent.web;

import com.conversationalcommerce.agent.agent.AgentResponse;

import java.util.List;

public record ChatResponse(
        String text,
        String conversationId,
        String refinedQuery,
        List<ProductDto> products
) {
    public static ChatResponse from(AgentResponse r) {
        List<ProductDto> products = r.products() != null
                ? r.products().stream()
                .map(p -> new ProductDto(p.id(), p.title(), p.description(), p.price(), p.imageUri()))
                .toList()
                : List.of();
        return new ChatResponse(
                r.text(),
                r.conversationId(),
                r.refinedQuery(),
                products
        );
    }

    public record ProductDto(String id, String title, String description, String price, String imageUri) {}
}
