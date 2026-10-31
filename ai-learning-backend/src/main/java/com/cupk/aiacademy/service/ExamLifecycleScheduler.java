package com.cupk.aiacademy.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExamLifecycleScheduler {
    private final ExamService examService;
    private final HomeworkAssessmentService homeworkAssessmentService;

    public ExamLifecycleScheduler(ExamService examService, HomeworkAssessmentService homeworkAssessmentService) {
        this.examService = examService;
        this.homeworkAssessmentService = homeworkAssessmentService;
    }

    @Scheduled(fixedDelay = 15000)
    public void synchronizeScheduledStatuses() {
        examService.synchronizeScheduledStatuses();
        homeworkAssessmentService.synchronizeScheduledStatuses();
    }
}
