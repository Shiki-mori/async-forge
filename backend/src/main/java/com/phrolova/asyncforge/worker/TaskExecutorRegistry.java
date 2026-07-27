package com.phrolova.asyncforge.worker;

import com.phrolova.asyncforge.common.ErrorCode;
import com.phrolova.asyncforge.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TaskExecutorRegistry {

    private final Map<String, TaskExecutor> executors;

    public TaskExecutorRegistry(List<TaskExecutor> executorList) {
        this.executors = executorList.stream()
                .collect(Collectors.toMap(TaskExecutor::taskType, Function.identity()));
    }

    public TaskExecutor get(String taskType) {
        TaskExecutor executor = executors.get(taskType);
        if (executor == null) {
            throw new BusinessException(ErrorCode.TASK_TYPE_UNSUPPORTED);
        }
        return executor;
    }
}
