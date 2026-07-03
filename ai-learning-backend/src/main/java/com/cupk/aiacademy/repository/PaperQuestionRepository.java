package com.cupk.aiacademy.repository;

import com.cupk.aiacademy.pojo.PaperQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaperQuestionRepository extends JpaRepository<PaperQuestion, Long> {
    List<PaperQuestion> findByPaperIdOrderBySortOrderAsc(Long paperId);
}
