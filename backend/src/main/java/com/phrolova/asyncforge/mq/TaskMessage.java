package com.phrolova.asyncforge.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskMessage {

    // 重试次数的消息头名：首次投递不带此头，只能抢占 PENDING 任务；
    // 重试消息带此头，只能抢占 FAILED 且 retry_count 与头值一致的任务，防止过期或重复消息重复执行
    public static final String RETRY_COUNT_HEADER = "task-retry-count";

    private Long taskId;
}
