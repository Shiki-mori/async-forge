package com.phrolova.asyncforge.mq;

import com.phrolova.asyncforge.entity.Task;
import com.phrolova.asyncforge.entity.TaskStatus;
import com.phrolova.asyncforge.mapper.TaskMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqListener {

    /** 监听容器 id，测试中可通过 RabbitListenerEndpointRegistry 暂停它来检查死信。 */
    public static final String LISTENER_ID = "dlqListener";

    private final TaskMapper taskMapper;

    /**
     * 处理死信队列消息。进入 DLQ 的消息不再执行，监听器只负责幂等收口并确认消息。
     * <ul>
     *   <li>重试耗尽：任务已在 NACK 前提交为 DEAD，这里只记录并 ACK。</li>
     *   <li>消费者意外异常（例如回写失败）：任务尚未终态，这里收成 DEAD。</li>
     * </ul>
     * 同一条死信被重复投递时，第二次看到的任务已是 DEAD，不会重复写库。
     */
    @RabbitListener(id = LISTENER_ID, queues = "${async-forge.mq.dlq}")
    public void onDeadLetter(TaskMessage message,
                             Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Long taskId = message.getTaskId();
        Task task = taskMapper.selectById(taskId);

        if (task == null) {
            log.warn("Dead letter for missing task, taskId={}", taskId);
        } else if (TaskStatus.DEAD.name().equals(task.getStatus())) {
            log.warn("Task entered DLQ after exhausting retries, taskId={}, retryCount={}",
                    taskId, task.getRetryCount());
        } else {
            log.warn("Task entered DLQ unexpectedly, marking DEAD, taskId={}, status={}",
                    taskId, task.getStatus());
            Task update = new Task();
            update.setId(taskId);
            update.setStatus(TaskStatus.DEAD.name());
            update.setErrorMessage("Task message was rejected to the dead letter queue");
            taskMapper.updateById(update);
        }

        channel.basicAck(deliveryTag, false);
    }
}
