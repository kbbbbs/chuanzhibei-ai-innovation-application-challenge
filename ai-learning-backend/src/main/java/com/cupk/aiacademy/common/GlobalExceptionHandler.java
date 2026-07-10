package com.cupk.aiacademy.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        return badRequest("请求体不能为空或格式错误", request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class, BindException.class})
    public ResponseEntity<Object> handleBadRequest(Exception e, HttpServletRequest request) {
        return badRequest("请求参数错误", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return badRequest(message.isEmpty() ? "请求参数错误" : message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraint(ConstraintViolationException e, HttpServletRequest request) {
        return badRequest(e.getMessage(), request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusiness(BusinessException e, HttpServletRequest request) {
        return badRequest(e.getMessage(), request);
    }

    @ExceptionHandler(LegacyHomeworkWriteException.class)
    public ResponseEntity<Object> handleLegacyHomeworkWrite(LegacyHomeworkWriteException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).body(body("410", e.getMessage(), request));
    }

    @ExceptionHandler(PaperPreflightWarningException.class)
    public ResponseEntity<ApiResponse<com.cupk.aiacademy.dto.PaperPreflightResponse>> handlePaperPreflightWarning(
            PaperPreflightWarningException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiResponse<>(false, e.getDiagnostics(), e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Object> handleForbidden(ForbiddenException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body("403", e.getMessage(), request));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Object> handleNotFound(Exception e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body("404", "接口不存在", request));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Object handleDuplicateKey(DuplicateKeyException e, HttpServletRequest request) {
        return conflict("数据已存在，请勿重复提交", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleDataIntegrityViolation(DataIntegrityViolationException e, HttpServletRequest request) {
        return conflict("数据存在关联或违反完整性约束，操作失败", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception e, HttpServletRequest request) {
        String message = e.getMessage() == null || e.getMessage().isBlank() ? "服务器内部错误" : e.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body("500", message, request));
    }

    private ResponseEntity<Object> badRequest(String message, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body("400", message, request));
    }

    private Object conflict(String message, HttpServletRequest request) {
        return body("409", message, request);
    }

    private Object body(String code, String message, HttpServletRequest request) {
        if (request != null && request.getRequestURI() != null && request.getRequestURI().startsWith("/api/")) {
            return ApiResponse.failure(message);
        }
        return Result.error(code, message);
    }
}
