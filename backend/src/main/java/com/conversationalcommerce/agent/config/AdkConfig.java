package com.conversationalcommerce.agent.config;

import com.conversationalcommerce.agent.agent.ConversationalCommerceClient;
import com.conversationalcommerce.agent.orchestration.AdkOrchestrator;
import com.conversationalcommerce.agent.tool.ConversationalCommerceTool;
import com.conversationalcommerce.agent.tool.LoyaltyRecommendationTool;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.Gemini;
import com.google.adk.tools.FunctionTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;

@Configuration
public class AdkConfig {

    private static final Logger log = LoggerFactory.getLogger(AdkConfig.class);

    @Value("${app.gemini.model:gemini-flash-latest}")
    private String model;

    @Value("${app.gemini.api-key:}")
    private String apiKeyFromConfig;

    private final ConversationalCommerceClient conversationalCommerceClient;
    private final ConversationalCommerceConfig config;
    private final Environment environment;
    private final GeminiClientFactory clientFactory;

    public AdkConfig(ConversationalCommerceClient conversationalCommerceClient,
                    ConversationalCommerceConfig config,
                    Environment environment,
                    GeminiClientFactory clientFactory) {
        this.conversationalCommerceClient = conversationalCommerceClient;
        this.config = config;
        this.environment = environment;
        this.clientFactory = clientFactory;
    }

    @PostConstruct
    void configureTool() {
        ConversationalCommerceTool.configure(
                conversationalCommerceClient,
                config.placement(),
                config.branch(),
                config.defaultVisitorId()
        );
    }

    @PostConstruct
    void warnIfApiKeyMissing() {
        String raw = getRawApiKey();
        if (raw == null || raw.isBlank()) {
            log.warn("GOOGLE_API_KEY is not set. Approach B (ADK orchestrator) will return placeholder responses. Set GOOGLE_API_KEY for full functionality.");
        }
    }

    private String getRawApiKey() {
        if (apiKeyFromConfig != null && !apiKeyFromConfig.isBlank()) {
            return apiKeyFromConfig;
        }
        return environment.getProperty("GOOGLE_API_KEY");
    }

    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText(@environment.getProperty('GOOGLE_API_KEY')) || T(org.springframework.util.StringUtils).hasText(@environment.getProperty('app.gemini.api-key'))")
    public LlmAgent adkOrchestratorAgent() {
        String rawKey = getRawApiKey();
        String sanitizedKey = ApiKeySanitizer.sanitize(rawKey);
        if (sanitizedKey == null || sanitizedKey.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_API_KEY or app.gemini.api-key is set but yielded no valid key after sanitization. " +
                    "Ensure the key has no newlines or trailing comments (e.g. '#export...').");
        }

        FunctionTool searchProductsTool = FunctionTool.create(
                ConversationalCommerceTool.class, "searchProducts");
        FunctionTool loyaltyTool = FunctionTool.create(
                LoyaltyRecommendationTool.class, "getLoyaltyRecommendations");

        var geminiModel = Gemini.builder()
                .modelName(model)
                .apiClient(clientFactory.buildClient(sanitizedKey))
                .build();

        return LlmAgent.builder()
                .model(geminiModel)
                .name("adk_orchestrator")
                .instruction("""
                    You are a shopping assistant orchestrator. You help users with product searches and shopping questions.

                    When the user's request is vague or broad (e.g. "show me products", "shoes", "browse", "what do you have"),
                    ask 1-2 clarifying questions BEFORE searching. For example:
                    - "What type of shoes? Running, casual, sandals, or formal?"
                    - "What style or color are you looking for?"
                    - "Do you have a price range in mind?"
                    Only call searchProducts when you have a sufficiently specific query.

                    When the user gives a specific query, use the searchProducts tool.
                    When the user asks about rewards, loyalty, or recommendations, use getLoyaltyRecommendations.
                    When the user has general questions (store hours, policies, etc.), answer directly from your knowledge.
                    If search results are returned, summarize them for the user.
                    """)
                .description("Orchestrates product search, loyalty info, and general shopping assistance")
                .tools(searchProductsTool, loyaltyTool)
                .build();
    }

    @Bean
    public AdkOrchestrator adkOrchestrator(
            @Autowired(required = false) @Qualifier("adkOrchestratorAgent") LlmAgent adkOrchestratorAgent) {
        return new AdkOrchestrator(adkOrchestratorAgent);
    }
}
