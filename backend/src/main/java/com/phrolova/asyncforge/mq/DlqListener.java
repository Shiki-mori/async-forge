package com.phrolova.asyncforge.mq;

import com.phrolova.asyncforge.entity.Task;
import com.phrolova.asyncforge.entity.TaskStatus;
import com.phrolova.asyncforge.mapper.TaskMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqListener {

    private final TaskMapper taskMapper;

    @RabbitListener(queues = "${async-forge.mq.dlq}")
    /**
     * 处理死信队列消息
     * 进入DLQ的消息不再重试执行，仅负责把任务收成终态DEAD，并确认死信消息
     * @param message 消息
     * @param rawMessage 原始消息
     * @param channel 通道
     * @param deliveryTag 交付标签
     * @throws IOException 输入输出异常
     */
    public void onDeadLetter(TaskMessage message,
                             Message rawMessage,
                             Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Long taskId = message.getTaskId();
        log.warn("Task entered DLQ, taskId={}", taskId);

        Task task = taskMapper.selectById(taskId);
        // 如果任务存在且状态不为DEAD，则更新任务状态为DEAD
        if (task != null && !TaskStatus.DEAD.name().equals(task.getStatus())) {
            Task update = new Task();
            update.setId(taskId);
            update.setStatus(TaskStatus.DEAD.name());
            update.setErrorMessage("Task exhausted retries and entered dead letter queue");
            taskMapper.updateById(update);
        }

        // 确认消息
        channel.basicAck(deliveryTag, false);
    }
}
