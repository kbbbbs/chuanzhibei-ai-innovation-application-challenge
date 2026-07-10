package com.cupk.aiacademy.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("t_homework_paper_question")
public class HomeworkPaperQuestion {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("paper_id")
    private Integer paperId;

    @TableField("question_id")
    private Integer questionId;

    private BigDecimal score;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("required_answer")
    private Integer requiredAnswer;
}
