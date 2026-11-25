package com.phrolova.asyncforge.mq;

import com.phrolova.asyncforge.config.MqProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProducer {

    private final RabbitTemplate rabbitTemplate;
    private final MqProperties mqProperties;

    // 向 mq 投递消息
    public void publish(Long taskId) {
        TaskMessage message = new TaskMessage(taskId);
        /*
        convertAndSend: 将message对象转换为JSON格式，并发送消息到RabbitMQ
        mqProperties.getExchange(): 交换机名称
        mqProperties.getRoutingKey(): 路由键
        message: 消息内容
        */
        rabbitTemplate.convertAndSend(
                mqProperties.getExchange(),
                mqProperties.getRoutingKey(),
                message
        );
        log.info("Published task to MQ, taskId={}", taskId);
    }

    /**
     * 在当前事务提交后再发布消息，避免消费者读到未提交的 DB 记录。
     * 若 DB 操作之后、commit 之前抛出异常，则事务回滚，task不入库，不会执行 afterCommit。
     */
    public void publishAfterCommit(Long taskId) {
        afterCommit(() -> publish(taskId));
        // Lambda 表达式，等价于：

        // Runnable action = new Runnable() {
        //     @Override
        //     public void run() {
        //         publish(taskId);
        //     }
        // };
        // afterCommit(action);
    }

    public void publishRetryAfterCommit(Long taskId, int retryCount) {
        String routingKey = retryCount == 1
                ? mqProperties.getRetry2sRoutingKey()
                : mqProperties.getRetry8sRoutingKey();
        afterCommit(() -> {
            rabbitTemplate.convertAndSend(mqProperties.getExchange(), routingKey, new TaskMessage(taskId), message -> {
                message.getMessageProperties().setHeader(TaskMessage.RETRY_COUNT_HEADER, retryCount);
                return message;
            });
            log.info("Published retry to MQ, taskId={}, retryCount={}, routingKey={}", taskId, retryCount, routingKey);
        });
    }

    // 将一段操作登记到当前数据库事务上，等事务提交成功后再执行
    // 两个 afterCommit 同名但不同:
    // 外层是 TaskProducer 自己的方法，负责登记；
    // 内层是 Spring 回调接口的方法，负责执行。
    private void afterCommit(Runnable action) {          // action：提交后要做的事
        TransactionSynchronizationManager                // Spring 的事务同步管理器
                .registerSynchronization(                    // 给当前线程的事务登记一个回调
                        new TransactionSynchronization() {       // 匿名内部类，实现回调接口
                            @Override
                            public void afterCommit() {          // 事务提交成功后由 Spring 调用
                                action.run();                    // 这时才真正执行
                            }
                        });
    }
}
