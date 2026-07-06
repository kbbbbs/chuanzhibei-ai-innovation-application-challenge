package com.cupk.aiacademy.agent.tool.exam;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.ExamRecordMapper;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.pojo.ExamRecord;
import com.cupk.aiacademy.pojo.ExamSession;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ExamRecordSearchTool implements AgentTool {
    private final ExamRecordMapper examRecordMapper;
    private final ExamSessionMapper examSessionMapper;
    private final AgentToolSupport support;

    public ExamRecordSearchTool(ExamRecordMapper examRecordMapper,
                                ExamSessionMapper examSessionMapper,
                                AgentToolSupport support) {
        this.examRecordMapper = examRecordMapper;
        this.examSessionMapper = examSessionMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "exam_record_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("exam_result_search", "exam_score_search");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Page<ExamRecord> page = new Page<>(1, context.maxItems());
        QueryWrapper<ExamRecord> query = new QueryWrapper<>();
        AuthUtils.LoginUser user = UserContext.get();
        if (support.isStudent(user)) {
            query.eq("student_id", user.getUserId());
        } else if (support.isTeacher(user)) {
            List<Integer> examSessionIds = findTeacherExamSessionIds(user.getUserId());
            if (examSessionIds.isEmpty()) {
                return AgentToolResult.read("当前教师没有可查询的考试记录范围。");
            }
            query.in("exam_session_id", examSessionIds);
        } else if (!support.isTeacher(user) && !support.isAdmin(user)) {
            return AgentToolResult.read("当前用户没有可查询的考试记录范围。");
        }
        query.orderByDesc("submit_time").orderByDesc("start_time").orderByDesc("id");
        examRecordMapper.selectPage(page, query);
        List<ExamRecord> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return AgentToolResult.read("未查询到匹配考试记录。");
        }

        List<String> lines = new ArrayList<>();
        for (ExamRecord record : records) {
            lines.add("考试记录ID=" + support.value(record.getId())
                    + "；考试ID=" + support.value(record.getExamSessionId())
                    + "；学生ID=" + support.value(record.getStudentId())
                    + "；开始=" + support.value(record.getStartTime())
                    + "；提交=" + support.value(record.getSubmitTime())
                    + "；总分=" + support.value(record.getTotalScore())
                    + "；客观题分=" + support.value(record.getObjectiveScore())
                    + "；主观题分=" + support.value(record.getSubjectiveScore())
                    + "；状态=" + support.value(record.getStatus())
                    + "；备注=" + support.summarize(record.getComment(), 100));
        }
        return AgentToolResult.read(String.join("\n", lines));
    }

    private List<Integer> findTeacherExamSessionIds(Integer teacherId) {
        QueryWrapper<ExamSession> query = new QueryWrapper<>();
        query.eq("teacher_id", teacherId);
        return examSessionMapper.selectList(query).stream()
                .map(ExamSession::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }
}
