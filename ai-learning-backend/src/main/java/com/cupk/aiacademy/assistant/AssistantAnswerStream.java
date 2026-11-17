package com.cupk.aiacademy.assistant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads visible section strings from incomplete model JSON without exposing protocol fragments. */
final class AssistantAnswerStream {
    private static final Pattern SECTIONS = Pattern.compile("\\\"sections\\\"\\s*:\\s*\\[");
    private static final Set<String> TYPES = Set.of("COURSE_MATERIAL", "LESSON_CONTEXT", "GENERAL");

    private AssistantAnswerStream() {}

    static List<Map<String, Object>> sections(String raw, boolean materials, boolean contextual) {
        String input = raw.stripLeading().replaceFirst("^```(?:json)?\\s*", "");
        if (input.isEmpty()) return List.of();
        if (!input.startsWith("{")) {
            // Wait for a possible opening JSON fence before treating output as plain Markdown.
            if (input.startsWith("`") && !raw.contains("\n")) return List.of();
            return List.of(section(materials ? "COURSE_MATERIAL" : "GENERAL", input));
        }
        var start = SECTIONS.matcher(input);
        if (!start.find()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        int depth = 0;
        String type = "", content = "";
        for (int index = start.end(); index < input.length(); index++) {
            char current = input.charAt(index);
            if (current == '{' || current == '[') {
                depth++;
                if (depth == 1) {
                    type = "";
                    content = "";
                }
            } else if (current == '}' || current == ']') {
                if (depth == 1 && TYPES.contains(type) && !content.isBlank()) {
                    result.add(section(normalize(type, materials, contextual), content));
                    type = "";
                    content = "";
                }
                if (--depth < 0) break;
            } else if (current == '"') {
                var key = string(input, index);
                index = key.end();
                if (!key.closed()) break;
                int colon = whitespace(input, index + 1);
                if (depth != 1 || colon >= input.length() || input.charAt(colon) != ':') continue;
                int valueStart = whitespace(input, colon + 1);
                if (valueStart >= input.length() || input.charAt(valueStart) != '"') continue;
                var value = string(input, valueStart);
                index = value.end();
                if ("type".equals(key.value()) && value.closed()) type = value.value();
                if ("content".equals(key.value())) content = value.value();
                if (!value.closed()) break;
            }
        }
        if (TYPES.contains(type) && !content.isBlank())
            result.add(section(normalize(type, materials, contextual), content));
        return result;
    }

    private static Map<String, Object> section(String type, String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("content", content);
        return result;
    }

    private static String normalize(String type, boolean materials, boolean contextual) {
        if ("COURSE_MATERIAL".equals(type) && !materials) return "GENERAL";
        if ("LESSON_CONTEXT".equals(type) && !contextual) return "GENERAL";
        return type;
    }

    private static int whitespace(String input, int index) {
        while (index < input.length() && Character.isWhitespace(input.charAt(index))) index++;
        return index;
    }

    private static Decoded string(String input, int opening) {
        StringBuilder value = new StringBuilder();
        int index = opening + 1;
        for (; index < input.length(); index++) {
            char current = input.charAt(index);
            if (current == '"') return new Decoded(value.toString(), index, true);
            if (current != '\\') {
                value.append(current);
                continue;
            }
            if (++index >= input.length()) break;
            char escaped = input.charAt(index);
            if (escaped == 'u') {
                if (index + 4 >= input.length()) break;
                try {
                    value.append(
                            (char) Integer.parseInt(input.substring(index + 1, index + 5), 16));
                } catch (NumberFormatException invalid) {
                    break;
                }
                index += 4;
            } else {
                switch (escaped) {
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case '"', '\\', '/' -> value.append(escaped);
                    default -> {
                        return new Decoded(value.toString(), index, false);
                    }
                }
            }
        }
        if (!value.isEmpty() && Character.isHighSurrogate(value.charAt(value.length() - 1)))
            value.setLength(value.length() - 1);
        return new Decoded(value.toString(), input.length(), false);
    }

    private record Decoded(String value, int end, boolean closed) {}
}
