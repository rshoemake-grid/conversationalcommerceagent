package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.web.ChatRequest;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Routes chat requests to the appropriate orchestrator based on mode.
 */
@Service
public class OrchestratorService {

    private final ChatOrchestrator convoCommerceOrchestrator;
    private final ChatOrchestrator adkOrchestrator;

    public OrchestratorService(ConvoCommerceOrchestrator convoCommerceOrchestrator, AdkOrchestrator adkOrchestrator) {
        this.convoCommerceOrchestrator = convoCommerceOrchestrator;
        this.adkOrchestrator = adkOrchestrator;
    }

    public AgentResponse process(ChatRequest.OrchestrationMode mode, String message, String conversationId, String sessionId, String imageBase64) {
        var context = new java.util.HashMap<String, Object>(Map.of("visitorId", sessionId, "sessionId", sessionId));
        context.put("orchestrationMode", mode.name());
        if (imageBase64 != null && !imageBase64.isBlank()) {
            context.put("imageBase64", imageBase64);
        }

        return switch (mode) {
            case convo_commerce -> convoCommerceOrchestrator.process(message, conversationId, context);
            case adk_orchestrator -> adkOrchestrator.process(message, conversationId, context);
        };
    }
}
