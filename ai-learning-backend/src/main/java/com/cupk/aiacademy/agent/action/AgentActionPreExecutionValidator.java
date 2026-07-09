package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.common.BusinessException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class AgentActionPreExecutionValidator {
    private static final Set<String> METADATA_KEYS = Set.of(
            "rawRequest",
            "requiredFields",
            "requiredConstraints",
            "requiredAnyOf",
            "optionalFields",
            "missingRequiredFields",
            "parameterSchema",
            "jsonSchema",
            "parameterExtraction"
    );

    private final AgentActionCatalog actionCatalog;
    private final AgentActionSchemaValidator schemaValidator;
    private final AgentEntityResolutionService entityResolutionService;

    public AgentActionPreExecutionValidator(AgentActionCatalog actionCatalog,
                                            AgentActionSchemaValidator schemaValidator,
                                            AgentEntityResolutionService entityResolutionService) {
        this.actionCatalog = actionCatalog;
        this.schemaValidator = schemaValidator;
        this.entityResolutionService = entityResolutionService;
    }

    public Map<String, Object> validateAndNormalize(String actionCode, Map<String, Object> parameters) {
        Map<String, Object> executableParameters = executableParameters(parameters);
        Optional<AgentActionSpec> optionalSpec = actionCatalog == null
                ? Optional.empty()
                : actionCatalog.findByActionCode(actionCode);
        if (optionalSpec.isEmpty()) {
            return executableParameters;
        }

        Map<String, Object> resolvedParameters = entityResolutionService == null
                ? executableParameters
                : entityResolutionService.resolve(actionCode, executableParameters);
        AgentActionParameterValidationResult validation = schemaValidator.validate(optionalSpec.get(), resolvedParameters);
        if (!validation.issues().isEmpty() || !validation.missingRequiredFields().isEmpty()) {
            throw new BusinessException(validationMessage(validation));
        }
        return validation.parameters();
    }

    private Map<String, Object> executableParameters(Map<String, Object> parameters) {
        Map<String, Object> executable = new LinkedHashMap<>();
        if (parameters == null || parameters.isEmpty()) {
            return executable;
        }
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getKey() == null || METADATA_KEYS.contains(entry.getKey())) {
                continue;
            }
            executable.put(entry.getKey(), entry.getValue());
        }
        return executable;
    }

    private String validationMessage(AgentActionParameterValidationResult validation) {
        StringBuilder builder = new StringBuilder("参数校验失败");
        List<String> missing = validation.missingRequiredFields();
        if (!missing.isEmpty()) {
            builder.append("，缺少：").append(String.join("、", missing));
        }
        if (!validation.issues().isEmpty()) {
            builder.append("，问题：");
            for (int i = 0; i < validation.issues().size(); i++) {
                if (i > 0) {
                    builder.append("；");
                }
                AgentActionValidationIssue issue = validation.issues().get(i);
                builder.append(issue.field()).append(" ").append(issue.message());
            }
        }
        return builder.toString();
    }
}
