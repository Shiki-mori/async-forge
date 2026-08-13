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
        // 将JSON文本解析为JSON树，JsonNode是JSON树的根节点，也即JSON对象
        /*
        json树的结构：
        payload (ObjectNode)
        ├── seconds → 1
        └── fail    → true
        */
        JsonNode payload = objectMapper.readTree(task.getPayloadJson());
        /*
        使用path而不是get:
        如果字段不存在,
        path将返回一个缺失节点MissingNode,不会抛出NullPointerException；
        get将返回null，后续再调用asInt()时会空指针异常。
        */
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
