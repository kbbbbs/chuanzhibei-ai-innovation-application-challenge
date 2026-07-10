package com.cupk.aiacademy.agent.tool.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.OperationLogMapper;
import com.cupk.aiacademy.pojo.OperationLog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ImportLogSearchTool implements AgentTool {
    private final OperationLogMapper operationLogMapper;
    private final AgentToolSupport support;

    public ImportLogSearchTool(OperationLogMapper operationLogMapper,
                               AgentToolSupport support) {
        this.operationLogMapper = operationLogMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "import_log_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("import_status_search", "import_history_search");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        AuthUtils.LoginUser user = UserContext.get();
        if (!support.isAdmin(user) && !support.isTeacher(user)) {
            return AgentToolResult.failure(name(), declaredOperationType(), "只有管理员或教师可以查询导入记录。");
        }

        QueryWrapper<OperationLog> query = new QueryWrapper<>();
        if (support.isTeacher(user)) {
            query.eq("user_id", user.getUserId());
        }
        query.and(wrapper -> wrapper.like("url", "/api/imports").or().like("operation", "import"));
        Integer httpStatus = context.integerArgument("httpStatus");
        if (httpStatus != null) {
            query.like("params", "\"status\":" + httpStatus);
        }
        String keyword = context.stringArgument("keyword");
        if (!keyword.isBlank()) {
            query.and(wrapper -> wrapper.like("operation", keyword)
                    .or().like("url", keyword)
                    .or().like("params", keyword));
        }
        query.orderByDesc("id").last("LIMIT " + context.maxItems());
        List<OperationLog> logs = operationLogMapper.selectList(query);
        if (logs == null || logs.isEmpty()) {
            return AgentToolResult.read(name(), "未查询到导入相关记录。", Map.of("count", 0));
        }

        String content = logs.stream()
                .map(log -> "操作日志ID=" + support.value(log.getId())
                        + "；用户ID=" + support.value(log.getUserId())
                        + "；操作=" + support.value(log.getOperation())
                        + "；方法=" + support.value(log.getMethod())
                        + "；地址=" + support.value(log.getUrl())
                        + "；时间=" + support.value(log.getCreateTime())
                        + "；摘要=" + support.summarize(log.getParams(), 160))
                .collect(Collectors.joining("\n"));
        return AgentToolResult.read(name(), content, Map.of("count", logs.size()));
    }
}
