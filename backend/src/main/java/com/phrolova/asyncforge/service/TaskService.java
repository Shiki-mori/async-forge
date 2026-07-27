package com.phrolova.asyncforge.service;

import com.phrolova.asyncforge.dto.request.CreateTaskRequest;
import com.phrolova.asyncforge.dto.response.TaskResponse;

import java.util.List;

public interface TaskService {

    TaskResponse createTask(CreateTaskRequest request);

    TaskResponse getTask(Long taskId);

    List<TaskResponse> listTasks();
}
