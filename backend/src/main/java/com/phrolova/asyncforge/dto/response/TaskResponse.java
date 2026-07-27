package com.phrolova.asyncforge.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TaskResponse {

    private Long id;
    private String taskType;
    private Object payload;
    private String status;
    private Integer retryCount;
    private Integer maxRetry;
    private String errorMessage;
    private Object result;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
