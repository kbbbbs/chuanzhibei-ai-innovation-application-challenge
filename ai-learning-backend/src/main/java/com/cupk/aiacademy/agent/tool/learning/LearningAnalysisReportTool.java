package com.cupk.aiacademy.agent.tool.learning;

import com.cupk.aiacademy.analysis.LearningAnalysisSnapshotService;
import com.cupk.aiacademy.agent.tool.api.*;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LearningAnalysisReportTool implements AgentTool {
    private final LearningAnalysisSnapshotService snapshots;
    public LearningAnalysisReportTool(LearningAnalysisSnapshotService snapshots) { this.snapshots = snapshots; }
    @Override public String name() { return "learning_analysis_report"; }
    @Override public boolean promptContextEnabled() { return false; }
    @Override public AgentToolResult execute(AgentToolContext context) {
        Integer scope = com.cupk.aiacademy.agent.tool.support.AgentToolScope.courseId();
        Integer requested = context.integerArgument("courseId");
        int courseId = snapshots.resolveCourse(requested == null && context.stringArgument("courseTitle").isBlank() ? scope : requested,
                context.stringArgument("courseTitle"));
        if (scope != null && scope != courseId) throw new com.cupk.aiacademy.common.BusinessException("当前对话仅能分析本课程，请在对应课程或通用助手中生成报告");
        var snapshot = snapshots.capture(courseId);
        Integer target = context.integerArgument("targetWords");
        String template = context.stringArgument("templateId");
        return AgentToolResult.read(name(), "已获取课程全部教学班的学情统计，等待生成报告。",
                Map.of("snapshot", snapshot, "targetWords", target == null ? 1000 : target,
                        "templateId", template.isBlank() ? "default" : template));
    }
}
