// 文件: src/main/java/com/cupk/aiacademy/repository/submission/SubmissionAnswerRepository.java
package com.cupk.aiacademy.repository.submission;

import com.cupk.aiacademy.pojo.submission.SubmissionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionAnswerRepository extends JpaRepository<SubmissionAnswer, Long> {
    List<SubmissionAnswer> findBySubmissionId(Long submissionId);
    Optional<SubmissionAnswer> findBySubmissionIdAndPaperQuestionId(Long submissionId, Long paperQuestionId);
    boolean existsByPaperQuestionId(Long paperQuestionId);
}
