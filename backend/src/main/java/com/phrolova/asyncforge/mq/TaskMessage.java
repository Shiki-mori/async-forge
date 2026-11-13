package com.phrolova.asyncforge.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskMessage {

    public static final String RETRY_COUNT_HEADER = "task-retry-count";

    private Long taskId;
}
