package com.cupk.aiacademy.model;

/** Stable, safe error: never contains endpoint, credential or provider response. */
public class ModelConfigurationException extends RuntimeException {
    private final String code;
    public ModelConfigurationException(String code) {
        super(code + ": 请检查对应运行模型配置；不会自动切换模型服务。");
        this.code = code;
    }
    public String code() { return code; }
}
