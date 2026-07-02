package com.example.final_exam_question_bank.paper.repository;

import com.example.final_exam_question_bank.paper.entity.Paper;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperRepository extends JpaRepository<Paper, Long> {
}
