package com.phrolova.asyncforge.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phrolova.asyncforge.agent.AgentA2aClient;
import com.phrolova.asyncforge.entity.Task;
import com.phrolova.asyncforge.entity.TaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentTaskExecutor implements TaskExecutor {

    private static final int MAX_INSTRUCTION_LENGTH = 2000;

    private final ObjectMapper objectMapper;
    private final AgentA2aClient agentA2aClient;

    @Override
    public String taskType() {
        return TaskType.AGENT_TASK.name();
    }

    @Override
    public String execute(Task task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayloadJson());
        String instruction = payload.path("instruction").asText("").trim();
        if (instruction.isEmpty()) {
            throw new IllegalArgumentException("instruction is required");
        }
        if (instruction.length() > MAX_INSTRUCTION_LENGTH) {
            throw new IllegalArgumentException("instruction must be at most " + MAX_INSTRUCTION_LENGTH + " characters");
        }

        boolean forceFail = payload.path("forceFail").asBoolean(false);
        JsonNode result = agentA2aClient.send(task.getId(), instruction, forceFail);
        requireStructuredResult(result);
        return result.toString();
    }

    private void requireStructuredResult(JsonNode result) {
        JsonNode summary = result.get("summary");
        if (summary == null || !summary.isTextual() || summary.asText().isBlank()) {
            throw new IllegalStateException("agent result missing non-empty summary");
        }
        JsonNode toolCalls = result.get("toolCalls");
        if (toolCalls == null || !toolCalls.isArray()) {
            throw new IllegalStateException("agent result toolCalls must be an array");
        }
    }
}
