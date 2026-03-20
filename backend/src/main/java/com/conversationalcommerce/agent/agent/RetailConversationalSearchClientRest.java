package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
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
 * REST-based implementation using HTTP/1.1. Use when gRPC fails (e.g. "Failed ALPN negotiation" behind VPN/proxy).
 * Set conversational-commerce.transport=rest to enable.
 */
@Component
@ConditionalOnExpression("@environment.getProperty('conversational-commerce.transport', 'rest') == 'rest'")
public class RetailConversationalSearchClientRest implements ConversationalCommerceClient {

    private static final Logger log = LoggerFactory.getLogger(RetailConversationalSearchClientRest.class);
    private static final String BASE_URL = "https://retail.googleapis.com/v2";

    private final ConversationalCommerceConfig config;
    private final GcpCredentialsProvider credentialsProvider;
    private final HttpClient httpClient;

    public RetailConversationalSearchClientRest(ConversationalCommerceConfig config,
                                                GcpCredentialsProvider credentialsProvider) {
        this.config = config;
        this.credentialsProvider = credentialsProvider;
        this.httpClient = HttpClient.newBuilder().build();
        log.info("Using REST transport for GCP Conversational Commerce (bypasses gRPC/ALPN)");
    }

    @Override
    public ConversationalCommerceResult search(ConversationalCommerceRequest request) {
        String url = BASE_URL + "/" + request.placement() + ":conversationalSearch";
        String body = buildRequestBody(request);

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
            HttpRequest httpRequest = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                throw new RuntimeException("REST API error: " + response.statusCode() + " " + response.body());
            }

            var result = parseResponse(response.body());
            if (log.isDebugEnabled()) {
                log.debug("Conversational search: requestConvId={} responseConvId={} query={}",
                        request.conversationId(), result.conversationId(), request.query());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("REST conversational search failed: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(ConversationalCommerceRequest request) {
        String mode = config.conversationalFilteringMode();
        String filteringSpec = "\"conversationalFilteringMode\": \"" + escapeJson(mode) + "\"";
        return """
                {
                  "branch": "%s",
                  "query": "%s",
                  "conversationId": "%s",
                  "visitorId": "%s",
                  "conversationalFilteringSpec": { %s }
                }
                """.formatted(
                escapeJson(request.branch()),
                escapeJson(request.query()),
                escapeJson(request.conversationId() != null ? request.conversationId() : ""),
                escapeJson(request.visitorId()),
                filteringSpec
        );
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /** Removes duplicated sentences (e.g. when streaming sends same content twice). */
    private static String deduplicateText(String text) {
        if (text == null || text.length() < 2) return text;
        int mid = text.length() / 2;
        String first = text.substring(0, mid).trim();
        String second = text.substring(mid).trim();
        if (first.equals(second)) return first;
        return text;
    }

    @SuppressWarnings("unchecked")
    private ConversationalCommerceResult parseResponse(String json) {
        List<String> textParts = new ArrayList<>();
        final String[] conversationId = {""};
        final String[] refinedQuery = {""};
        final String[] queryType = {""};
        final String[] followupQuestion = {""};

        try {
            Object parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
            if (parsed instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        extractFromMap((Map<String, Object>) m, textParts,
                                s -> conversationId[0] = s, s -> refinedQuery[0] = s, s -> queryType[0] = s, s -> followupQuestion[0] = s);
                    }
                }
            } else if (parsed instanceof Map<?, ?> m) {
                extractFromMap((Map<String, Object>) m, textParts,
                        s -> conversationId[0] = s, s -> refinedQuery[0] = s, s -> queryType[0] = s, s -> followupQuestion[0] = s);
            }
        } catch (Exception e) {
            log.warn("Failed to parse REST response, using raw: {}", e.getMessage());
        }

        String text = deduplicateText(String.join(" ", textParts));
        String source = "agent";
        if (text.isEmpty() && !followupQuestion[0].isEmpty()) {
            text = followupQuestion[0];
        } else if (text.isEmpty()) {
            text = refinedQuery[0] != null && !refinedQuery[0].isEmpty()
                    ? "Searching for: " + refinedQuery[0]
                    : "I didn't understand your response.";
            source = "app";
        }

        return new ConversationalCommerceResult(text, conversationId[0], refinedQuery[0], queryType[0], source);
    }

    @SuppressWarnings("unchecked")
    private void extractFromMap(Map<String, Object> map, List<String> textParts,
                               java.util.function.Consumer<String> setConversationId,
                               java.util.function.Consumer<String> setRefinedQuery,
                               java.util.function.Consumer<String> setQueryType,
                               java.util.function.Consumer<String> setFollowupQuestion) {
        if (map == null) return;
        // Check top-level and nested conversationalSearchResult (GCP nests it in SearchResponse)
        Map<String, Object> toScan = map;
        if (map.containsKey("conversationalSearchResult") && map.get("conversationalSearchResult") instanceof Map<?, ?> csr) {
            toScan = (Map<String, Object>) csr;
        }
        Object convId = toScan.get("conversationId");
        if (convId == null) convId = toScan.get("conversation_id");
        if (convId != null) {
            String s = String.valueOf(convId).trim();
            if (!s.isEmpty()) setConversationId.accept(s);
        }
        // refinedQuery: from refinedSearch (streaming) or conversationalSearchResult
        if (map.containsKey("refinedSearch") && map.get("refinedSearch") instanceof List<?> rs
                && !rs.isEmpty() && rs.get(0) instanceof Map<?, ?> rq) {
            Object q = ((Map<?, ?>) rq).get("query");
            if (q != null) setRefinedQuery.accept(String.valueOf(q));
        } else {
            Object rq = toScan.get("refinedQuery");
            if (rq != null) {
                String s = String.valueOf(rq).trim();
                if (!s.isEmpty()) setRefinedQuery.accept(s);
            }
        }
        if (map.containsKey("userQueryTypes") && map.get("userQueryTypes") instanceof List<?> uqt
                && !uqt.isEmpty()) {
            setQueryType.accept(String.valueOf(uqt.get(0)));
        }
        Object textObj = map.get("conversationalTextResponse");
        if (textObj == null) textObj = toScan.get("textResult");
        if (textObj != null) {
            String part = String.valueOf(textObj).trim();
            if (!part.isEmpty()) {
                if (!textParts.isEmpty()) {
                    textParts.set(textParts.size() - 1, part);
                } else {
                    textParts.add(part);
                }
            }
        }
        String fq = extractFollowupQuestion(map);
        if (fq.isEmpty() && toScan != map) fq = extractFollowupQuestion(toScan);
        if (!fq.isEmpty()) setFollowupQuestion.accept(fq);
    }

    @SuppressWarnings("unchecked")
    private String extractFollowupQuestion(Map<String, Object> map) {
        if (map == null) return "";
        // Direct string (ConversationalSearchResult format)
        Object fqObj = map.get("followupQuestion");
        if (fqObj != null) {
            if (fqObj instanceof String s && !s.trim().isEmpty()) return s.trim();
            if (fqObj instanceof Map<?, ?> fqMap) {
                Object q = ((Map<?, ?>) fqMap).get("followupQuestion");
                if (q != null) {
                    String s = String.valueOf(q).trim();
                    if (!s.isEmpty()) return s;
                }
            }
        }
        if (map.containsKey("conversationalFilteringResult") && map.get("conversationalFilteringResult") instanceof Map<?, ?> cfr) {
            Object cfrFq = ((Map<?, ?>) cfr).get("followupQuestion");
            if (cfrFq instanceof Map<?, ?> fq) {
                Object q = ((Map<?, ?>) fq).get("followupQuestion");
                if (q != null) {
                    String s = String.valueOf(q).trim();
                    if (!s.isEmpty()) return s;
                }
            }
        }
        return "";
    }
}
