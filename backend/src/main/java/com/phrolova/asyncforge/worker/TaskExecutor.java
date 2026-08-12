package com.phrolova.asyncforge.worker;

import com.phrolova.asyncforge.entity.Task;

// 策略模式，根据任务类型选择不同的任务执行器

// 策略接口
public interface TaskExecutor {

    String taskType();

    String execute(Task task) throws Exception;
}
