package com.phrolova.asyncforge.common;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(0, "success"),
    BAD_REQUEST(40000, "bad request"),
    UNAUTHORIZED(40100, "unauthorized"),
    FORBIDDEN(40300, "forbidden"),
    NOT_FOUND(40400, "resource not found"),
    CONFLICT(40900, "resource conflict"),
    RATE_LIMITED(42900, "too many requests"),
    INTERNAL_ERROR(50000, "internal server error"),
    TASK_NOT_FOUND(40401, "task not found"),
    TASK_TYPE_UNSUPPORTED(40001, "unsupported task type"),
    USERNAME_EXISTS(40901, "username already exists"),
    INVALID_CREDENTIALS(40101, "invalid username or password"),
    HTTP_CALL_BLOCKED(40002, "target URL is not allowed");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
