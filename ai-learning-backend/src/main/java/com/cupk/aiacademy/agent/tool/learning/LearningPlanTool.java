package com.cupk.aiacademy.agent.tool.learning;

import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.pojo.LearningProgress;
import com.cupk.aiacademy.pojo.Lesson;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class LearningPlanTool implements AgentTool {
    private final AgentToolSupport support;

    public LearningPlanTool(AgentToolSupport support) {
        this.support = support;
    }

    @Override
    public String name() {
        return "learning_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("study_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        AuthUtils.LoginUser user = UserContext.get();
        if (!support.isStudent(user)) {
            return AgentToolResult.read("学习计划工具需要学生账号的个人学习进度；当前用户没有可生成计划的个人进度。");
        }
        List<LearningProgress> progressList = support.listStudentProgress(user.getUserId(), Math.max(context.maxItems(), 10));
        if (progressList.isEmpty()) {
            return AgentToolResult.read("暂无学习进度记录；建议先选择一门课程完成首个课时，并每天安排 30 分钟学习。");
        }

        int totalProgress = 0;
        int unfinishedCount = 0;
        List<String> weakLessons = new ArrayList<>();
        for (LearningProgress progress : progressList) {
            int value = progress.getProgress() == null ? 0 : progress.getProgress();
            totalProgress += value;
            if (value < 100) {
                unfinishedCount++;
            }
            if (value < 60) {
                Lesson lesson = support.safeLesson(progress.getLessonId());
                weakLessons.add(support.value(lesson == null ? progress.getLessonId() : lesson.getTitle()) + "(" + value + "%)");
            }
        }
        int averageProgress = totalProgress / progressList.size();
        String focus = weakLessons.isEmpty() ? "暂无明显薄弱课时" : String.join("、", weakLessons);

        return AgentToolResult.read("学习记录数=" + progressList.size()
                + "；平均进度=" + averageProgress + "%"
                + "；未完成课时数=" + unfinishedCount
                + "；优先复习=" + focus
                + "；建议：先补齐低于 60% 的课时，再完成未达 100% 的课时；每天安排 30-45 分钟学习，完成后及时提交相关作业。");
    }
}
