package com.cupk.aiacademy.agent.tool.homework;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.pojo.Homework;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class HomeworkSearchTool implements AgentTool {
    private final HomeworkMapper homeworkMapper;
    private final AgentToolSupport support;

    public HomeworkSearchTool(HomeworkMapper homeworkMapper, AgentToolSupport support) {
        this.homeworkMapper = homeworkMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "homework_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("homework_query", "assignment_search");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Page<Homework> page = new Page<>(1, context.maxItems());
        QueryWrapper<Homework> query = new QueryWrapper<>();
        if (!support.applyHomeworkVisibility(query)) {
            return AgentToolResult.read("没有可查询的作业范围。");
        }
        support.applyKeywordFilter(query, support.extractSearchTerms(context.normalizedQuery()), "title", "content");
        query.orderByDesc("deadline").orderByDesc("id");
        homeworkMapper.selectPage(page, query);
        List<Homework> homeworks = page.getRecords();
        if (homeworks == null || homeworks.isEmpty()) {
            return AgentToolResult.read("未查询到匹配作业。");
        }

        AuthUtils.LoginUser user = UserContext.get();
        List<String> lines = new ArrayList<>();
        for (Homework homework : homeworks) {
            String submitStatus = support.buildSubmitStatus(homework.getId(), user);
            lines.add("作业ID=" + support.value(homework.getId())
                    + "；标题=" + support.value(homework.getTitle())
                    + "；教学班ID=" + support.value(homework.getClassId())
                    + "；截止时间=" + support.value(homework.getDeadline())
                    + "；满分=" + support.value(homework.getFullScore())
                    + "；提交情况=" + submitStatus
                    + "；内容=" + support.summarize(homework.getContent(), 120));
        }
        return AgentToolResult.read(String.join("\n", lines));
    }
}
