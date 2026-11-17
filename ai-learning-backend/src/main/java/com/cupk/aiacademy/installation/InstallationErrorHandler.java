package com.cupk.aiacademy.installation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestControllerAdvice(assignableTypes = InstallationController.class)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="installation.lifecycle.enabled",havingValue="true")
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class InstallationErrorHandler {
    @ExceptionHandler(InstallationException.class)
    public ResponseEntity<?> installation(InstallationException error) { return error(error.status(), error.code()); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> malformed() { return error(400, "REQUEST_INVALID"); }
    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public ResponseEntity<?> missingHeader() { return error(400, "REQUEST_HEADER_REQUIRED"); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalid(IllegalArgumentException error) {
        String code=safe(error.getMessage());return error("OPERATION_NOT_FOUND".equals(code)?404:400,code);
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> conflict(IllegalStateException error) { return error(409, safe(error.getMessage())); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception error) { return error(503, "INSTALLATION_TEMPORARILY_UNAVAILABLE"); }
    private String safe(String code) { return code != null && code.matches("[A-Z_]{3,80}") ? code : "REQUEST_INVALID"; }
    private ResponseEntity<?> error(int status, String code) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(Map.of(
                "code", String.valueOf(status), "msg", "操作未完成，请检查配置或授权",
                "data", Map.of("errorCode", code, "retryable", status == 503)));
    }
}
