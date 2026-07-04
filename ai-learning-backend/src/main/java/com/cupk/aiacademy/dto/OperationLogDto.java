package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.OperationLog;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OperationLogDto {
    @Positive
    private Integer userId;

    @Size(max = 100)
    private String operation;

    @Size(max = 20)
    private String method;

    @Size(max = 255)
    private String url;

    private String params;

    @Size(max = 64)
    private String ip;

    public OperationLog toEntity() {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setOperation(operation);
        log.setMethod(method);
        log.setUrl(url);
        log.setParams(params);
        log.setIp(ip);
        return log;
    }
}
