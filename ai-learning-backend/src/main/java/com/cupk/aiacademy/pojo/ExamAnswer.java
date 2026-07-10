package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_exam_answer")
public class ExamAnswer {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("exam_record_id")
    private Integer examRecordId;

    @TableField("question_id")
    private Integer questionId;

    @TableField("paper_question_id")
    private Integer paperQuestionId;

    @TableField("student_answer")
    private String studentAnswer;

    @TableField("correct_answer")
    private String correctAnswer;

    /** Immutable question payload captured when the student starts the assessment. */
    @TableField("question_snapshot")
    private String questionSnapshot;

    private BigDecimal score;

    @TableField("is_correct")
    private Integer isCorrect;

    @TableField("grade_status")
    private Integer gradeStatus;

    @TableField("grader_id")
    private Integer graderId;

    @TableField("grade_time")
    private LocalDateTime gradeTime;

    private String comment;
}
