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

    /*
    注解 @RabbitListener 用于定义一个 RabbitMQ 消费者，监听指定的队列，当有消息到达时，会自动调用 consume 方法进行处理
    */
    @RabbitListener(queues = "${async-forge.mq.queue}")
    public void consume(TaskMessage message,
                        /*
                        参数 Channel channel 用于与 RabbitMQ 进行通信，发送ACK/NACK消息
                        deliveryTag 消息投递标签，用于标识消息的唯一标识
                        */
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Long taskId = message.getTaskId();
        log.info("Consuming task message, taskId={}", taskId);
        try {
            taskExecutionService.execute(taskId);
            // 发送ACK消息，确认消息已成功处理
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("Task execution failed in consumer, taskId={}", taskId, ex);
            // 发送NACK消息，拒绝消息
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
