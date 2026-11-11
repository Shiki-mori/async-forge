package com.phrolova.asyncforge.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "async-forge.task.max-retry=3"
)
class DelayDemoRetryIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("async_forge")
            .withUsername("asyncforge_test")
            .withPassword("test-password")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("schema.sql"),
                    "/docker-entrypoint-initdb.d/01-schema.sql"
            );

    @Container
    static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management")
            .withAdminUser("asyncforge_test")
            .withAdminPassword("test-password");

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }

    @Autowired
    private TestRestTemplate http;

    @Test
    void failingDelayTaskBecomesDeadAfterThreeFailures() {
        // 准备：注册一个测试用户并取得 JWT。
        ResponseEntity<JsonNode> registration = http.postForEntity(
                "/api/auth/register",
                Map.of(
                        "username", "it-" + UUID.randomUUID(),
                        "password", "test-password"
                ),
                JsonNode.class
        );
        String token = requireSuccess(registration).path("token").asText();
        assertFalse(token.isBlank(), "注册成功后应返回 token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        // 执行：提交一个每次执行都会失败的任务。
        Map<String, Object> taskBody = Map.of(
                "taskType", "DELAY_DEMO",
                "payload", Map.of("seconds", 0, "fail", true)
        );
        ResponseEntity<JsonNode> submission = http.postForEntity(
                "/api/tasks",
                new HttpEntity<>(taskBody, headers),
                JsonNode.class
        );
        long taskId = requireSuccess(submission).path("id").asLong();
        assertTrue(taskId > 0, "创建任务后应返回有效的 id");

        // 断言：按任务 ID 查询，等待 Worker 完成有限重试。
        HttpEntity<Void> query = new HttpEntity<>(headers);
        await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    ResponseEntity<JsonNode> response = http.exchange(
                            "/api/tasks/{id}",
                            HttpMethod.GET,
                            query,
                            JsonNode.class,
                            taskId
                    );
                    JsonNode task = requireSuccess(response);

                    assertEquals("DEAD", task.path("status").asText(), task.toString());
                    assertEquals(3, task.path("retryCount").asInt(-1));
                    assertEquals("DELAY_DEMO forced failure", task.path("errorMessage").asText());
                });
    }

    private JsonNode requireSuccess(ResponseEntity<JsonNode> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = response.getBody();
        assertNotNull(body, "接口应返回 JSON 响应体");
        assertEquals(0, body.path("code").asInt(-1), body.path("message").asText());
        return body.path("data");
    }
}