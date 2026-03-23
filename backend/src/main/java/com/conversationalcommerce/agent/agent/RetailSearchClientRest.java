package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.GcpCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST-based product search. Use when transport=rest to avoid gRPC/credentials-at-init.
 * Credentials are obtained lazily per request (no init-time ADC requirement).
 */
@Component
@ConditionalOnExpression("@environment.getProperty('conversational-commerce.transport', 'rest') == 'rest'")
public class RetailSearchClientRest implements RetailSearchClient {

    private static final Logger log = LoggerFactory.getLogger(RetailSearchClientRest.class);
    private static final String BASE_URL = "https://retail.googleapis.com/v2";

    private final GcpCredentialsProvider credentialsProvider;
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    public RetailSearchClientRest(GcpCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    @Override
    public List<AgentResponse.ProductResult> search(String placement, String branch, String query, String visitorId) {
        return search(placement, branch, query, visitorId, null);
    }

    @Override
    public List<AgentResponse.ProductResult> search(String placement, String branch, String query, String visitorId, String filter) {
        String url = BASE_URL + "/" + placement + ":search";
        String filterJson = (filter != null && !filter.isBlank())
                ? ", \"filter\": \"" + escapeJson(filter) + "\""
                : "";
        String body = """
                {
                  "branch": "%s",
                  "query": "%s",
                  "visitorId": "%s",
                  "pageSize": 20,
                  "variantRollupKeys": ["price"]%s
                }
                """.formatted(
                escapeJson(branch),
                escapeJson(query),
                escapeJson(visitorId),
                filterJson
        );

        try {
            GoogleCredentials credentials = credentialsProvider.getCredentials();
            credentials.refreshIfExpired();
            String token = credentials.getAccessToken().getTokenValue();

            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json");
            String quotaProject = credentialsProvider.getQuotaProject();
            if (quotaProject != null && !quotaProject.isEmpty()) {
                requestBuilder.header(GcpCredentialsProvider.quotaProjectHeaderName(), quotaProject);
            }
            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.warn("Product search REST error: {} {}", response.statusCode(), response.body());
                return List.of();
            }

            return parseResponse(response.body());
        } catch (Exception e) {
            log.warn("Product search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    @SuppressWarnings("unchecked")
    private List<AgentResponse.ProductResult> parseResponse(String json) {
        List<AgentResponse.ProductResult> results = new ArrayList<>();
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> root = mapper.readValue(json, Map.class);
            List<Map<String, Object>> resultList = (List<Map<String, Object>>) root.get("results");
            if (resultList == null) return results;

            for (Map<String, Object> item : resultList) {
                Map<String, Object> product = (Map<String, Object>) item.get("product");
                if (product == null) continue;
                AgentResponse.ProductResult pr = ProductResponseParser.fromProductMap(product, item, false);
                if (pr != null) results.add(pr);
            }
        } catch (Exception e) {
            log.warn("Failed to parse search response: {}", e.getMessage());
        }
        return results;
    }
}
