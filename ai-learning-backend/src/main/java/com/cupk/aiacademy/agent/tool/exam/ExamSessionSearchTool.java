package com.cupk.aiacademy.agent.tool.exam;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.pojo.ExamSession;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ExamSessionSearchTool implements AgentTool {
    private final ExamSessionMapper examSessionMapper;
    private final AgentToolSupport support;

    public ExamSessionSearchTool(ExamSessionMapper examSessionMapper, AgentToolSupport support) {
        this.examSessionMapper = examSessionMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "exam_session_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("exam_search", "exam_schedule_search");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Page<ExamSession> page = new Page<>(1, context.maxItems());
        QueryWrapper<ExamSession> query = new QueryWrapper<>();
        AuthUtils.LoginUser user = UserContext.get();
        if (support.isStudent(user)) {
            List<Integer> classIds = support.findVisibleClassIds();
            if (classIds.isEmpty()) {
                return AgentToolResult.read("没有可查询的考试范围。");
            }
            query.in("class_id", classIds);
        } else if (support.isTeacher(user)) {
            query.eq("teacher_id", user.getUserId());
        } else if (!support.isAdmin(user)) {
            query.eq("status", 1);
        }
        support.applyKeywordFilter(query, support.extractSearchTerms(context.normalizedQuery()),
                "title", "description");
        query.orderByDesc("start_time").orderByDesc("id");
        examSessionMapper.selectPage(page, query);
        List<ExamSession> sessions = page.getRecords();
        if (sessions == null || sessions.isEmpty()) {
            return AgentToolResult.read("未查询到匹配考试。");
        }

        List<String> lines = new ArrayList<>();
        for (ExamSession session : sessions) {
            lines.add("考试ID=" + support.value(session.getId())
                    + "；教学班ID=" + support.value(session.getClassId())
                    + "；试卷ID=" + support.value(session.getPaperId())
                    + "；标题=" + support.value(session.getTitle())
                    + "；开始=" + support.value(session.getStartTime())
                    + "；结束=" + support.value(session.getEndTime())
                    + "；时长=" + support.value(session.getDuration())
                    + "；状态=" + support.value(session.getStatus()));
        }
        return AgentToolResult.read(String.join("\n", lines));
    }
}
