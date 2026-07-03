// 文件: src/main/java/com/cupk/aiacademy/repository/submission/ExamSubmissionRepository.java
package com.cupk.aiacademy.repository.submission;

import com.cupk.aiacademy.pojo.submission.ExamSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, Long> {
    List<ExamSubmission> findByExamId(Long examId);
    List<ExamSubmission> findByCandidateId(String candidateId);
    List<ExamSubmission> findByExamIdAndCandidateId(Long examId, String candidateId);
}
