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
public class LearningProgressTool implements AgentTool {
    private final AgentToolSupport support;

    public LearningProgressTool(AgentToolSupport support) {
        this.support = support;
    }

    @Override
    public String name() {
        return "learning_progress";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("progress_query");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        AuthUtils.LoginUser user = UserContext.get();
        if (!support.isStudent(user)) {
            return AgentToolResult.read("学习进度工具仅返回学生本人的学习记录；当前用户没有可读取的个人学习进度。");
        }
        List<LearningProgress> progressList = support.listStudentProgress(user.getUserId(), context.maxItems());
        if (progressList.isEmpty()) {
            return AgentToolResult.read("暂无学习进度记录。");
        }

        List<String> lines = new ArrayList<>();
        for (LearningProgress progress : progressList) {
            Lesson lesson = support.safeLesson(progress.getLessonId());
            lines.add("课时ID=" + support.value(progress.getLessonId())
                    + "；课时=" + support.value(lesson == null ? null : lesson.getTitle())
                    + "；教学班ID=" + support.value(progress.getClassId())
                    + "；进度=" + support.value(progress.getProgress()) + "%"
                    + "；已学秒数=" + support.value(progress.getLearnedSeconds())
                    + "；最近学习=" + support.value(progress.getLastLearnTime()));
        }
        return AgentToolResult.read(String.join("\n", lines));
    }
}
