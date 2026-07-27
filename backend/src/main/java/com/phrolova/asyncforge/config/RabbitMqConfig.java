package com.phrolova.asyncforge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phrolova.asyncforge.common.ErrorCode;
import com.phrolova.asyncforge.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitMqConfig {

    private final MqProperties mqProperties;

    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(mqProperties.getExchange(), true, false);
    }

    @Bean
    public DirectExchange taskDlxExchange() {
        return new DirectExchange(mqProperties.getDlxExchange(), true, false);
    }

    @Bean
    public Queue taskQueue() {
        return QueueBuilder.durable(mqProperties.getQueue())
                .withArgument("x-dead-letter-exchange", mqProperties.getDlxExchange())
                .withArgument("x-dead-letter-routing-key", mqProperties.getDlqRoutingKey())
                .build();
    }

    @Bean
    public Queue taskDlq() {
        return QueueBuilder.durable(mqProperties.getDlq()).build();
    }

    @Bean
    public Binding taskBinding() {
        return BindingBuilder.bind(taskQueue())
                .to(taskExchange())
                .with(mqProperties.getRoutingKey());
    }

    @Bean
    public Binding taskDlqBinding() {
        return BindingBuilder.bind(taskDlq())
                .to(taskDlxExchange())
                .with(mqProperties.getDlqRoutingKey());
    }

    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
