package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.agent.ConversationalCommerceAdapter;
import com.conversationalcommerce.agent.agent.ConversationalCommerceClient;
import com.conversationalcommerce.agent.agent.RetailSearchClient;
import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConvoCommerceOrchestratorTest {

    private ConvoCommerceOrchestrator orchestrator;
    private StubConversationalCommerceClient stubClient;

    @BeforeEach
    void setUp() {
        var config = new ConversationalCommerceConfig();
        config.setPlacement("projects/p/placements/default");
        config.setBranch("projects/p/branches/default");

        stubClient = new StubConversationalCommerceClient();
        var adapter = new ConversationalCommerceAdapter(stubClient, new StubRetailSearchClient(), config);
        var specialist = new StubGeneralQuestionSpecialist();

        orchestrator = new ConvoCommerceOrchestrator(adapter, specialist);
    }

    @Test
    void process_returnsConvoResponseForProductSearch() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are shoes", "conv-1", "shoes", "SIMPLE_PRODUCT_SEARCH"));

        AgentResponse r = orchestrator.process("shoes", "", Map.of());

        assertThat(r.text()).contains("Here are shoes");
    }

    @Test
    void process_invokesSpecialistForGeneralQuestion() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "General question", "conv-1", null, "GENERAL_QUESTION"));

        AgentResponse r = orchestrator.process("store hours?", "", Map.of());

        assertThat(r.text()).contains("General question");
        assertThat(r.text()).contains("Specialist");
    }

    private static class StubConversationalCommerceClient implements ConversationalCommerceClient {
        ConversationalCommerceResult nextResult;

        void setNextResult(ConversationalCommerceResult r) {
            this.nextResult = r;
        }

        @Override
        public ConversationalCommerceResult search(ConversationalCommerceRequest request) {
            return nextResult != null ? nextResult : new ConversationalCommerceResult("", "", null, null);
        }
    }

    private static class StubRetailSearchClient implements RetailSearchClient {
        @Override
        public List<AgentResponse.ProductResult> search(String placement, String branch, String query, String visitorId) {
            return List.of();
        }
    }

    private static class StubGeneralQuestionSpecialist implements GeneralQuestionHandler {
        @Override
        public AgentResponse handle(String message, Map<String, Object> context) {
            return AgentResponse.builder().text("Specialist: store hours 9-9").build();
        }
    }
}
