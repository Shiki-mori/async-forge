package com.phrolova.asyncforge.service;

import com.phrolova.asyncforge.entity.Task;
import com.phrolova.asyncforge.entity.TaskStatus;
import com.phrolova.asyncforge.mapper.TaskMapper;
import com.phrolova.asyncforge.mq.TaskProducer;
import com.phrolova.asyncforge.worker.TaskExecutor;
import com.phrolova.asyncforge.worker.TaskExecutorRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionService {

    private final TaskMapper taskMapper;
    private final TaskExecutorRegistry taskExecutorRegistry;
    private final TaskProducer taskProducer;

    @Transactional
    // 执行任务
    public void execute(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("Task not found, taskId={}", taskId);
            return;
        }

        // 如果任务状态为成功或死亡，则跳过消费，避免重复执行或变动终态任务
        if (TaskStatus.SUCCESS.name().equals(task.getStatus())
                || TaskStatus.DEAD.name().equals(task.getStatus())) {
            log.info("Skip duplicate consumption for terminal task, taskId={}, status={}", taskId, task.getStatus());
            return;
        }

        // 尝试获取任务执行权
        int claimed = taskMapper.claimForExecution(taskId, TaskStatus.RUNNING.name());
        // 如果获取失败，则跳过消费，避免多个消费者竞争同一个任务
        if (claimed == 0) {
            log.info("Skip duplicate or in-flight task, taskId={}, status={}", taskId, task.getStatus());
            return;
        }

        try {
            TaskExecutor executor = taskExecutorRegistry.get(task.getTaskType());
            String resultJson = executor.execute(task);

            // 更新任务状态为成功
            Task success = new Task();
            success.setId(taskId);
            success.setStatus(TaskStatus.SUCCESS.name());
            success.setResultJson(resultJson);
            success.setErrorMessage(null);
            taskMapper.updateById(success);
            log.info("Task succeeded, taskId={}", taskId);
        } catch (Exception ex) {
            handleFailure(task, ex.getMessage());
        }
    }

    // 失败重试机制
    private void handleFailure(Task task, String errorMessage) {
        int nextRetryCount = task.getRetryCount() + 1;
        boolean exhausted = nextRetryCount >= task.getMaxRetry();

        Task update = new Task();
        update.setId(task.getId());
        update.setRetryCount(nextRetryCount);
        update.setErrorMessage(errorMessage);

        // 如果重试次数达到最大重试次数，则将任务状态设置为DEAD
        if (exhausted) {
            update.setStatus(TaskStatus.DEAD.name());
            taskMapper.updateById(update);
            log.warn("Task retries exhausted, taskId={}, retryCount={}", task.getId(), nextRetryCount);
        } else {
            // 如果重试次数未达到最大重试次数，则将任务状态设置为PENDING
            update.setStatus(TaskStatus.PENDING.name());
            taskMapper.updateById(update);
            Long taskId = task.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    taskProducer.publish(taskId);
                }
            });
            log.info("Task failed and scheduled for retry, taskId={}, retryCount={}", task.getId(), nextRetryCount);
        }
    }
}
