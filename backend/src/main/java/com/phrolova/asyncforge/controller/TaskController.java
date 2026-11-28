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

// @RestController = @Controller + @ResponseBody
// @ResponseBody: 返回值就是响应体的内容
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * 创建任务
     * @param request 创建任务请求
     * @return 任务响应
     */
    @PostMapping
    public Result<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        // 此处的 taskService 是 Spring 生成的事务代理
        // TaskServiceImpl.createTask 返回后，事务代理再将返回结果交还到 Controller
        // 代理的 return 发生在事务提交后的回调完成之后
        return Result.success(taskService.createTask(request));
    }

    /**
     * 获取当前用户任务列表
     * @return 任务列表
     */
    @GetMapping
    public Result<List<TaskResponse>> listTasks() {
        return Result.success(taskService.listTasks());
    }

    /**
     * 获取任务详情
     * @param id 任务ID
     * @return 任务详情
     */
    @GetMapping("/{id}")
    public Result<TaskResponse> getTask(@PathVariable Long id) {
        return Result.success(taskService.getTask(id));
    }
}
