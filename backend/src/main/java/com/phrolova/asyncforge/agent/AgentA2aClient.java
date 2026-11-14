package com.phrolova.asyncforge.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.UUID;

/**
 * Pulls the Agent Card then issues a blocking A2A {@code SendMessage}.
 * Does not interpret summary / toolCalls — that is {@code AgentTaskExecutor}'s job.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentA2aClient {

    private static final String A2A_VERSION = "1.0";
    private static final String CARD_PATH = "/.well-known/agent-card.json";
    private static final String COMPLETED = "TASK_STATE_COMPLETED";
    private static final String FAILED = "TASK_STATE_FAILED";

    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;
    // uvicorn 只讲 HTTP/1.1；JDK 默认 HTTP/2 upgrade 会把 POST body 弄丢，Agent 侧出现 JSON-RPC -32700
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public JsonNode send(Long taskId, String instruction, boolean forceFail) throws Exception {
        String baseUrl = normalizeBaseUrl(agentProperties.getBaseUrl());
        Duration timeout = requestTimeout();

        fetchAgentCard(baseUrl, timeout);
        JsonNode rpcResponse = sendMessage(baseUrl, taskId, instruction, forceFail, timeout);
        return extractResultJson(rpcResponse, taskId);
    }

    private void fetchAgentCard(String baseUrl, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + CARD_PATH))
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = sendHttp(request);
        require2xx(response, "agent card");
        JsonNode card = readJson(response.body(), "agent card");
        if (card.path("name").asText("").isBlank()) {
            throw new IllegalStateException("agent card missing name");
        }
        log.info("Fetched agent card, task wiring name={}", card.path("name").asText());
    }

    private JsonNode sendMessage(String baseUrl,
                                 Long taskId,
                                 String instruction,
                                 boolean forceFail,
                                 Duration timeout) throws Exception {
        ObjectNode userPayload = objectMapper.createObjectNode();
        userPayload.put("taskId", taskId);
        userPayload.put("instruction", instruction);
        userPayload.put("forceFail", forceFail);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", UUID.randomUUID().toString());
        body.put("method", "SendMessage");

        ObjectNode message = body.putObject("params").putObject("message");
        message.put("role", "ROLE_USER");
        message.put("messageId", UUID.randomUUID().toString());
        ArrayNode parts = message.putArray("parts");
        parts.addObject().put("text", objectMapper.writeValueAsString(userPayload));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("A2A-Version", A2A_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = sendHttp(request);
        require2xx(response, "A2A SendMessage");
        return readJson(response.body(), "A2A SendMessage");
    }

    private JsonNode extractResultJson(JsonNode rpcResponse, Long taskId) {
        if (rpcResponse.has("error") && !rpcResponse.get("error").isNull()) {
            String message = rpcResponse.path("error").path("message").asText("json-rpc error");
            throw new IllegalStateException("A2A json-rpc error: " + message);
        }

        JsonNode taskNode = resolveTaskNode(rpcResponse);
        String state = taskNode.path("status").path("state").asText("");
        log.info("A2A SendMessage returned, taskId={}, state={}", taskId, state);

        if (FAILED.equals(state)) {
            String detail = firstTextPart(taskNode.path("status").path("message").path("parts"));
            throw new IllegalStateException(detail != null ? detail : "A2A task failed");
        }
        if (!COMPLETED.equals(state)) {
            throw new IllegalStateException("unexpected A2A task state: " + (state.isBlank() ? "missing" : state));
        }

        String resultText = extractCompletedText(taskNode);
        if (resultText == null || resultText.isBlank()) {
            throw new IllegalStateException("A2A completed task missing result JSON text");
        }
        return readJson(resultText, "A2A result_json");
    }

    private JsonNode resolveTaskNode(JsonNode rpcResponse) {
        JsonNode result = rpcResponse.path("result");
        if (result.has("task")) {
            return result.get("task");
        }
        if (result.has("status")) {
            return result;
        }
        throw new IllegalStateException("A2A response missing task");
    }

    private String extractCompletedText(JsonNode taskNode) {
        JsonNode artifacts = taskNode.path("artifacts");
        if (artifacts.isArray()) {
            String firstText = null;
            for (JsonNode artifact : artifacts) {
                String text = firstTextPart(artifact.path("parts"));
                if (text == null) {
                    continue;
                }
                if ("result_json".equals(artifact.path("name").asText())) {
                    return text;
                }
                if (firstText == null) {
                    firstText = text;
                }
            }
            if (firstText != null) {
                return firstText;
            }
        }
        return firstTextPart(taskNode.path("status").path("message").path("parts"));
    }

    private String firstTextPart(JsonNode parts) {
        if (!parts.isArray()) {
            return null;
        }
        for (JsonNode part : parts) {
            JsonNode text = part.get("text");
            if (text != null && text.isTextual() && !text.asText().isBlank()) {
                return text.asText();
            }
        }
        return null;
    }

    private HttpResponse<String> sendHttp(HttpRequest request) throws Exception {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException ex) {
            throw new IllegalStateException(
                    "A2A call timed out after " + requestTimeout().toSeconds() + "s", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("A2A call interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("A2A call failed: " + ex.getMessage(), ex);
        }
    }

    private void require2xx(HttpResponse<String> response, String what) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(what + " HTTP " + status);
        }
    }

    private JsonNode readJson(String body, String what) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node == null || node.isMissingNode() || node.isNull()) {
                throw new IllegalStateException(what + " body is empty");
            }
            return node;
        } catch (IOException ex) {
            throw new IllegalStateException(what + " is not JSON", ex);
        }
    }

    private Duration requestTimeout() {
        int seconds = agentProperties.getTimeoutSeconds();
        return Duration.ofSeconds(seconds > 0 ? seconds : 60);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("async-forge.agent.base-url is not set");
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
