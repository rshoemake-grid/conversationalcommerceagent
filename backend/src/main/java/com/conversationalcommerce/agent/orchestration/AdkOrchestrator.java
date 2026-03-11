package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Approach B: ADK agent as orchestrator.
 * ADK agent has tools including Conversational Commerce, delegates as needed.
 */
@Service
public class AdkOrchestrator {

    private static final String APP_NAME = "conversational_commerce";

    private final LlmAgent adkOrchestratorAgent;
    private final InMemoryRunner runner;

    public AdkOrchestrator(LlmAgent adkOrchestratorAgent) {
        this.adkOrchestratorAgent = adkOrchestratorAgent;
        this.runner = new InMemoryRunner(adkOrchestratorAgent, APP_NAME);
    }

    public AgentResponse process(String message, String conversationId, Map<String, Object> context) {
        String visitorId = context != null && context.containsKey("visitorId")
                ? String.valueOf(context.get("visitorId"))
                : "user-" + System.currentTimeMillis();

        String sessionId = resolveOrCreateSession(conversationId, visitorId);
        Content userMessage = Content.fromParts(Part.fromText(message));

        List<String> responseParts = new ArrayList<>();
        Flowable<Event> eventStream = runner.runAsync(visitorId, sessionId, userMessage);

        eventStream.blockingForEach(event -> {
            if (event.finalResponse()) {
                String content = event.stringifyContent();
                if (content != null && !content.isEmpty()) {
                    responseParts.add(content);
                }
            }
        });

        String text = String.join("\n", responseParts);
        if (text.isEmpty()) {
            text = "I'm here to help. Could you tell me more about what you're looking for?";
        }

        return AgentResponse.builder()
                .text(text)
                .conversationId(sessionId)
                .build();
    }

    /**
     * Resolves or creates an ADK session. InMemoryRunner requires sessions to exist before runAsync.
     * If conversationId is provided and the session exists, reuse it. Otherwise create a new session.
     */
    private String resolveOrCreateSession(String conversationId, String visitorId) {
        if (conversationId != null && !conversationId.isEmpty()) {
            Session existing = runner.sessionService()
                    .getSession(APP_NAME, visitorId, conversationId, Optional.empty())
                    .blockingGet();
            if (existing != null) {
                return existing.id();
            }
            // Session doesn't exist, create one with the requested ID for multi-turn
            Session session = runner.sessionService()
                    .createSession(APP_NAME, visitorId, null, conversationId)
                    .blockingGet();
            return session.id();
        }
        Session session = runner.sessionService().createSession(APP_NAME, visitorId, null, null).blockingGet();
        return session.id();
    }
}
