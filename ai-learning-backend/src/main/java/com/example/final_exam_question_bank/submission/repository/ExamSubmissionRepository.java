package com.example.final_exam_question_bank.submission.repository;

import com.example.final_exam_question_bank.submission.entity.ExamSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, Long> {
    List<ExamSubmission> findByExamId(Long examId);
    List<ExamSubmission> findByCandidateId(String candidateId);
    List<ExamSubmission> findByExamIdAndCandidateId(Long examId, String candidateId);
}
