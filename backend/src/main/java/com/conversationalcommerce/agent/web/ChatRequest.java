package com.conversationalcommerce.agent.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatRequest(
        @NotNull OrchestrationMode mode,
        @NotBlank String message,
        String conversationId,
        String sessionId
) {
    public enum OrchestrationMode {
        convo_commerce,   // Approach A: Convo Commerce as orchestrator
        adk_orchestrator  // Approach B: ADK agent as orchestrator
    }
}
