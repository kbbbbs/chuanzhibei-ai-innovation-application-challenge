package com.cupk.aiacademy.agent.tool.exam;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.ExamPaperMapper;
import com.cupk.aiacademy.pojo.ExamPaper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ExamPaperSearchTool implements AgentTool {
    private final ExamPaperMapper examPaperMapper;
    private final AgentToolSupport support;

    public ExamPaperSearchTool(ExamPaperMapper examPaperMapper, AgentToolSupport support) {
        this.examPaperMapper = examPaperMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "exam_paper_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("exam_paper_query");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Page<ExamPaper> page = new Page<>(1, context.maxItems());
        QueryWrapper<ExamPaper> query = new QueryWrapper<>();
        AuthUtils.LoginUser user = UserContext.get();
        if (support.isStudent(user)) {
            List<Integer> courseIds = support.findVisibleCourseIds();
            if (courseIds.isEmpty()) {
                return AgentToolResult.read("没有可查询的试卷范围。");
            }
            query.in("course_id", courseIds);
        } else if (support.isTeacher(user)) {
            query.eq("teacher_id", user.getUserId());
        } else if (!support.isAdmin(user)) {
            query.eq("status", 1);
        }
        support.applyKeywordFilter(query, support.extractSearchTerms(context.normalizedQuery()),
                "title", "description");
        query.orderByDesc("id");
        examPaperMapper.selectPage(page, query);
        List<ExamPaper> papers = page.getRecords();
        if (papers == null || papers.isEmpty()) {
            return AgentToolResult.read("未查询到匹配试卷。");
        }

        List<String> lines = new ArrayList<>();
        for (ExamPaper paper : papers) {
            lines.add("试卷ID=" + support.value(paper.getId())
                    + "；课程ID=" + support.value(paper.getCourseId())
                    + "；教师ID=" + support.value(paper.getTeacherId())
                    + "；标题=" + support.value(paper.getTitle())
                    + "；总分=" + support.value(paper.getTotalScore())
                    + "；时长=" + support.value(paper.getDurationMinutes())
                    + "；状态=" + support.value(paper.getStatus())
                    + "；说明=" + support.summarize(paper.getDescription(), 100));
        }
        return AgentToolResult.read(String.join("\n", lines));
    }
}
