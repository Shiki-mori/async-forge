package com.phrolova.asyncforge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

import java.util.Objects;

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

    public void execute(Long taskId, Integer retryCount) {
        Task claimed = claimInShortTransaction(taskId, retryCount);
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
            String errorMessage = Objects.toString(ex.getMessage(), ex.getClass().getSimpleName());
            transactionTemplate.executeWithoutResult(status -> handleFailure(claimed, errorMessage));
        }
    }

    private Task claimInShortTransaction(Long taskId, Integer retryCount) {
        return transactionTemplate.execute(status -> {
            if (taskMapper.claimForExecution(taskId, retryCount) == 0) {
                log.info("Skip unclaimable task message, taskId={}, retryCount={}", taskId, retryCount);
                return null;
            }
            return taskMapper.selectById(taskId);
        });
    }

    private void markSuccessInShortTransaction(Long taskId, String resultJson) {
        transactionTemplate.executeWithoutResult(status -> taskMapper.update(Wrappers.<Task>lambdaUpdate()
                .eq(Task::getId, taskId)
                .set(Task::getStatus, TaskStatus.SUCCESS.name())
                .set(Task::getResultJson, resultJson)
                .set(Task::getErrorMessage, null)));
    }

    private void handleFailure(Task task, String errorMessage) {
        int nextRetryCount = task.getRetryCount() + 1;
        boolean exhausted = nextRetryCount >= task.getMaxRetry();

        Task update = new Task();
        update.setId(task.getId());
        update.setRetryCount(nextRetryCount);
        update.setErrorMessage(errorMessage);
        update.setStatus(exhausted ? TaskStatus.DEAD.name() : TaskStatus.FAILED.name());
        taskMapper.updateById(update);

        if (exhausted) {
            log.warn("Task retries exhausted, taskId={}, retryCount={}", task.getId(), nextRetryCount);
        } else {
            taskProducer.publishRetryAfterCommit(task.getId(), nextRetryCount);
        }
    }
}
