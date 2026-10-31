package com.cupk.aiacademy.teacherapply;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_teacher_application")
public class TeacherApplication {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private String reason;
    private Integer status;
    @TableField("reviewer_id")
    private Integer reviewerId;
    @TableField("review_comment")
    private String reviewComment;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("review_time")
    private LocalDateTime reviewTime;
}
