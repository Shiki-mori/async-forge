package com.phrolova.asyncforge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phrolova.asyncforge.auth.UserContext;
import com.phrolova.asyncforge.common.ErrorCode;
import com.phrolova.asyncforge.config.TaskProperties;
import com.phrolova.asyncforge.dto.request.CreateTaskRequest;
import com.phrolova.asyncforge.dto.response.TaskResponse;
import com.phrolova.asyncforge.entity.Task;
import com.phrolova.asyncforge.entity.TaskStatus;
import com.phrolova.asyncforge.entity.TaskType;
import com.phrolova.asyncforge.exception.BusinessException;
import com.phrolova.asyncforge.mapper.TaskMapper;
import com.phrolova.asyncforge.mq.TaskProducer;
import com.phrolova.asyncforge.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskMapper taskMapper;
    private final TaskProducer taskProducer;
    private final TaskProperties taskProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public TaskResponse createTask(CreateTaskRequest request) {
        validateTaskType(request.getTaskType());

        Task task = new Task();
        task.setUserId(UserContext.getUserId());
        task.setTaskType(request.getTaskType());
        task.setPayloadJson(toJson(request.getPayload()));
        task.setStatus(TaskStatus.PENDING.name());
        task.setRetryCount(0);
        task.setMaxRetry(taskProperties.getMaxRetry());
        taskMapper.insert(task);

        Long taskId = task.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskProducer.publish(taskId);
            }
        });
        return toResponse(task);
    }

    @Override
    public TaskResponse getTask(Long taskId) {
        Task task = requireOwnedTask(taskId);
        return toResponse(task);
    }

    @Override
    public List<TaskResponse> listTasks() {
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, UserContext.getUserId())
                .orderByDesc(Task::getId));
        return tasks.stream().map(this::toResponse).toList();
    }

    private Task requireOwnedTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    private void validateTaskType(String taskType) {
        boolean supported = Arrays.stream(TaskType.values())
                .anyMatch(type -> type.name().equals(taskType));
        if (!supported) {
            throw new BusinessException(ErrorCode.TASK_TYPE_UNSUPPORTED);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "invalid payload");
        }
    }

    private TaskResponse toResponse(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .taskType(task.getTaskType())
                .payload(parseJson(task.getPayloadJson()))
                .status(task.getStatus())
                .retryCount(task.getRetryCount())
                .maxRetry(task.getMaxRetry())
                .errorMessage(task.getErrorMessage())
                .result(parseJson(task.getResultJson()))
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException ex) {
            return json;
        }
    }
}
