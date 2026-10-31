package com.cupk.aiacademy.rag.chat;

import com.cupk.aiacademy.rag.RagSearchHit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates source identifiers, not the factual correctness of every model assertion. */
public record GroundedAnswer(String answer, List<RagSearchHit> citations, String status) {
    private static final Pattern LABEL = Pattern.compile("\\[(\\d+)]");

    public static GroundedAnswer validate(String text, List<RagSearchHit> provided) {
        String answer = text == null ? "" : text.trim();
        if (provided == null || provided.isEmpty()) {
            return new GroundedAnswer(answer, List.of(), "GENERAL_ANSWER");
        }
        Set<Integer> used = new LinkedHashSet<>();
        StringBuilder outsideMath=new StringBuilder(answer);
        for(var formula:com.cupk.aiacademy.rag.formula.FormulaText.analyze(answer).blocks())
            for(int i=formula.start();i<formula.end();i++)outsideMath.setCharAt(i,' ');
        var matcher = LABEL.matcher(outsideMath);
        boolean invalid = answer.isBlank();
        while (matcher.find()) {
            if (matcher.group(1).length() > 9) { invalid = true; continue; }
            int index = Integer.parseInt(matcher.group(1));
            if (index < 1 || index > provided.size()) invalid = true;
            else used.add(index);
        }
        if (invalid || used.isEmpty()) {
            StringBuilder safe = new StringBuilder("模型回答未通过引用编号检查，以下为本次检索到的资料摘录，供你核验：\n\n");
            for (int i = 0; i < Math.min(3, provided.size()); i++) {
                RagSearchHit hit = provided.get(i);
                safe.append('[').append(i + 1).append("] ").append(hit.title())
                        .append(hit.page() == null ? "（片段 " + hit.ordinal() + "）\n" : "（第 " + hit.page() + " 页）\n")
                        .append(com.cupk.aiacademy.rag.formula.FormulaText.safePrefix(hit.content(),500)).append("\n\n");
            }
            return new GroundedAnswer(safe.toString().trim(), List.copyOf(provided.subList(0, Math.min(3, provided.size()))),
                    "CITATION_CHECK_FAILED");
        }
        List<RagSearchHit> referenced = new ArrayList<>();
        for (int i = 0; i < provided.size(); i++) if (used.contains(i + 1)) referenced.add(provided.get(i));
        return new GroundedAnswer(answer, List.copyOf(referenced), "ANSWERED");
    }
}
