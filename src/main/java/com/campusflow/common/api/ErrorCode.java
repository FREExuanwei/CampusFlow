package com.campusflow.common.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "请求参数校验失败"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "请求参数有误"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "请求体格式有误"),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "用户名或密码无效"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "请提供有效的访问令牌"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "没有访问该资源的权限"),
    USERNAME_CONFLICT(HttpStatus.CONFLICT, "用户名已存在"),
    CONFLICT(HttpStatus.CONFLICT, "资源状态冲突"),
    CAPACITY_FULL(HttpStatus.CONFLICT, "活动报名名额已满"),
    BUSINESS_ERROR(HttpStatus.UNPROCESSABLE_CONTENT, "业务处理失败"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "请求方法不受支持"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "请求的响应格式不受支持"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求内容类型不受支持"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
