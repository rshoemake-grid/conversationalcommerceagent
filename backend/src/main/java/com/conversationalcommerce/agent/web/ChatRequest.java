package com.conversationalcommerce.agent.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Chat request payload")
public record ChatRequest(
        @NotNull @Schema(description = "Orchestration mode", example = "adk_orchestrator") OrchestrationMode mode,
        @NotBlank @Schema(description = "User message", example = "What running shoes do you have?") String message,
        @Schema(description = "Conversation ID for multi-turn") String conversationId,
        @Schema(description = "Session/visitor ID") String sessionId
) {
    public enum OrchestrationMode {
        convo_commerce,   // Approach A: Convo Commerce as orchestrator
        adk_orchestrator  // Approach B: ADK agent as orchestrator
    }
}
