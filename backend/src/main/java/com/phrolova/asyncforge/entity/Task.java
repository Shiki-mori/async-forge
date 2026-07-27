package com.phrolova.asyncforge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String taskType;
    private String payloadJson;
    private String status;
    private Integer retryCount;
    private Integer maxRetry;
    private String errorMessage;
    private String resultJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
