package com.phrolova.asyncforge.worker;

import com.phrolova.asyncforge.entity.Task;

public interface TaskExecutor {

    String taskType();

    String execute(Task task) throws Exception;
}
