package com.cupk.aiacademy.agent.tool.learning;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.GradeRecordMapper;
import com.cupk.aiacademy.pojo.GradeRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class GradeRecordSearchTool implements AgentTool {
    private final GradeRecordMapper gradeRecordMapper;
    private final AgentToolSupport support;

    public GradeRecordSearchTool(GradeRecordMapper gradeRecordMapper, AgentToolSupport support) {
        this.gradeRecordMapper = gradeRecordMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "grade_record_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("grade_search", "score_search");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Page<GradeRecord> page = new Page<>(1, context.maxItems());
        QueryWrapper<GradeRecord> query = new QueryWrapper<>();
        AuthUtils.LoginUser user = UserContext.get();
        if (support.isStudent(user)) {
            query.eq("student_id", user.getUserId());
        } else if (support.isTeacher(user)) {
            query.eq("teacher_id", user.getUserId());
        } else if (!support.isAdmin(user)) {
            return AgentToolResult.read("当前用户没有可查询的成绩范围。");
        }
        support.applyKeywordFilter(query, support.extractSearchTerms(context.normalizedQuery()),
                "grade_name", "source_type", "grade_comment");
        query.orderByDesc("create_time").orderByDesc("id");
        gradeRecordMapper.selectPage(page, query);
        List<GradeRecord> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return AgentToolResult.read("未查询到匹配成绩记录。");
        }

        List<String> lines = new ArrayList<>();
        for (GradeRecord record : records) {
            lines.add("成绩ID=" + support.value(record.getId())
                    + "；学生ID=" + support.value(record.getStudentId())
                    + "；课程ID=" + support.value(record.getCourseId())
                    + "；教学班ID=" + support.value(record.getClassId())
                    + "；名称=" + support.value(record.getGradeName())
                    + "；来源=" + support.value(record.getSourceType())
                    + "；分数=" + support.value(record.getScore())
                    + "；满分=" + support.value(record.getFullScore())
                    + "；评语=" + support.summarize(record.getComment(), 100));
        }
        return AgentToolResult.read(String.join("\n", lines));
    }
}
