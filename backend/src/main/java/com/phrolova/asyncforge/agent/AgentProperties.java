package com.phrolova.asyncforge.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "async-forge.agent")
public class AgentProperties {

    private String baseUrl = "http://localhost:8081";
    private int timeoutSeconds = 60;
}
