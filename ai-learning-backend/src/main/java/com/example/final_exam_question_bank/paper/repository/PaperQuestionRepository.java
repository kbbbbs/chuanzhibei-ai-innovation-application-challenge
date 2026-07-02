package com.example.final_exam_question_bank.paper.repository;

import com.example.final_exam_question_bank.paper.entity.PaperQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaperQuestionRepository extends JpaRepository<PaperQuestion, Long> {
    List<PaperQuestion> findByPaperIdOrderBySortOrderAsc(Long paperId);
}
