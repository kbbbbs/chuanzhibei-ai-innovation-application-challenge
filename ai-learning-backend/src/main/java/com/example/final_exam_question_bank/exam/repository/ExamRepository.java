package com.example.final_exam_question_bank.exam.repository;

import com.example.final_exam_question_bank.exam.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRepository extends JpaRepository<Exam, Long> {
}
