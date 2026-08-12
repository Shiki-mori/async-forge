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

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskMapper taskMapper;
    private final TaskProducer taskProducer;
    private final TaskProperties taskProperties;
    private final ObjectMapper objectMapper;

    /**
     * 创建任务
     * @param request 创建任务请求
     * @return 任务响应
     */
    @Override
    @Transactional // 该方法需要在数据库事务中执行
    // Spring 会自动开启一个数据库事务，并在事务提交后执行 afterCommit 方法
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

        // 立即返回taskId，HTTP不等待执行完成
        // Spring 随后提交事务，并调用 afterCommit 方法
        taskProducer.publishAfterCommit(task.getId());
        return toResponse(task);
    }

    /**
     * 获取任务详情
     * @param taskId 任务ID
     * @return 任务详情
     */
    @Override
    public TaskResponse getTask(Long taskId) {
        Task task = requireOwnedTask(taskId);
        return toResponse(task);
    }

    /**
     * 获取当前用户任务列表
     * @return 任务列表
     */
    @Override
    public List<TaskResponse> listTasks() {
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, UserContext.getUserId())
                .orderByDesc(Task::getId));
        return tasks.stream().map(this::toResponse).toList();
    }

    /**
     * 检查任务是否属于当前用户
     * @param taskId 任务ID
     * @return 任务
     * @throws BusinessException 如果任务不存在或不属于当前用户
     */
    private Task requireOwnedTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    /**
     * 验证任务类型是否支持
     * @param taskType 任务类型
     * @throws BusinessException 如果任务类型不支持
     */
    private void validateTaskType(String taskType) {
        boolean supported = Arrays.stream(TaskType.values())
                .anyMatch(type -> type.name().equals(taskType));
        if (!supported) {
            throw new BusinessException(ErrorCode.TASK_TYPE_UNSUPPORTED);
        }
    }

    /**
     * 将对象转换为JSON字符串
     * @param payload 对象
     * @return JSON字符串
     * @throws JsonProcessingException 如果转换失败
     */
    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "invalid payload");
        }
    }

    /**
     * 将任务转换为任务响应
     * @param task 任务
     * @return 任务响应
     */
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

    /**
     * 将JSON字符串转换为对象
     * @param json JSON字符串
     * @return 对象
     * @throws JsonProcessingException 如果转换失败
     */
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
