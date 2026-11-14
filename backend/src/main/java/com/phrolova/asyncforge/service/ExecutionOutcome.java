package com.phrolova.asyncforge.service;

/**
 * 一次消息处理的结果，决定消费者如何确认当前消息。
 */
public enum ExecutionOutcome {
    /** 执行成功，ACK。 */
    SUCCESS,
    /** 执行失败但尚可重试，已写 FAILED 并在提交后投递延迟消息，ACK。 */
    RETRY,
    /** 重试耗尽，已写 DEAD，NACK(requeue=false) 让消息经 DLX 进入死信队列。 */
    EXHAUSTED,
    /** 任务不可抢占（已终态、重复或过期消息），无需执行，ACK。 */
    SKIPPED
}
