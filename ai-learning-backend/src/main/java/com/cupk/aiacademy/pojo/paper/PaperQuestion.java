// 文件: src/main/java/com/cupk/aiacademy/pojo/paper/PaperQuestion.java
package com.cupk.aiacademy.pojo.paper;

import com.cupk.aiacademy.pojo.question.Question;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "t_paper_question")
public class PaperQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_id", nullable = false)
    private Paper paper;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal score;

    @Column(nullable = false)
    private Boolean requiredAnswer;
}
