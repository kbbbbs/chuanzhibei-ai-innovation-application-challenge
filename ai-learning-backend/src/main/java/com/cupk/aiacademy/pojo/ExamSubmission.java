package com.cupk.aiacademy.pojo;

import com.cupk.aiacademy.common.SubmissionStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "t_exam_submission")
public class ExamSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnoreProperties({"paper"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(nullable = false, length = 64)
    private String candidateId;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime submittedAt;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal objectiveScore;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subjectiveScore;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubmissionStatus status;

    @Column(length = 1000)
    private String comment;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubmissionAnswer> answers = new ArrayList<>();
}
