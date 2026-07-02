package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("t_exam_paper_question")
public class ExamPaperQuestion {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("paper_id")
    private Integer paperId;

    @TableField("question_id")
    private Integer questionId;

    private BigDecimal score;

    @TableField("sort_order")
    private Integer sortOrder;
}
