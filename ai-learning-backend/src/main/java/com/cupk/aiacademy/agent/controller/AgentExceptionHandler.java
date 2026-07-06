package com.cupk.aiacademy.agent.controller;

import com.cupk.aiacademy.agent.exception.AgentModelCallException;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Result;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {ChatController.class, AgentDefinitionController.class})
public class AgentExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result> handleHttpMessageNotReadable() {
        return error(HttpStatus.BAD_REQUEST, "400", "请求体不能为空或格式错误");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class, BindException.class})
    public ResponseEntity<Result> handleBadRequest() {
        return error(HttpStatus.BAD_REQUEST, "400", "请求参数错误");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, "400", message.isEmpty() ? "请求参数错误" : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result> handleConstraint(ConstraintViolationException e) {
        return error(HttpStatus.BAD_REQUEST, "400", e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result> handleBusiness(BusinessException e) {
        return error(HttpStatus.BAD_REQUEST, "400", e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result> handleResponseStatus(ResponseStatusException e) {
        HttpStatusCode status = e.getStatusCode();
        String code = String.valueOf(status.value());
        String message = e.getReason() == null || e.getReason().isBlank() ? "请求失败" : e.getReason();
        return error(status, code, message);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result> handleDuplicateKey() {
        return error(HttpStatus.CONFLICT, "409", "数据已存在，请勿重复提交");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result> handleDataIntegrityViolation() {
        return error(HttpStatus.CONFLICT, "409", "数据存在关联或违反完整性约束，操作失败");
    }

    @ExceptionHandler(AgentModelCallException.class)
    public ResponseEntity<Result> handleModelCall(AgentModelCallException e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", e.getSessionId());
        data.put("userMessageId", e.getUserMessageId());
        data.put("assistantMessageId", e.getAssistantMessageId());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("500", e.getMessage(), data));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleException(Exception e) {
        String message = e.getMessage() == null || e.getMessage().isBlank() ? "服务器内部错误" : e.getMessage();
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "500", message);
    }

    private ResponseEntity<Result> error(HttpStatusCode status, String code, String message) {
        return ResponseEntity.status(status).body(Result.error(code, message));
    }
}
