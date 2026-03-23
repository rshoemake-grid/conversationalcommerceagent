package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BrandDisplayResolver.
 */
class BrandDisplayResolverTest {

    private ConversationalCommerceConfig config;
    private BrandDisplayResolver resolver;

    @BeforeEach
    void setUp() {
        config = new ConversationalCommerceConfig();
        config.setPlacement("projects/p/locations/global/catalogs/default_catalog/placements/default_search");
        config.setBranch("projects/p/locations/global/catalogs/default_catalog/branches/default_branch");
    }

    @Test
    void resolve_returnsConfigMappingWhenPresent() {
        config.setAttributeDisplayMapping(Map.of(
                "brands", Map.of("NIKE", "Nike", "ADIDAS", "Adidas")
        ));
        resolver = new BrandDisplayResolver(config, null);

        assertThat(resolver.resolveDisplayText("attributes.brands", "NIKE")).isEqualTo("Nike");
        assertThat(resolver.resolveDisplayText("attributes.brandId", "ADIDAS")).isEqualTo("Adidas");
    }

    @Test
    void resolve_returnsProductBrandFromApiWhenNoConfigAndSearchClientPresent() {
        resolver = new BrandDisplayResolver(config, new StubSearchClient());

        // Stub returns product with brands=["Nike"] for filter on NIKE
        String result = resolver.resolveDisplayText("attributes.brands", "NIKE");
        assertThat(result).isEqualTo("Nike");
    }

    @Test
    void resolve_fallsBackToTitleCaseWhenNoConfigAndNoSearchClient() {
        resolver = new BrandDisplayResolver(config, null);

        assertThat(resolver.resolveDisplayText("attributes.brands", "NIKE")).isEqualTo("Nike");
        assertThat(resolver.resolveDisplayText("attributes.brandId", "ADIDAS")).isEqualTo("Adidas");
    }

    @Test
    void resolve_fallsBackToTitleCaseWhenSearchReturnsEmpty() {
        resolver = new BrandDisplayResolver(config, new StubSearchClient()); // stub returns empty for unknown

        String result = resolver.resolveDisplayText("attributes.brands", "UNKNOWN_BRAND");
        assertThat(result).isEqualTo("Unknown_brand");
    }

    @Test
    void resolve_returnsValueAsIsForNonBrandAttribute() {
        resolver = new BrandDisplayResolver(config, null);

        assertThat(resolver.resolveDisplayText("attributes.type", "Balloons")).isEqualTo("Balloons");
        assertThat(resolver.resolveDisplayText("attributes.color", "BLUE")).isEqualTo("BLUE");
    }

    @Test
    void resolve_configTakesPrecedenceOverApi() {
        config.setAttributeDisplayMapping(Map.of("brands", Map.of("NIKE", "Custom Nike")));
        resolver = new BrandDisplayResolver(config, new StubSearchClient());

        assertThat(resolver.resolveDisplayText("attributes.brands", "NIKE")).isEqualTo("Custom Nike");
    }

    private static class StubSearchClient implements RetailSearchClient {
        @Override
        public List<AgentResponse.ProductResult> search(String placement, String branch, String query, String visitorId, String filter) {
            if (filter != null && filter.contains("NIKE")) {
                return List.of(new AgentResponse.ProductResult(
                        null, "Test", null, null, null, null, null,
                        null, List.of("Nike"), null, null, null, null, null));
            }
            return List.of();
        }
    }
}
