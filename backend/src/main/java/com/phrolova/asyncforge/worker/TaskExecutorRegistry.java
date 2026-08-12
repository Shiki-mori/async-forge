package com.phrolova.asyncforge.worker;

import com.phrolova.asyncforge.common.ErrorCode;
import com.phrolova.asyncforge.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
// 任务执行器注册表
// 用于根据任务类型`taskType`获取对应的任务执行器`TaskExecutor`
public class TaskExecutorRegistry {

    private final Map<String, TaskExecutor> executors;

    // 构造函数，将任务执行器列表转换为Map，键为任务类型，值为任务执行器对象
    public TaskExecutorRegistry(List<TaskExecutor> executorList) {
        this.executors = executorList.stream()
                .collect(Collectors.toMap(TaskExecutor::taskType, Function.identity()));
    }

    // 根据任务类型获取对应的任务执行器
    public TaskExecutor get(String taskType) {
        TaskExecutor executor = executors.get(taskType);
        if (executor == null) {
            throw new BusinessException(ErrorCode.TASK_TYPE_UNSUPPORTED);
        }
        return executor;
    }
}
