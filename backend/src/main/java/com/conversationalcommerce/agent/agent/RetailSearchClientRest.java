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
import java.util.HashMap;
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
                  "pageSize": 20%s
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

                String id = nullToEmpty(getString(product, "name"));
                String title = nullToEmpty(getString(product, "title"));
                String desc = nullToEmpty(getString(product, "description"));
                String price = parsePrice(product);
                String imageUri = parseFirstImageUri(product);
                String gtin = getString(product, "gtin");
                String productId = getString(product, "id");
                List<String> categories = getStringList(product, "categories");
                List<String> brands = getStringList(product, "brands");
                String uri = getString(product, "uri");
                String availability = product.containsKey("availability") ? String.valueOf(product.get("availability")) : null;
                List<String> sizes = getStringList(product, "sizes");
                List<String> materials = getStringList(product, "materials");
                Map<String, Object> attributes = parseAttributes(product);

                results.add(new AgentResponse.ProductResult(
                        id, title, desc, price, imageUri,
                        gtin, productId, categories, brands, uri, availability, sizes, materials, attributes));
            }
        } catch (Exception e) {
            log.warn("Failed to parse search response: {}", e.getMessage());
        }
        return results;
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static String getString(Map<String, Object> map, String key) {
        if (!map.containsKey(key)) return null;
        Object v = map.get(key);
        return v != null ? String.valueOf(v).trim() : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStringList(Map<String, Object> map, String key) {
        if (!map.containsKey(key) || !(map.get(key) instanceof List<?> list)) return null;
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) out.add(String.valueOf(item).trim());
        }
        return out.isEmpty() ? null : out;
    }

    private static String parsePrice(Map<String, Object> product) {
        if (!product.containsKey("priceInfo")) return "";
        Object pi = product.get("priceInfo");
        if (!(pi instanceof Map<?, ?> priceInfo)) return "";
        Object p = ((Map<?, ?>) priceInfo).get("price");
        if (p instanceof Number num && num.doubleValue() > 0) {
            return String.format("$%.2f", num.doubleValue());
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static String parseFirstImageUri(Map<String, Object> product) {
        if (!product.containsKey("images") || !(product.get("images") instanceof List<?> images) || images.isEmpty())
            return null;
        Object img = images.get(0);
        if (img instanceof Map<?, ?> imgMap && imgMap.containsKey("uri")) {
            return String.valueOf(imgMap.get("uri"));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseAttributes(Map<String, Object> product) {
        if (!product.containsKey("attributes") || !(product.get("attributes") instanceof Map<?, ?> attrs))
            return null;
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> e : ((Map<String, Object>) attrs).entrySet()) {
            Object val = e.getValue();
            if (val instanceof Map<?, ?> m) {
                if (m.containsKey("text") && m.get("text") instanceof List<?> textList && !textList.isEmpty()) {
                    out.put(e.getKey(), String.valueOf(textList.get(0)));
                } else if (m.containsKey("numbers") && m.get("numbers") instanceof List<?> numList && !numList.isEmpty()) {
                    out.put(e.getKey(), numList.get(0));
                }
            }
        }
        return out.isEmpty() ? null : out;
    }
}
