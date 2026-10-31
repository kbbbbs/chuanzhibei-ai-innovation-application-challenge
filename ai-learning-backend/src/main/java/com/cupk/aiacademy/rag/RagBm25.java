package com.cupk.aiacademy.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic lexical baseline: English words and overlapping Chinese bigrams. */
final class RagBm25 {
    private static final Pattern TERMS = Pattern.compile("[\\p{IsHan}]+|[a-zA-Z0-9_]+");
    private static final Set<String> STOP = Set.of("the", "a", "an", "and", "or", "is", "to", "of", "in", "what", "how", "什么", "怎么", "如何", "是否", "请问", "一下");

    static List<String> tokenize(String input) {
        List<String> terms = new ArrayList<>();
        Matcher matcher = TERMS.matcher(input.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (Character.UnicodeScript.of(token.codePointAt(0)) == Character.UnicodeScript.HAN) {
                int[] points = token.codePoints().toArray();
                if (points.length == 1) terms.add(token);
                else for (int i = 0; i < points.length - 1; i++) {
                    String pair = new String(points, i, 2);
                    if (!STOP.contains(pair)) terms.add(pair);
                }
            } else if (!STOP.contains(token)) terms.add(token);
        }
        return terms;
    }

    static double[] score(String query, List<String> documents) {
        Set<String> requested = new HashSet<>(tokenize(query));
        List<Map<String, Integer>> termFrequencies = new ArrayList<>();
        Map<String, Integer> documentFrequencies = new HashMap<>();
        int[] lengths = new int[documents.size()];
        double average = 0;
        for (int i = 0; i < documents.size(); i++) {
            List<String> tokens = tokenize(documents.get(i));
            lengths[i] = tokens.size();
            average += lengths[i];
            Map<String, Integer> frequencies = new HashMap<>();
            for (String term : tokens) if (requested.contains(term)) frequencies.merge(term, 1, Integer::sum);
            for (String term : frequencies.keySet()) documentFrequencies.merge(term, 1, Integer::sum);
            termFrequencies.add(frequencies);
        }
        average = documents.isEmpty() ? 1 : Math.max(1, average / documents.size());
        double[] scores = new double[documents.size()];
        for (int i = 0; i < documents.size(); i++) {
            for (String term : requested) {
                int tf = termFrequencies.get(i).getOrDefault(term, 0);
                if (tf == 0) continue;
                int df = documentFrequencies.getOrDefault(term, 0);
                double idf = Math.log(1 + (documents.size() - df + 0.5) / (df + 0.5));
                scores[i] += idf * (tf * 2.2) / (tf + 1.2 * (0.25 + 0.75 * lengths[i] / average));
            }
        }
        return scores;
    }
}
