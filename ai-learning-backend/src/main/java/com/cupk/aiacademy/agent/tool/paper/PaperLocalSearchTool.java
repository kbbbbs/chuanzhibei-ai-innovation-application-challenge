package com.cupk.aiacademy.agent.tool.paper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.entity.Paper;
import com.cupk.aiacademy.mapper.PaperMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class PaperLocalSearchTool implements AgentTool {
    private final PaperMapper paperMapper;
    private final AgentToolSupport support;

    public PaperLocalSearchTool(PaperMapper paperMapper, AgentToolSupport support) {
        this.paperMapper = paperMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "paper_local_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("paper_search", "academic_paper_search");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Page<Paper> page = new Page<>(1, context.maxItems());
        QueryWrapper<Paper> query = new QueryWrapper<>();
        support.applyKeywordFilter(query, support.extractSearchTerms(context.normalizedQuery()),
                "title", "abstract_text", "venue", "source");
        query.orderByDesc("year").orderByDesc("citation_count").orderByDesc("id");
        paperMapper.selectPage(page, query);
        List<Paper> papers = page.getRecords();
        if (papers == null || papers.isEmpty()) {
            return AgentToolResult.read("未查询到匹配论文。");
        }

        List<String> lines = new ArrayList<>();
        for (Paper paper : papers) {
            lines.add("论文ID=" + support.value(paper.getId())
                    + "；标题=" + support.value(paper.getTitle())
                    + "；年份=" + support.value(paper.getYear())
                    + "；来源=" + support.value(paper.getSource())
                    + "；会议/期刊=" + support.value(paper.getVenue())
                    + "；引用数=" + support.value(paper.getCitationCount())
                    + "；链接=" + support.value(paper.getUrl())
                    + "；摘要=" + support.summarize(paper.getAbstractText(), 160));
        }
        return AgentToolResult.read(String.join("\n", lines));
    }
}
