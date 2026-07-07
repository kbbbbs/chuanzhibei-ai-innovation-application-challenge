package com.cupk.aiacademy.agent.tool.notice;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.pojo.Notice;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class NoticeSearchTool implements AgentTool {
    private final NoticeMapper noticeMapper;
    private final AgentToolSupport support;

    public NoticeSearchTool(NoticeMapper noticeMapper, AgentToolSupport support) {
        this.noticeMapper = noticeMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "notice_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("announcement_search", "notice_query");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Page<Notice> page = new Page<>(1, context.maxItems());
        QueryWrapper<Notice> query = new QueryWrapper<>();
        applyVisibility(query);
        support.applyKeywordFilter(query, support.extractSearchTerms(context.normalizedQuery()),
                "title", "content", "notice_type");
        query.orderByDesc("create_time").orderByDesc("id");
        noticeMapper.selectPage(page, query);
        List<Notice> notices = page.getRecords();
        if (notices == null || notices.isEmpty()) {
            return AgentToolResult.read("未查询到匹配通知。");
        }

        List<String> lines = new ArrayList<>();
        for (Notice notice : notices) {
            lines.add("通知ID=" + support.value(notice.getId())
                    + "；发布人ID=" + support.value(notice.getPublisherId())
                    + "；教学班ID=" + support.value(notice.getClassId())
                    + "；标题=" + support.value(notice.getTitle())
                    + "；类型=" + support.value(notice.getNoticeType())
                    + "；发布时间=" + support.value(notice.getCreateTime())
                    + "；状态=" + support.value(notice.getStatus())
                    + "；内容=" + support.summarize(notice.getContent(), 120));
        }
        return AgentToolResult.read(String.join("\n", lines));
    }

    private void applyVisibility(QueryWrapper<Notice> query) {
        AuthUtils.LoginUser user = UserContext.get();
        if (support.isAdmin(user)) {
            return;
        }
        if (support.isStudent(user)) {
            List<Integer> classIds = support.findVisibleClassIds();
            if (classIds.isEmpty()) {
                query.isNull("class_id");
            } else {
                query.and(wrapper -> wrapper.isNull("class_id").or().in("class_id", classIds));
            }
            return;
        }
        if (support.isTeacher(user)) {
            List<Integer> classIds = support.findVisibleClassIds();
            query.and(wrapper -> {
                wrapper.eq("publisher_id", user.getUserId()).or().isNull("class_id");
                if (!classIds.isEmpty()) {
                    wrapper.or().in("class_id", classIds);
                }
            });
            return;
        }
        query.eq("status", 1).isNull("class_id");
    }
}
