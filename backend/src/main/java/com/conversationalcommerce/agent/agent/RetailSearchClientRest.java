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
        String url = BASE_URL + "/" + placement + ":search";
        String body = """
                {
                  "branch": "%s",
                  "query": "%s",
                  "visitorId": "%s",
                  "pageSize": 20
                }
                """.formatted(
                escapeJson(branch),
                escapeJson(query),
                escapeJson(visitorId)
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

                String id = product.containsKey("name") ? String.valueOf(product.get("name")) : "";
                String title = product.containsKey("title") ? String.valueOf(product.get("title")) : "";
                String desc = product.containsKey("description") ? String.valueOf(product.get("description")) : "";
                String price = "";
                if (product.containsKey("priceInfo")) {
                    Map<String, Object> priceInfo = (Map<String, Object>) product.get("priceInfo");
                    if (priceInfo != null && priceInfo.containsKey("price")) {
                        Object p = priceInfo.get("price");
                        if (p instanceof Number num && num.doubleValue() > 0) {
                            price = String.format("$%.2f", num.doubleValue());
                        }
                    }
                }
                String imageUri = null;
                if (product.containsKey("images") && product.get("images") instanceof List<?> images && !images.isEmpty()) {
                    Object img = images.get(0);
                    if (img instanceof Map<?, ?> imgMap && imgMap.containsKey("uri")) {
                        imageUri = String.valueOf(imgMap.get("uri"));
                    }
                }
                results.add(new AgentResponse.ProductResult(id, title, desc, price, imageUri));
            }
        } catch (Exception e) {
            log.warn("Failed to parse search response: {}", e.getMessage());
        }
        return results;
    }
}
