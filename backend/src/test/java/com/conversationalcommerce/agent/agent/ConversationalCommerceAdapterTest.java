package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.conversationalcommerce.agent.gemini.ClarifyingQuestionGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationalCommerceAdapterTest {

    private ConversationalCommerceAdapter adapter;
    private StubConversationalCommerceClient stubClient;
    private StubRetailSearchClient stubSearchClient;
    private ConversationalCommerceConfig config;

    @BeforeEach
    void setUp() {
        config = new ConversationalCommerceConfig();
        config.setPlacement("projects/p/locations/global/catalogs/default_catalog/placements/default_search");
        config.setBranch("projects/p/locations/global/catalogs/default_catalog/branches/default_branch");
        config.setDefaultVisitorId("test-visitor");

        stubClient = new StubConversationalCommerceClient();
        stubSearchClient = new StubRetailSearchClient();
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, config, Optional.empty());
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
                "SIMPLE_PRODUCT_SEARCH",
                "agent"
        ));
        stubSearchClient.setProducts(List.of(
                new AgentResponse.ProductResult("p1", "Nike Run", "Running shoes", "$99", null)
        ));

        AgentResponse response = adapter.sendMessage("", "show me shoes", Map.of("orchestrationMode", "convo_commerce"));

        assertThat(response.text()).isEqualTo("Here are some options");
        assertThat(response.conversationId()).isEqualTo("conv-123");
        assertThat(response.refinedQuery()).isEqualTo("refined query");
        assertThat(response.queryType()).isEqualTo("SIMPLE_PRODUCT_SEARCH");
    }

    @Test
    void sendMessage_usesVisitorIdFromContext() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Response", "conv-1", null, null, "agent"
        ));

        adapter.sendMessage("", "hi", Map.of("visitorId", "custom-visitor"));

        assertThat(stubClient.lastRequest.visitorId()).isEqualTo("custom-visitor");
    }

    @Test
    void sendMessage_fetchesProductsWhenRefinedQueryPresent() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are shoes", "conv-1", "running shoes", "SIMPLE_PRODUCT_SEARCH", "agent"
        ));
        stubSearchClient.setProducts(List.of(
                new AgentResponse.ProductResult("p1", "Nike Run", "Running shoes", "$99", null)
        ));

        AgentResponse response = adapter.sendMessage("", "running shoes", Map.of());

        assertThat(response.products()).hasSize(1);
        assertThat(response.products().get(0).title()).isEqualTo("Nike Run");
    }

    @Test
    void sendMessage_returnsHelpfulMessageWhenNoProductsAndSearchingFallback() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Searching for: fresh jumbo shrimp options", "conv-1", "fresh jumbo shrimp options", "SIMPLE_PRODUCT_SEARCH", "app"
        ));
        stubSearchClient.setProducts(List.of());

        AgentResponse response = adapter.sendMessage("", "what options do I have?", Map.of());

        assertThat(response.products()).isEmpty();
        assertThat(response.text()).isEqualTo("No products found.");
    }

    @Test
    void sendMessage_usesGeneratedClarifyingQuestionWhenGeneratorAvailable() {
        var stubGenerator = new ClarifyingQuestionGenerator(null, null) {
            @Override
            public String generate(String refinedQuery, int productCount) {
                return "What type of shrimp are you looking for? Raw, cooked, or peeled?";
            }
        };
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, config, Optional.of(stubGenerator));

        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are options", "conv-1", "Shrimp", "SIMPLE_PRODUCT_SEARCH", "agent"
        ));
        var manyProducts = List.of(
                new AgentResponse.ProductResult("p1", "Shrimp 1", "Desc", "$10", null),
                new AgentResponse.ProductResult("p2", "Shrimp 2", "Desc", "$12", null),
                new AgentResponse.ProductResult("p3", "Shrimp 3", "Desc", "$14", null),
                new AgentResponse.ProductResult("p4", "Shrimp 4", "Desc", "$16", null),
                new AgentResponse.ProductResult("p5", "Shrimp 5", "Desc", "$18", null),
                new AgentResponse.ProductResult("p6", "Shrimp 6", "Desc", "$20", null),
                new AgentResponse.ProductResult("p7", "Shrimp 7", "Desc", "$22", null),
                new AgentResponse.ProductResult("p8", "Shrimp 8", "Desc", "$24", null),
                new AgentResponse.ProductResult("p9", "Shrimp 9", "Desc", "$26", null)
        );
        stubSearchClient.setProducts(manyProducts);

        AgentResponse response = adapter.sendMessage("", "what other options?", Map.of());

        assertThat(response.products()).isEmpty();
        assertThat(response.text()).isEqualTo("What type of shrimp are you looking for? Raw, cooked, or peeled?");
    }

    @Test
    void sendMessage_asksClarifyingQuestionWhenManyProductsReturned() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are options", "conv-1", "shoes", "SIMPLE_PRODUCT_SEARCH", "agent"
        ));
        var manyProducts = List.of(
                new AgentResponse.ProductResult("p1", "Shoe 1", "Desc", "$50", null),
                new AgentResponse.ProductResult("p2", "Shoe 2", "Desc", "$60", null),
                new AgentResponse.ProductResult("p3", "Shoe 3", "Desc", "$70", null),
                new AgentResponse.ProductResult("p4", "Shoe 4", "Desc", "$80", null),
                new AgentResponse.ProductResult("p5", "Shoe 5", "Desc", "$90", null),
                new AgentResponse.ProductResult("p6", "Shoe 6", "Desc", "$100", null),
                new AgentResponse.ProductResult("p7", "Shoe 7", "Desc", "$110", null),
                new AgentResponse.ProductResult("p8", "Shoe 8", "Desc", "$120", null),
                new AgentResponse.ProductResult("p9", "Shoe 9", "Desc", "$130", null)
        );
        stubSearchClient.setProducts(manyProducts);

        AgentResponse response = adapter.sendMessage("", "shoes", Map.of());

        assertThat(response.products()).isEmpty();
        assertThat(response.text()).contains("narrow it down");
    }

    @Test
    void sendMessage_convoCommerceMode_usesApiResponseWhenManyProducts() {
        var stubGenerator = new ClarifyingQuestionGenerator(null, null) {
            @Override
            public String generate(String refinedQuery, int productCount) {
                return "Gemini-generated question";
            }
        };
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, config, Optional.of(stubGenerator));

        String apiResponse = "I found many shoes. Could you tell me more about what you're looking for?";
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                apiResponse, "conv-1", "shoes", "SIMPLE_PRODUCT_SEARCH", "agent"
        ));
        var manyProducts = List.of(
                new AgentResponse.ProductResult("p1", "Shoe 1", "Desc", "$50", null),
                new AgentResponse.ProductResult("p2", "Shoe 2", "Desc", "$60", null),
                new AgentResponse.ProductResult("p3", "Shoe 3", "Desc", "$70", null),
                new AgentResponse.ProductResult("p4", "Shoe 4", "Desc", "$80", null),
                new AgentResponse.ProductResult("p5", "Shoe 5", "Desc", "$90", null),
                new AgentResponse.ProductResult("p6", "Shoe 6", "Desc", "$100", null),
                new AgentResponse.ProductResult("p7", "Shoe 7", "Desc", "$110", null),
                new AgentResponse.ProductResult("p8", "Shoe 8", "Desc", "$120", null),
                new AgentResponse.ProductResult("p9", "Shoe 9", "Desc", "$130", null)
        );
        stubSearchClient.setProducts(manyProducts);

        var context = Map.<String, Object>of("orchestrationMode", "convo_commerce");
        AgentResponse response = adapter.sendMessage("", "shoes", context);

        assertThat(response.products()).isEmpty();
        assertThat(response.text()).isEqualTo(apiResponse);
    }

    @Test
    void sendMessage_convoCommerceMode_passesThroughAgentResponse() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Searching for: shrimp", "conv-1", "shrimp", "SIMPLE_PRODUCT_SEARCH", "app"
        ));
        var manyProducts = List.of(
                new AgentResponse.ProductResult("p1", "Shrimp 1", "Desc", "$10", null),
                new AgentResponse.ProductResult("p2", "Shrimp 2", "Desc", "$12", null),
                new AgentResponse.ProductResult("p3", "Shrimp 3", "Desc", "$14", null),
                new AgentResponse.ProductResult("p4", "Shrimp 4", "Desc", "$16", null),
                new AgentResponse.ProductResult("p5", "Shrimp 5", "Desc", "$18", null),
                new AgentResponse.ProductResult("p6", "Shrimp 6", "Desc", "$20", null),
                new AgentResponse.ProductResult("p7", "Shrimp 7", "Desc", "$22", null),
                new AgentResponse.ProductResult("p8", "Shrimp 8", "Desc", "$24", null),
                new AgentResponse.ProductResult("p9", "Shrimp 9", "Desc", "$26", null)
        );
        stubSearchClient.setProducts(manyProducts);

        var context = Map.<String, Object>of("orchestrationMode", "convo_commerce");
        AgentResponse response = adapter.sendMessage("", "shrimp", context);

        assertThat(response.products()).isEmpty();
        assertThat(response.text()).isEqualTo("Searching for: shrimp");
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
            return nextResult != null ? nextResult : new ConversationalCommerceResult("", "", null, null, "agent");
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
