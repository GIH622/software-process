package edu.course.library.exception;

import org.springframework.http.HttpStatus;

/** 业务拒绝原因可追踪；与非预期数据库/程序异常区别处理。 */
public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;
    public BusinessException(HttpStatus status, String errorCode, String message) {
        super(message); this.status = status; this.errorCode = errorCode;
    }
    public HttpStatus status() { return status; }
    public String errorCode() { return errorCode; }
}
