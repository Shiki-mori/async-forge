package com.phrolova.asyncforge.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phrolova.asyncforge.common.ErrorCode;
import com.phrolova.asyncforge.entity.Task;
import com.phrolova.asyncforge.entity.TaskType;
import com.phrolova.asyncforge.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class HttpCallExecutor implements TaskExecutor {

    private static final int MAX_BODY_LENGTH = 1024;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String taskType() {
        return TaskType.HTTP_CALL.name();
    }

    @Override
    public String execute(Task task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayloadJson());
        String url = payload.path("url").asText(null);
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "url is required");
        }

        validateUrl(url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String bodySnippet = truncate(response.body());

        return objectMapper.createObjectNode()
                .put("statusCode", response.statusCode())
                .put("bodySnippet", bodySnippet)
                .toString();
    }

    private void validateUrl(String url) throws Exception {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new BusinessException(ErrorCode.HTTP_CALL_BLOCKED, "only http/https is allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException(ErrorCode.HTTP_CALL_BLOCKED, "invalid host");
        }

        if (isBlockedHost(host)) {
            throw new BusinessException(ErrorCode.HTTP_CALL_BLOCKED, "private or local addresses are blocked");
        }
    }

    private boolean isBlockedHost(String host) throws Exception {
        String lowerHost = host.toLowerCase();
        if ("localhost".equals(lowerHost) || lowerHost.endsWith(".local")) {
            return true;
        }

        InetAddress address = InetAddress.getByName(host);
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress();
    }

    private String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= MAX_BODY_LENGTH ? body : body.substring(0, MAX_BODY_LENGTH);
    }
}
