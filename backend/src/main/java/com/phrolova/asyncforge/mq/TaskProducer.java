package com.phrolova.asyncforge.mq;

import com.phrolova.asyncforge.config.MqProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProducer {

    private final RabbitTemplate rabbitTemplate;
    private final MqProperties mqProperties;

    public void publish(Long taskId) {
        TaskMessage message = new TaskMessage(taskId);
        rabbitTemplate.convertAndSend(
                mqProperties.getExchange(),
                mqProperties.getRoutingKey(),
                message
        );
        log.info("Published task to MQ, taskId={}", taskId);
    }
}
