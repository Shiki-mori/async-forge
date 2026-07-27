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
    public void execute(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("Task not found, taskId={}", taskId);
            return;
        }

        if (TaskStatus.SUCCESS.name().equals(task.getStatus())
                || TaskStatus.DEAD.name().equals(task.getStatus())) {
            log.info("Skip duplicate consumption for terminal task, taskId={}, status={}", taskId, task.getStatus());
            return;
        }

        int claimed = taskMapper.claimForExecution(taskId, TaskStatus.RUNNING.name());
        if (claimed == 0) {
            log.info("Skip duplicate or in-flight task, taskId={}, status={}", taskId, task.getStatus());
            return;
        }

        try {
            TaskExecutor executor = taskExecutorRegistry.get(task.getTaskType());
            String resultJson = executor.execute(task);

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

    private void handleFailure(Task task, String errorMessage) {
        int nextRetryCount = task.getRetryCount() + 1;
        boolean exhausted = nextRetryCount >= task.getMaxRetry();

        Task update = new Task();
        update.setId(task.getId());
        update.setRetryCount(nextRetryCount);
        update.setErrorMessage(errorMessage);

        if (exhausted) {
            update.setStatus(TaskStatus.DEAD.name());
            taskMapper.updateById(update);
            log.warn("Task retries exhausted, taskId={}, retryCount={}", task.getId(), nextRetryCount);
        } else {
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
