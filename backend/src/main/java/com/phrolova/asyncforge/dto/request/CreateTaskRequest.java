package com.phrolova.asyncforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTaskRequest {

    @NotBlank(message = "taskType is required")
    private String taskType;

    @NotNull(message = "payload is required")
    private Object payload;
}
