package com.phrolova.asyncforge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "async-forge.task")
public class TaskProperties {

    private int maxRetry = 3;
}
