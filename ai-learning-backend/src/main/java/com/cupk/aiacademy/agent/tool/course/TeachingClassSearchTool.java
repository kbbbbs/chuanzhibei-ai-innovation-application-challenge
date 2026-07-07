package com.cupk.aiacademy.agent.tool.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class TeachingClassSearchTool implements AgentTool {
    private final TeachingClassMapper teachingClassMapper;
    private final AgentToolSupport support;

    public TeachingClassSearchTool(TeachingClassMapper teachingClassMapper, AgentToolSupport support) {
        this.teachingClassMapper = teachingClassMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "teaching_class_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("class_search", "teaching_class_query");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Page<TeachingClass> page = new Page<>(1, context.maxItems());
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        if (!support.applyTeachingClassVisibility(query)) {
            return AgentToolResult.read("没有可查询的教学班范围。");
        }
        support.applyKeywordFilter(query, support.extractSearchTerms(context.normalizedQuery()),
                "class_name", "semester", "description");
        query.orderByDesc("id");
        teachingClassMapper.selectPage(page, query);
        List<TeachingClass> classes = page.getRecords();
        if (classes == null || classes.isEmpty()) {
            return AgentToolResult.read("未查询到匹配教学班。");
        }

        List<String> lines = new ArrayList<>();
        for (TeachingClass teachingClass : classes) {
            lines.add("教学班ID=" + support.value(teachingClass.getId())
                    + "；课程ID=" + support.value(teachingClass.getCourseId())
                    + "；教师ID=" + support.value(teachingClass.getTeacherId())
                    + "；班级名称=" + support.value(teachingClass.getClassName())
                    + "；学期=" + support.value(teachingClass.getSemester())
                    + "；状态=" + support.value(teachingClass.getStatus())
                    + "；说明=" + support.summarize(teachingClass.getDescription(), 100));
        }
        return AgentToolResult.read(String.join("\n", lines));
    }
}
