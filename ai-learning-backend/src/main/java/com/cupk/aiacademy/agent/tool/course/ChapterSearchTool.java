package com.cupk.aiacademy.agent.tool.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.pojo.Chapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ChapterSearchTool implements AgentTool {
    private final ChapterMapper chapterMapper;
    private final AgentToolSupport support;

    public ChapterSearchTool(ChapterMapper chapterMapper, AgentToolSupport support) {
        this.chapterMapper = chapterMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "chapter_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("chapter_query", "course_chapter_search");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Page<Chapter> page = new Page<>(1, context.maxItems());
        QueryWrapper<Chapter> query = new QueryWrapper<>();
        if (!support.applyChapterVisibility(query)) {
            return AgentToolResult.read("没有可查询的章节范围。");
        }
        support.applyKeywordFilter(query, support.extractSearchTerms(context.normalizedQuery()),
                "title", "description");
        query.orderByAsc("course_id").orderByAsc("sort_order").orderByAsc("id");
        chapterMapper.selectPage(page, query);
        List<Chapter> chapters = page.getRecords();
        if (chapters == null || chapters.isEmpty()) {
            return AgentToolResult.read("未查询到匹配章节。");
        }

        List<String> lines = new ArrayList<>();
        for (Chapter chapter : chapters) {
            lines.add("章节ID=" + support.value(chapter.getId())
                    + "；课程ID=" + support.value(chapter.getCourseId())
                    + "；标题=" + support.value(chapter.getTitle())
                    + "；排序=" + support.value(chapter.getSortOrder())
                    + "；简介=" + support.summarize(chapter.getDescription(), 120));
        }
        return AgentToolResult.read(String.join("\n", lines));
    }
}
