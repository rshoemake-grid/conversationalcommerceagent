package com.conversationalcommerce.agent.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Chat request payload")
public record ChatRequest(
        @NotNull @Schema(description = "Orchestration mode", example = "adk_orchestrator") OrchestrationMode mode,
        @Schema(description = "User message (required if no image)", example = "What running shoes do you have?") String message,
        @Schema(description = "Conversation ID for multi-turn") String conversationId,
        @Schema(description = "Session/visitor ID") String sessionId,
        @Schema(description = "Optional image as base64 (multimodal search)") String imageBase64
) {
    public ChatRequest {
        message = message != null ? message : "";
        imageBase64 = imageBase64 != null && imageBase64.isBlank() ? null : imageBase64;
    }

    @AssertTrue(message = "Either message or imageBase64 must be provided")
    public boolean isValidInput() {
        return (message != null && !message.isBlank()) || (imageBase64 != null && !imageBase64.isBlank());
    }

    public enum OrchestrationMode {
        convo_commerce,   // Approach A: Convo Commerce as orchestrator
        adk_orchestrator  // Approach B: ADK agent as orchestrator
    }
}
