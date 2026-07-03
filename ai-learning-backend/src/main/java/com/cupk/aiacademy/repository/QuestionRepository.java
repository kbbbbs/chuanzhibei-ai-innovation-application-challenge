package com.cupk.aiacademy.repository;

import com.cupk.aiacademy.common.Difficulty;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.RecordStatus;
import com.cupk.aiacademy.pojo.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    Page<Question> findByTypeAndDifficultyAndStatus(QuestionType type, Difficulty difficulty, RecordStatus status, Pageable pageable);
    Page<Question> findByTypeAndDifficulty(QuestionType type, Difficulty difficulty, Pageable pageable);
    Page<Question> findByType(QuestionType type, Pageable pageable);
    Page<Question> findByDifficulty(Difficulty difficulty, Pageable pageable);
    Page<Question> findByStatus(RecordStatus status, Pageable pageable);
}
