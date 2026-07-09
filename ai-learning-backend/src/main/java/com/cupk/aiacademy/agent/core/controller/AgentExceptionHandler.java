package com.cupk.aiacademy.agent.core.controller;

import com.cupk.aiacademy.agent.action.AgentActionController;
import com.cupk.aiacademy.agent.core.exception.AgentModelCallException;
import com.cupk.aiacademy.agent.definition.controller.AgentDefinitionController;
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

/**
 * Agent 模块专用异常处理器。
 * <p>
 * 当前项目中部分兼容接口会在全局异常处理里返回 ApiResponse。
 * Agent 接口成功响应已经使用 Result，因此这里单独绑定 Agent controller，
 * 保证 /api/agent/** 的成功和失败响应结构一致，同时避免影响其他团队维护的全局模块。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {ChatController.class, AgentModelController.class, AgentDefinitionController.class, AgentActionController.class})
public class AgentExceptionHandler {

    /**
     * 请求体缺失、JSON 格式错误等场景。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result> handleHttpMessageNotReadable() {
        return error(HttpStatus.BAD_REQUEST, "400", "请求体不能为空或格式错误");
    }

    /**
     * 路径参数、查询参数类型错误或缺少必填参数。
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class, BindException.class})
    public ResponseEntity<Result> handleBadRequest() {
        return error(HttpStatus.BAD_REQUEST, "400", "请求参数错误");
    }

    /**
     * {@code @Valid} 校验失败时返回具体字段错误，便于前端直接展示。
     */
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

    /**
     * Agent 业务规则失败，例如会话不存在、消息内容为空。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result> handleBusiness(BusinessException e) {
        return error(HttpStatus.BAD_REQUEST, "400", e.getMessage());
    }

    /**
     * 保留 controller 主动抛出的 HTTP 状态，例如 /chat 调试接口的 400。
     */
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

    /**
     * 模型调用失败时返回 500，同时把已落库的失败消息 ID 返回给前端。
     */
    @ExceptionHandler(AgentModelCallException.class)
    public ResponseEntity<Result> handleModelCall(AgentModelCallException e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", e.getSessionId());
        data.put("userMessageId", e.getUserMessageId());
        data.put("assistantMessageId", e.getAssistantMessageId());
        if (e.getDiagnostics() != null) {
            data.put("diagnostics", e.getDiagnostics().toData());
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("500", e.getMessage(), data));
    }

    /**
     * 兜底异常，避免 Agent 接口退回全局 /api 兼容响应格式。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleException(Exception e) {
        String message = e.getMessage() == null || e.getMessage().isBlank() ? "服务器内部错误" : e.getMessage();
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "500", message);
    }

    private ResponseEntity<Result> error(HttpStatusCode status, String code, String message) {
        return ResponseEntity.status(status).body(Result.error(code, message));
    }
}
