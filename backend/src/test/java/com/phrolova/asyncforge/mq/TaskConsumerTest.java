package com.phrolova.asyncforge.mq;

import com.phrolova.asyncforge.service.ExecutionOutcome;
import com.phrolova.asyncforge.service.TaskExecutionService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskConsumerTest {

    private static final long TAG = 42L;

    private final TaskExecutionService service = mock(TaskExecutionService.class);
    private final Channel channel = mock(Channel.class);
    private final TaskConsumer consumer = new TaskConsumer(service);

    @ParameterizedTest
    @EnumSource(value = ExecutionOutcome.class, names = {"SUCCESS", "RETRY", "SKIPPED"})
    void acksWhenMessageIsHandled(ExecutionOutcome outcome) throws IOException {
        when(service.execute(1L, null)).thenReturn(outcome);

        consumer.consume(new TaskMessage(1L), channel, TAG, null);

        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void rejectsToDlqWhenRetriesExhausted() throws IOException {
        when(service.execute(1L, 2)).thenReturn(ExecutionOutcome.EXHAUSTED);

        consumer.consume(new TaskMessage(1L), channel, TAG, 2);

        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void rejectsToDlqOnUnexpectedException() throws IOException {
        when(service.execute(1L, null)).thenThrow(new IllegalStateException("db down"));

        consumer.consume(new TaskMessage(1L), channel, TAG, null);

        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
