package com.cupk.aiacademy.repository;

import com.cupk.aiacademy.pojo.SubmissionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionAnswerRepository extends JpaRepository<SubmissionAnswer, Long> {
    List<SubmissionAnswer> findBySubmissionId(Long submissionId);
    Optional<SubmissionAnswer> findBySubmissionIdAndPaperQuestionId(Long submissionId, Long paperQuestionId);
    boolean existsByPaperQuestionId(Long paperQuestionId);
}
