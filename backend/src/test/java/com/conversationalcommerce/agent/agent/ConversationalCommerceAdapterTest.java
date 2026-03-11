package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationalCommerceAdapterTest {

    private ConversationalCommerceAdapter adapter;
    private StubConversationalCommerceClient stubClient;
    private StubRetailSearchClient stubSearchClient;

    @BeforeEach
    void setUp() {
        var config = new ConversationalCommerceConfig();
        config.setPlacement("projects/p/locations/global/catalogs/default_catalog/placements/default_search");
        config.setBranch("projects/p/locations/global/catalogs/default_catalog/branches/default_branch");
        config.setDefaultVisitorId("test-visitor");

        stubClient = new StubConversationalCommerceClient();
        stubSearchClient = new StubRetailSearchClient();
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, config);
    }

    @Test
    void getAgentId_returnsConversationalCommerceId() {
        assertThat(adapter.getAgentId()).isEqualTo(ConversationalCommerceAdapter.AGENT_ID);
    }

    @Test
    void sendMessage_returnsResponseFromClient() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are some options",
                "conv-123",
                "refined query",
                "SIMPLE_PRODUCT_SEARCH"
        ));

        AgentResponse response = adapter.sendMessage("", "show me shoes", Map.of());

        assertThat(response.text()).isEqualTo("Here are some options");
        assertThat(response.conversationId()).isEqualTo("conv-123");
        assertThat(response.refinedQuery()).isEqualTo("refined query");
        assertThat(response.queryType()).isEqualTo("SIMPLE_PRODUCT_SEARCH");
    }

    @Test
    void sendMessage_usesVisitorIdFromContext() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Response", "conv-1", null, null
        ));

        adapter.sendMessage("", "hi", Map.of("visitorId", "custom-visitor"));

        assertThat(stubClient.lastRequest.visitorId()).isEqualTo("custom-visitor");
    }

    @Test
    void sendMessage_fetchesProductsWhenRefinedQueryPresent() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are shoes", "conv-1", "running shoes", "SIMPLE_PRODUCT_SEARCH"
        ));
        stubSearchClient.setProducts(List.of(
                new AgentResponse.ProductResult("p1", "Nike Run", "Running shoes", "$99", null)
        ));

        AgentResponse response = adapter.sendMessage("", "running shoes", Map.of());

        assertThat(response.products()).hasSize(1);
        assertThat(response.products().get(0).title()).isEqualTo("Nike Run");
    }

    private static class StubConversationalCommerceClient implements ConversationalCommerceClient {
        ConversationalCommerceRequest lastRequest;
        ConversationalCommerceResult nextResult;

        void setNextResult(ConversationalCommerceResult result) {
            this.nextResult = result;
        }

        @Override
        public ConversationalCommerceResult search(ConversationalCommerceRequest request) {
            this.lastRequest = request;
            return nextResult != null ? nextResult : new ConversationalCommerceResult("", "", null, null);
        }
    }

    private static class StubRetailSearchClient implements RetailSearchClient {
        List<AgentResponse.ProductResult> products = List.of();

        void setProducts(List<AgentResponse.ProductResult> products) {
            this.products = products;
        }

        @Override
        public List<AgentResponse.ProductResult> search(String placement, String branch, String query, String visitorId) {
            return products;
        }
    }
}
