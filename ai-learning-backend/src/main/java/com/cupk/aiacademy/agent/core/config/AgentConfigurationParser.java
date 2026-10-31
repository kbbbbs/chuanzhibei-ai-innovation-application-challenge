package com.cupk.aiacademy.agent.core.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses runtime Agent configuration stored in t_agent.
 * <p>
 * Definition writes still validate configuration strictly. Runtime parsing is
 * intentionally tolerant so a bad saved value does not break a user chat.
 */
public class AgentConfigurationParser {
    private static final Logger log = LoggerFactory.getLogger(AgentConfigurationParser.class);

    private final ObjectMapper objectMapper;

    public AgentConfigurationParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public List<String> parseEnabledTools(String value) {
        return parseJsonArrayStrings(value, "enabledTools");
    }

    public List<String> parseJsonArrayStrings(String value, String fieldName) {
        if (isBlank(value)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(value.trim());
            if (!node.isArray()) {
                return List.of();
            }

            List<String> items = new ArrayList<>();
            for (JsonNode item : node) {
                if (!item.isNull()) {
                    String text = item.asText();
                    if (!isBlank(text)) {
                        items.add(text.trim());
                    }
                }
            }
            return items;
        } catch (JsonProcessingException e) {
            log.warn("Invalid {} stored in Agent definition: {}", fieldName, value);
            return List.of();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
