package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.agent.ConversationalAgent;
import com.conversationalcommerce.agent.agent.ConversationalCommerceAdapter;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Approach A: Conversational Commerce API as primary orchestrator.
 * Delegates to Convo Commerce first, then optionally to ADK specialists based on query type.
 */
@Service
public class ConvoCommerceOrchestrator {

    private final ConversationalAgent conversationalCommerceAgent;
    private final GeneralQuestionHandler generalQuestionSpecialist;

    public ConvoCommerceOrchestrator(
            ConversationalCommerceAdapter conversationalCommerceAgent,
            GeneralQuestionHandler generalQuestionSpecialist) {
        this.conversationalCommerceAgent = conversationalCommerceAgent;
        this.generalQuestionSpecialist = generalQuestionSpecialist;
    }

    public AgentResponse process(String message, String conversationId, Map<String, Object> context) {
        AgentResponse convoResponse = conversationalCommerceAgent.sendMessage(
                conversationId != null ? conversationId : "",
                message,
                context
        );

        // If Convo Commerce indicates a general question, enhance with specialist
        if (shouldInvokeSpecialist(convoResponse)) {
            AgentResponse specialistResponse = generalQuestionSpecialist.handle(message, context);
            return mergeResponses(convoResponse, specialistResponse);
        }

        return convoResponse;
    }

    private boolean shouldInvokeSpecialist(AgentResponse response) {
        if (response.queryType() == null) return false;
        return "GENERAL_QUESTION".equals(response.queryType())
                || "UNSPECIFIED".equals(response.queryType());
    }

    private AgentResponse mergeResponses(AgentResponse convo, AgentResponse specialist) {
        String mergedText = convo.text();
        if (specialist.text() != null && !specialist.text().isEmpty()) {
            mergedText = mergedText + "\n\n" + specialist.text();
        }
        return AgentResponse.builder()
                .text(mergedText)
                .conversationId(convo.conversationId())
                .refinedQuery(convo.refinedQuery())
                .products(convo.products())
                .queryType(convo.queryType())
                .build();
    }
}
