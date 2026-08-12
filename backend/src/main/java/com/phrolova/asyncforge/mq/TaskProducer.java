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
        // TransactionSynchronizationManager Spring提供的事务同步工具
        // 在当前活跃事务中注册事务同步回调，在事务生命周期的特定时点执行
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            // 数据库事务提交后，才发布消息到RabbitMQ
            public void afterCommit() {
                publish(taskId);
            }
        });
    }
}
