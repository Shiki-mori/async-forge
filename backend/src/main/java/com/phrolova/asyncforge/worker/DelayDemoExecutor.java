package com.phrolova.asyncforge.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phrolova.asyncforge.entity.Task;
import com.phrolova.asyncforge.entity.TaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DelayDemoExecutor implements TaskExecutor {

    private final ObjectMapper objectMapper;

    @Override
    public String taskType() {
        return TaskType.DELAY_DEMO.name();
    }

    @Override
    public String execute(Task task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayloadJson());
        int seconds = payload.path("seconds").asInt(1);
        boolean fail = payload.path("fail").asBoolean(false);

        if (seconds > 0) {
            Thread.sleep(seconds * 1000L);
        }
        if (fail) {
            throw new IllegalStateException("DELAY_DEMO forced failure");
        }

        return objectMapper.createObjectNode()
                .put("message", "delay completed")
                .put("seconds", seconds)
                .toString();
    }
}
