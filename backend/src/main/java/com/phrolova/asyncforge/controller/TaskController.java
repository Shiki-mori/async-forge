package com.phrolova.asyncforge.controller;

import com.phrolova.asyncforge.common.Result;
import com.phrolova.asyncforge.dto.request.CreateTaskRequest;
import com.phrolova.asyncforge.dto.response.TaskResponse;
import com.phrolova.asyncforge.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public Result<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        return Result.success(taskService.createTask(request));
    }

    @GetMapping
    public Result<List<TaskResponse>> listTasks() {
        return Result.success(taskService.listTasks());
    }

    @GetMapping("/{id}")
    public Result<TaskResponse> getTask(@PathVariable Long id) {
        return Result.success(taskService.getTask(id));
    }
}
