package edu.course.library.exception;

import edu.course.library.dto.ApiResult;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 错误统一转中文消息，技术栈异常详情只写服务器日志，不回传数据库凭据/SQL。 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Object>> business(BusinessException ex) {
        log.warn("业务拒绝或回滚，错误码={}，说明={}",ex.errorCode(),ex.getMessage());
        return ResponseEntity.status(ex.status()).body(new ApiResult<>(ex.status().value(), ex.errorCode()+": "+ex.getMessage(), null));
    }
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResult<Object>> duplicate(DuplicateKeyException ex) {
        log.warn("数据库唯一约束拒绝了重复写入",ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResult<>(409,"DUPLICATE_KEY: 数据已存在或图书已被借出",null));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
                        HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResult<Object>> badInput(Exception ex) {
        return ResponseEntity.badRequest().body(new ApiResult<>(400,"请求参数格式不正确",null));
    }
    @ExceptionHandler({DataAccessException.class, RuntimeException.class})
    public ResponseEntity<ApiResult<Object>> unexpected(Exception ex) {
        log.error("事务执行异常，Spring 应回滚相关操作；请结合时间戳排查", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiResult<>(500,"服务器处理失败，请联系管理员",null));
    }
}
