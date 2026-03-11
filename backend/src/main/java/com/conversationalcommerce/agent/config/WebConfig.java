package com.conversationalcommerce.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration including CORS.
 * Set app.cors.allowed-origins for production (e.g. https://your-frontend.com).
 * Default "*" allows all origins for development.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOriginsConfig;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = "*".equals(allowedOriginsConfig) || allowedOriginsConfig.isEmpty()
                ? new String[]{"*"}
                : allowedOriginsConfig.split(",\\s*");
        String[] mappings = {"/api/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"};
        for (String mapping : mappings) {
            registry.addMapping(mapping)
                    .allowedOrigins(origins)
                    .allowedMethods("GET", "POST", "OPTIONS")
                    .allowedHeaders("*");
        }
    }
}
