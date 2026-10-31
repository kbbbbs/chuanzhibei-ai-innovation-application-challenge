package com.cupk.aiacademy.model;

/** Safe across API, logs and async wrappers; deliberately does not retain a provider cause/body. */
public class ModelInvocationException extends RuntimeException {
    private final String code;
    public ModelInvocationException(String code) {
        super(code+": 运行模型调用未完成，请检查接口状态。");
        this.code=code;
    }
    public String code(){ return code; }
}
