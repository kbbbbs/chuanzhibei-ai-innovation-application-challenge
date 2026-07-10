package com.cupk.aiacademy.agent.tool.exam;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.QuestionMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Question;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class QuestionSearchTool implements AgentTool {
    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;
    private final AgentToolSupport support;

    public QuestionSearchTool(QuestionMapper questionMapper,
                              CourseMapper courseMapper,
                              AgentToolSupport support) {
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "question_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("question_bank_search", "question_query");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        AuthUtils.LoginUser user = UserContext.get();
        if (!support.isTeacher(user) && !support.isAdmin(user)) {
            return AgentToolResult.failure(name(), declaredOperationType(), "只有教师或管理员可以查询题库。");
        }

        QueryWrapper<Question> query = new QueryWrapper<>();
        if (support.isTeacher(user)) {
            query.eq("teacher_id", user.getUserId());
        }
        String courseTitle = context.stringArgument("courseTitle");
        if (!courseTitle.isBlank()) {
            List<Integer> courseIds = courseIds(courseTitle);
            if (courseIds.isEmpty()) {
                return AgentToolResult.read(name(), "未找到当前账号可见的匹配课程。", Map.of("count", 0));
            }
            query.in("course_id", courseIds);
        }
        String keyword = context.stringArgument("keyword");
        if (!keyword.isBlank()) {
            query.and(wrapper -> wrapper.like("title", keyword)
                    .or().like("knowledge_point", keyword)
                    .or().like("analysis", keyword));
        }
        String questionType = context.stringArgument("questionType");
        if (!questionType.isBlank()) {
            query.eq("question_type", questionType);
        }
        Integer status = context.integerArgument("status");
        if (status != null) {
            query.eq("status", status);
        }
        query.orderByDesc("id").last("LIMIT " + context.maxItems());
        List<Question> questions = questionMapper.selectList(query);
        if (questions == null || questions.isEmpty()) {
            return AgentToolResult.read(name(), "未查询到匹配题目。", Map.of("count", 0));
        }

        String content = questions.stream()
                .map(question -> "题目ID=" + support.value(question.getId())
                        + "；课程ID=" + support.value(question.getCourseId())
                        + "；题型=" + support.value(question.getQuestionType())
                        + "；题干=" + support.summarize(question.getTitle(), 160)
                        + "；知识点=" + support.value(question.getKnowledgePoint())
                        + "；难度=" + support.value(question.getDifficulty())
                        + "；分值=" + support.value(question.getScore())
                        + "；状态=" + support.value(question.getStatus()))
                .collect(Collectors.joining("\n"));
        return AgentToolResult.read(name(), content, Map.of("count", questions.size()));
    }

    private List<Integer> courseIds(String courseTitle) {
        QueryWrapper<Course> query = new QueryWrapper<>();
        if (!support.applyCourseVisibility(query)) {
            return List.of();
        }
        query.like("title", courseTitle).last("LIMIT 20");
        List<Course> courses = courseMapper.selectList(query);
        return courses == null ? List.of() : courses.stream().map(Course::getId).toList();
    }
}
