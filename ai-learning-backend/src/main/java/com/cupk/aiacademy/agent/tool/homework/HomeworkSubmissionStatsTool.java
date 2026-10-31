package com.cupk.aiacademy.agent.tool.homework;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.HomeworkSubmit;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.pojo.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class HomeworkSubmissionStatsTool implements AgentTool {
    private final HomeworkMapper homeworkMapper;
    private final HomeworkSubmitMapper homeworkSubmitMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final ClassStudentMapper classStudentMapper;
    private final UserMapper userMapper;
    private final AgentToolSupport support;

    public HomeworkSubmissionStatsTool(HomeworkMapper homeworkMapper,
                                       HomeworkSubmitMapper homeworkSubmitMapper,
                                       TeachingClassMapper teachingClassMapper,
                                       ClassStudentMapper classStudentMapper,
                                       UserMapper userMapper,
                                       AgentToolSupport support) {
        this.homeworkMapper = homeworkMapper;
        this.homeworkSubmitMapper = homeworkSubmitMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.classStudentMapper = classStudentMapper;
        this.userMapper = userMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "homework_submission_stats";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("homework_submit_stats", "homework_missing_students");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        AuthUtils.LoginUser user = UserContext.get();
        if (!support.isTeacher(user) && !support.isAdmin(user)) {
            return AgentToolResult.failure(name(), declaredOperationType(), "只有教师或管理员可以查询作业提交统计。");
        }

        List<Integer> classIds = resolveClassIds(context.stringArgument("className"));
        if (classIds != null && classIds.isEmpty()) {
            return AgentToolResult.read(name(), "未找到当前账号可见的匹配教学班。", Map.of("count", 0));
        }

        QueryWrapper<Homework> query = new QueryWrapper<>();
        if (!support.applyHomeworkVisibility(query)) {
            return AgentToolResult.read(name(), "当前账号没有可查询的作业范围。", Map.of("count", 0));
        }
        if (classIds != null) {
            query.in("class_id", classIds);
        }
        String homeworkTitle = context.stringArgument("homeworkTitle");
        if (!homeworkTitle.isBlank()) {
            query.like("title", homeworkTitle);
        }
        query.orderByDesc("deadline").orderByDesc("id").last("LIMIT " + context.maxItems());
        List<Homework> homeworks = homeworkMapper.selectList(query);
        if (homeworks == null || homeworks.isEmpty()) {
            return AgentToolResult.read(name(), "未查询到匹配作业，无法统计提交情况。", Map.of("count", 0));
        }

        List<String> lines = new ArrayList<>();
        for (Homework homework : homeworks) {
            List<Integer> studentIds = activeStudentIds(homework.getClassId());
            Set<Integer> submittedIds = submittedStudentIds(homework.getId());
            long submittedCount = studentIds.stream().filter(submittedIds::contains).count();
            List<Integer> missingIds = studentIds.stream().filter(id -> !submittedIds.contains(id)).toList();
            lines.add("作业=" + support.value(homework.getTitle())
                    + "；作业ID=" + support.value(homework.getId())
                    + "；教学班ID=" + support.value(homework.getClassId())
                    + "；应交人数=" + studentIds.size()
                    + "；已交人数=" + submittedCount
                    + "；未交人数=" + missingIds.size()
                    + "；提交率=" + percent(submittedCount, studentIds.size())
                    + "；未交学生=" + userNames(missingIds));
        }
        return AgentToolResult.read(name(), String.join("\n", lines), Map.of("count", homeworks.size()));
    }

    private List<Integer> resolveClassIds(String className) {
        if (className.isBlank()) {
            return null;
        }
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        if (!support.applyTeachingClassVisibility(query)) {
            return List.of();
        }
        query.like("class_name", className).orderByDesc("id").last("LIMIT 20");
        List<TeachingClass> classes = teachingClassMapper.selectList(query);
        return classes == null ? List.of() : classes.stream()
                .map(TeachingClass::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private List<Integer> activeStudentIds(Integer classId) {
        if (classId == null) {
            return List.of();
        }
        QueryWrapper<ClassStudent> query = new QueryWrapper<>();
        query.eq("class_id", classId).eq("status", 1);
        List<ClassStudent> students = classStudentMapper.selectList(query);
        return students == null ? List.of() : students.stream()
                .map(ClassStudent::getStudentId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private Set<Integer> submittedStudentIds(Integer homeworkId) {
        if (homeworkId == null) {
            return Set.of();
        }
        QueryWrapper<HomeworkSubmit> query = new QueryWrapper<>();
        query.eq("homework_id", homeworkId);
        List<HomeworkSubmit> submits = homeworkSubmitMapper.selectList(query);
        if (submits == null) {
            return Set.of();
        }
        return submits.stream()
                .map(HomeworkSubmit::getStudentId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String userNames(List<Integer> userIds) {
        if (userIds.isEmpty()) {
            return "无";
        }
        return userIds.stream().limit(12).map(this::userName).collect(Collectors.joining("、"));
    }

    private String userName(Integer userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return String.valueOf(userId);
        }
        return user.getNickname() == null || user.getNickname().isBlank()
                ? support.value(user.getUsername())
                : user.getNickname();
    }

    private String percent(long value, int total) {
        if (total <= 0) {
            return "0%";
        }
        return BigDecimal.valueOf(value * 100)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "%";
    }
}
