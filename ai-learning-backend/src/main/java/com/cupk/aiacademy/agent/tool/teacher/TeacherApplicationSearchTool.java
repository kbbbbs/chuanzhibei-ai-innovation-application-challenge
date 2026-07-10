package com.cupk.aiacademy.agent.tool.teacher;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.teacherapply.TeacherApplication;
import com.cupk.aiacademy.teacherapply.TeacherApplicationMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TeacherApplicationSearchTool implements AgentTool {
    private final TeacherApplicationMapper applicationMapper;
    private final AgentToolSupport support;

    public TeacherApplicationSearchTool(TeacherApplicationMapper applicationMapper,
                                        AgentToolSupport support) {
        this.applicationMapper = applicationMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "teacher_application_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("teacher_apply_search", "teacher_application_query");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        if (!support.isAdmin(UserContext.get())) {
            return AgentToolResult.failure(name(), declaredOperationType(), "只有管理员可以查询教师申请。");
        }

        QueryWrapper<TeacherApplication> query = new QueryWrapper<>();
        Integer status = context.integerArgument("status");
        if (status != null) {
            query.eq("status", status);
        }
        String keyword = context.stringArgument("keyword");
        if (!keyword.isBlank()) {
            query.and(wrapper -> wrapper.like("username", keyword)
                    .or().like("nickname", keyword)
                    .or().like("email", keyword)
                    .or().like("phone", keyword)
                    .or().like("reason", keyword)
                    .or().like("review_comment", keyword));
        }
        query.orderByDesc("id").last("LIMIT " + context.maxItems());
        List<TeacherApplication> applications = applicationMapper.selectList(query);
        if (applications == null || applications.isEmpty()) {
            return AgentToolResult.read(name(), "未查询到匹配教师申请。", Map.of("count", 0));
        }

        String content = applications.stream()
                .map(application -> "教师申请ID=" + support.value(application.getId())
                        + "；用户名=" + support.value(application.getUsername())
                        + "；昵称=" + support.value(application.getNickname())
                        + "；邮箱=" + support.value(application.getEmail())
                        + "；手机号=" + support.value(application.getPhone())
                        + "；状态=" + statusLabel(application.getStatus())
                        + "；申请时间=" + support.value(application.getCreateTime())
                        + "；原因=" + support.summarize(application.getReason(), 100)
                        + "；审核意见=" + support.summarize(application.getReviewComment(), 80))
                .collect(Collectors.joining("\n"));
        return AgentToolResult.read(name(), content, Map.of("count", applications.size()));
    }

    private String statusLabel(Integer status) {
        if (status == null) {
            return "无";
        }
        return switch (status) {
            case 0 -> "待审核#0";
            case 1 -> "通过#1";
            case 2 -> "拒绝#2";
            default -> status.toString();
        };
    }
}
