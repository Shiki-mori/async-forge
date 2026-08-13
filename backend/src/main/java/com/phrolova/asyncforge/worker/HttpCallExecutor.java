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

    /**
     * URL安全限制，防SSRF攻击
     * @param url
     * @throws Exception
     */
    private void validateUrl(String url) throws Exception {
        // 将URL转换为URI对象
        URI uri = URI.create(url);

        // 获取URL的协议
        String scheme = uri.getScheme();
        // 若协议不是http或https，则抛出异常
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new BusinessException(ErrorCode.HTTP_CALL_BLOCKED, "only http/https is allowed");
        }

        // 获取URL的主机名
        String host = uri.getHost();
        // 若主机名为空，则抛出异常
        if (host == null || host.isBlank()) {
            throw new BusinessException(ErrorCode.HTTP_CALL_BLOCKED, "invalid host");
        }

        // 若主机名为私有或本地地址，则抛出异常
        if (isBlockedHost(host)) {
            throw new BusinessException(ErrorCode.HTTP_CALL_BLOCKED, "private or local addresses are blocked");
        }
    }

    /**
     * 判断主机名是否为私有或本地地址
     * @param host
     * @throws Exception
     */
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

    /**
     * 截取响应体的前MAX_BODY_LENGTH=1024个字符
     * @param body
     * @return 截取后的响应体
     */
    private String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= MAX_BODY_LENGTH ? body : body.substring(0, MAX_BODY_LENGTH);
    }
}
