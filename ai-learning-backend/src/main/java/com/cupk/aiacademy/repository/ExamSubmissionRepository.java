package com.cupk.aiacademy.repository;

import com.cupk.aiacademy.pojo.ExamSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, Long> {
    List<ExamSubmission> findByExamId(Long examId);
    List<ExamSubmission> findByCandidateId(String candidateId);
    List<ExamSubmission> findByExamIdAndCandidateId(Long examId, String candidateId);
}
