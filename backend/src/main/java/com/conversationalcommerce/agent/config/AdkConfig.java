package com.conversationalcommerce.agent.config;

import com.conversationalcommerce.agent.agent.ConversationalCommerceClient;
import com.conversationalcommerce.agent.tool.ConversationalCommerceTool;
import com.conversationalcommerce.agent.tool.LoyaltyRecommendationTool;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.FunctionTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class AdkConfig {

    private final ConversationalCommerceClient conversationalCommerceClient;
    private final ConversationalCommerceConfig config;

    public AdkConfig(ConversationalCommerceClient conversationalCommerceClient,
                    ConversationalCommerceConfig config) {
        this.conversationalCommerceClient = conversationalCommerceClient;
        this.config = config;
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

    @Bean
    public LlmAgent adkOrchestratorAgent() {
        FunctionTool searchProductsTool = FunctionTool.create(
                ConversationalCommerceTool.class, "searchProducts");
        FunctionTool loyaltyTool = FunctionTool.create(
                LoyaltyRecommendationTool.class, "getLoyaltyRecommendations");

        return LlmAgent.builder()
                .model("gemini-2.0-flash")
                .name("adk_orchestrator")
                .instruction("""
                    You are a shopping assistant orchestrator. You help users with product searches and shopping questions.
                    When the user wants to find, search, or browse products, use the searchProducts tool with their query.
                    When the user asks about rewards, loyalty, or recommendations, use getLoyaltyRecommendations.
                    When the user has general questions (store hours, policies, etc.), answer directly from your knowledge.
                    Always be helpful and concise. If search results are returned, summarize them for the user.
                    """)
                .description("Orchestrates product search, loyalty info, and general shopping assistance")
                .tools(searchProductsTool, loyaltyTool)
                .build();
    }
}
