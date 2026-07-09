package com.cupk.aiacademy.agent.action;

public enum AgentActionParameterType {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    ARRAY("array"),
    OBJECT("object");

    private final String jsonSchemaType;

    AgentActionParameterType(String jsonSchemaType) {
        this.jsonSchemaType = jsonSchemaType;
    }

    public String jsonSchemaType() {
        return jsonSchemaType;
    }
}
