package com.phrolova.asyncforge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "async-forge.mq")
public class MqProperties {

    private String exchange;
    private String queue;
    private String routingKey;
    private String dlxExchange;
    private String dlq;
    private String dlqRoutingKey;
}
