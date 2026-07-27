package com.phrolova.asyncforge.mq;

import com.phrolova.asyncforge.service.TaskExecutionService;
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
public class TaskConsumer {

    private final TaskExecutionService taskExecutionService;

    @RabbitListener(queues = "${async-forge.mq.queue}")
    public void consume(TaskMessage message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Long taskId = message.getTaskId();
        log.info("Consuming task message, taskId={}", taskId);
        try {
            taskExecutionService.execute(taskId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("Task execution failed in consumer, taskId={}", taskId, ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
