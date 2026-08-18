package com.phrolova.asyncforge.service;

import com.phrolova.asyncforge.entity.Task;
import com.phrolova.asyncforge.entity.TaskStatus;
import com.phrolova.asyncforge.mapper.TaskMapper;
import com.phrolova.asyncforge.mq.TaskProducer;
import com.phrolova.asyncforge.worker.TaskExecutor;
import com.phrolova.asyncforge.worker.TaskExecutorRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class TaskExecutionService {

    private final TaskMapper taskMapper;
    private final TaskExecutorRegistry taskExecutorRegistry;
    private final TaskProducer taskProducer;
    private final TransactionTemplate transactionTemplate;

    public TaskExecutionService(TaskMapper taskMapper,
                                TaskExecutorRegistry taskExecutorRegistry,
                                TaskProducer taskProducer,
                                PlatformTransactionManager transactionManager) {
        this.taskMapper = taskMapper;
        this.taskExecutorRegistry = taskExecutorRegistry;
        this.taskProducer = taskProducer;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void execute(Long taskId) {
        Task claimed = claimInShortTransaction(taskId);
        if (claimed == null) {
            return;
        }

        try {
            TaskExecutor executor = taskExecutorRegistry.get(claimed.getTaskType());
            String resultJson = executor.execute(claimed);
            markSuccessInShortTransaction(taskId, resultJson);
            log.info("Task succeeded, taskId={}", taskId);
        } catch (Exception ex) {
            log.warn("Task execution failed, taskId={}", taskId, ex);
            transactionTemplate.executeWithoutResult(status -> handleFailure(claimed, ex.getMessage()));
        }
    }

    private Task claimInShortTransaction(Long taskId) {
        return transactionTemplate.execute(status -> {
            Task task = taskMapper.selectById(taskId);
            if (task == null) {
                log.warn("Task not found, taskId={}", taskId);
                return null;
            }

            if (TaskStatus.SUCCESS.name().equals(task.getStatus())
                    || TaskStatus.DEAD.name().equals(task.getStatus())) {
                log.info("Skip duplicate consumption for terminal task, taskId={}, status={}", taskId, task.getStatus());
                return null;
            }

            int claimed = taskMapper.claimForExecution(taskId, TaskStatus.RUNNING.name());
            if (claimed == 0) {
                log.info("Skip duplicate or in-flight task, taskId={}, status={}", taskId, task.getStatus());
                return null;
            }
            return task;
        });
    }

    private void markSuccessInShortTransaction(Long taskId, String resultJson) {
        transactionTemplate.executeWithoutResult(status -> {
            Task success = new Task();
            success.setId(taskId);
            success.setStatus(TaskStatus.SUCCESS.name());
            success.setResultJson(resultJson);
            success.setErrorMessage(null);
            taskMapper.updateById(success);
        });
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
            taskProducer.publishAfterCommit(task.getId());
            log.info("Task failed and scheduled for retry, taskId={}, retryCount={}", task.getId(), nextRetryCount);
        }
    }
}
