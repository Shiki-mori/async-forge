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
        /*
        DirectExchange: 直接交换器，根据路由键将消息路由到指定的队列
        name: 交换机名称
        durable: 持久化，消息持久化到磁盘，重启后可恢复交换机
        autoDelete: 自动删除，当没有队列绑定到该交换器时，自动删除该交换器
        */
        return new DirectExchange(mqProperties.getExchange(), true, false);
    }

    @Bean
    public DirectExchange taskDlxExchange() {
        return new DirectExchange(mqProperties.getDlxExchange(), true, false);
    }

    @Bean
    // 声明一个持久化队列，并为其配置死信交换机
    // 该队列为正常消费队列
    public Queue taskQueue() {
        return QueueBuilder.durable(mqProperties.getQueue())
                // 设置RabbitMQ参数"x-dead-letter-exchange"为死信交换机，"x-dead-letter-routing-key"为死信路由键
                // 当前队列产生死信后，将消息发送到哪个交换机
                // 死信消息被发送到DLX时，使用哪个routing-key
                .withArgument("x-dead-letter-exchange", mqProperties.getDlxExchange())
                .withArgument("x-dead-letter-routing-key", mqProperties.getDlqRoutingKey())
                .build();
    }

    @Bean
    // 死信队列，用于存储死信消息
    public Queue taskDlq() {
        return QueueBuilder.durable(mqProperties.getDlq()).build();
    }

    // 将正常消费队列绑定到交换机
    @Bean
    public Binding taskBinding() {
        return BindingBuilder.bind(taskQueue())
                .to(taskExchange())
                .with(mqProperties.getRoutingKey());
    }

    // 将死信队列绑定到死信交换机
    @Bean
    public Binding taskDlqBinding() {
        return BindingBuilder.bind(taskDlq())
                .to(taskDlxExchange())
                .with(mqProperties.getDlqRoutingKey());
    }

    @Bean
    // 消息转换器，将消息转换为JSON格式
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
