package com.phrolova.asyncforge.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phrolova.asyncforge.entity.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DelayDemoExecutor读取 task 的 payload 字段中的 seconds 和 fail 字段，分别表示延迟的秒数和是否执行失败
 * <p>
 * ----------------------
 * 输入
 * {
 * "seconds": 0,
 * "fail": false
 * }
 * 预期返回的JSON包含
 * {
 * "message": "delay completed",
 * "seconds": 0
 * }
 * <p>
 * ----------------------
 * 输入
 * {
 * "seconds": 0,
 * "fail": true
 * }
 * 预期抛出异常 IllegalStateException
 * 异常信息 DELAY_DEMO forced failure
 */

class DelayDemoExecutorTest {
    private ObjectMapper objectMapper;
    private DelayDemoExecutor executor;

    @BeforeEach
    /*
     * JUnit 每执行一次测试方法之前都会执行一次 setUp 方法
     */
    void setUp() {
        objectMapper = new ObjectMapper();
        executor = new DelayDemoExecutor(objectMapper);
    }

    @Test
    void executorReturnsResultWhenFailsFalse() throws Exception {
        // 准备
        Task task = new Task();
        task.setPayloadJson("""
                {
                    "seconds":0,
                    "fail":false
                }
                """);

        // 执行
        String resultJson = executor.execute(task);

        // 断言
        JsonNode result = objectMapper.readTree(resultJson);
        assertEquals("delay completed", result.path("message").asText());
        assertEquals(0, result.path("seconds").asInt());
    }

    @Test
    void executorThrowsExceptionWhenFailsTrue() {
        // 准备
        Task task = new Task();
        task.setPayloadJson("""
                {
                    "seconds":0,
                    "fail":true
                }
                """);

        // 执行并断言异常类型
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> executor.execute(task));

        // 断言异常信息
        assertEquals("DELAY_DEMO forced failure", exception.getMessage());
    }
}
