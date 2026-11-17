package com.cupk.aiacademy.analysis;

import java.time.LocalDateTime;
import java.util.List;

public record LearningAnalysisSnapshot(
        int courseId, String courseTitle, String description, String category, String difficulty,
        String teacher, String semester, int chapterCount, int lessonCount, int studentCount,
        LocalDateTime capturedAt, List<ClassSummary> classes, List<Assessment> assessments,
        Stats homework, Stats exam) {

    public record ClassSummary(int id, String name, String semester, int status, int studentCount,
                               Stats homework, Stats exam) {}

    public record StudentResult(int studentId, String studentName, String status, Double score,
                                Double percentage, String submittedAt) {}

    public record Assessment(int id, int classId, String className, String type, String title,
                             Double fullScore, String startTime, String endTime,
                             List<StudentResult> students, Stats stats) {}

    public record Stats(int assessmentCount, int expectedCount, int submittedCount, int gradedCount,
                        int pendingCount, int inProgressCount, int notStartedCount, int invalidScoreCount,
                        Double submissionRate, Double averageScore, Double passRate,
                        Double highestScore, Double lowestScore, List<Integer> distribution) {
        public static Stats of(List<Assessment> assessments) {
            List<StudentResult> rows = assessments.stream().flatMap(a -> a.students().stream()).toList();
            List<Double> scores = rows.stream().map(StudentResult::percentage).filter(java.util.Objects::nonNull).toList();
            int submitted = (int) rows.stream().filter(r -> List.of("SUBMITTED", "PENDING_MANUAL_REVIEW", "GRADED").contains(r.status())).count();
            int pending = (int) rows.stream().filter(r -> List.of("SUBMITTED", "PENDING_MANUAL_REVIEW").contains(r.status())).count();
            int inProgress = (int) rows.stream().filter(r -> r.status().equals("IN_PROGRESS")).count();
            int notStarted = (int) rows.stream().filter(r -> r.status().equals("NOT_STARTED")).count();
            int invalid = (int) rows.stream().filter(r -> r.status().equals("GRADED") && r.percentage() == null).count();
            int[] buckets = new int[5];
            for (double score : scores) buckets[score < 60 ? 0 : score < 70 ? 1 : score < 80 ? 2 : score < 90 ? 3 : 4]++;
            return new Stats(assessments.size(), rows.size(), submitted, scores.size(), pending, inProgress,
                    notStarted, invalid, percent(submitted, rows.size()),
                    scores.isEmpty() ? null : round(scores.stream().mapToDouble(Double::doubleValue).average().orElseThrow()),
                    percent(scores.stream().filter(s -> s >= 60).count(), scores.size()),
                    scores.stream().max(Double::compare).orElse(null), scores.stream().min(Double::compare).orElse(null),
                    java.util.Arrays.stream(buckets).boxed().toList());
        }
        private static Double percent(long numerator, int denominator) {
            return denominator == 0 ? null : round(numerator * 100.0 / denominator);
        }
        private static double round(double value) { return Math.round(value * 100.0) / 100.0; }
    }
}
