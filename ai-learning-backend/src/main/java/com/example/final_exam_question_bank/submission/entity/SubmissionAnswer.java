package com.example.final_exam_question_bank.submission.entity;

import com.example.final_exam_question_bank.common.JudgeStatus;
import com.example.final_exam_question_bank.paper.entity.PaperQuestion;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "submission_answer")
public class SubmissionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private ExamSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_question_id", nullable = false)
    private PaperQuestion paperQuestion;

    @Column(length = 2000)
    private String answerContent;

    private Boolean correct;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JudgeStatus judgeStatus;

    @Column(length = 1000)
    private String comment;
}
