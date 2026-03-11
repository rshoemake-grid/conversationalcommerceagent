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

    private final ConvoCommerceOrchestrator convoCommerceOrchestrator;
    private final AdkOrchestrator adkOrchestrator;

    public OrchestratorService(ConvoCommerceOrchestrator convoCommerceOrchestrator, AdkOrchestrator adkOrchestrator) {
        this.convoCommerceOrchestrator = convoCommerceOrchestrator;
        this.adkOrchestrator = adkOrchestrator;
    }

    public AgentResponse process(ChatRequest.OrchestrationMode mode, String message, String conversationId, String sessionId) {
        var context = Map.<String, Object>of(
                "visitorId", sessionId,
                "sessionId", sessionId
        );

        return switch (mode) {
            case convo_commerce -> convoCommerceOrchestrator.process(message, conversationId, context);
            case adk_orchestrator -> adkOrchestrator.process(message, conversationId, context);
        };
    }
}
