package com.phrolova.asyncforge.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.phrolova.asyncforge.config.MqProperties;
import com.phrolova.asyncforge.mq.TaskMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
import java.time.LocalDateTime;
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

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MqProperties mqProperties;

    @Test
    void failingDelayTaskWaitsBetweenRetriesAndSkipsStaleMessages() {
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

        HttpEntity<Void> query = new HttpEntity<>(headers);
        LocalDateTime firstFailure = awaitTaskState(taskId, query, "FAILED", 1);
        LocalDateTime secondFailure = awaitTaskState(taskId, query, "FAILED", 2);
        assertBackoff(firstFailure, secondFailure, Duration.ofSeconds(2));

        // 在第二轮退避期间，把首投和上一轮重试消息直接重投到执行队列。
        TaskMessage duplicate = new TaskMessage(taskId);
        rabbitTemplate.convertAndSend(mqProperties.getExchange(), mqProperties.getRoutingKey(), duplicate);
        rabbitTemplate.convertAndSend(mqProperties.getExchange(), mqProperties.getRoutingKey(), duplicate, message -> {
            message.getMessageProperties().setHeader(TaskMessage.RETRY_COUNT_HEADER, 1);
            return message;
        });

        LocalDateTime exhausted = awaitTaskState(taskId, query, "DEAD", 3);
        assertBackoff(secondFailure, exhausted, Duration.ofSeconds(8));
    }

    private LocalDateTime awaitTaskState(long taskId, HttpEntity<Void> query, String expectedStatus, int expectedRetryCount) {
        JsonNode task = await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> getTask(taskId, query), response ->
                        expectedStatus.equals(response.path("status").asText())
                                && expectedRetryCount == response.path("retryCount").asInt(-1));
        assertEquals("DELAY_DEMO forced failure", task.path("errorMessage").asText());
        return LocalDateTime.parse(task.path("updatedAt").asText());
    }

    private JsonNode getTask(long taskId, HttpEntity<Void> query) {
        ResponseEntity<JsonNode> response = http.exchange(
                "/api/tasks/{id}", HttpMethod.GET, query, JsonNode.class, taskId
        );
        return requireSuccess(response);
    }

    private void assertBackoff(LocalDateTime previousFailure, LocalDateTime currentFailure, Duration expectedDelay) {
        Duration elapsed = Duration.between(previousFailure, currentFailure);
        assertTrue(elapsed.compareTo(expectedDelay) >= 0,
                () -> "两次失败间隔至少应为 " + expectedDelay + "，实际为 " + elapsed);
    }

    private JsonNode requireSuccess(ResponseEntity<JsonNode> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = response.getBody();
        assertNotNull(body, "接口应返回 JSON 响应体");
        assertEquals(0, body.path("code").asInt(-1), body.path("message").asText());
        return body.path("data");
    }
}
