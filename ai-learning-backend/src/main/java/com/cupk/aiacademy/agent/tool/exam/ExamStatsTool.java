package com.cupk.aiacademy.agent.tool.exam;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.ExamRecordMapper;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.ExamRecord;
import com.cupk.aiacademy.pojo.ExamSession;
import com.cupk.aiacademy.pojo.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ExamStatsTool implements AgentTool {
    private final ExamRecordMapper examRecordMapper;
    private final ExamSessionMapper examSessionMapper;
    private final UserMapper userMapper;
    private final AgentToolSupport support;

    public ExamStatsTool(ExamRecordMapper examRecordMapper,
                         ExamSessionMapper examSessionMapper,
                         UserMapper userMapper,
                         AgentToolSupport support) {
        this.examRecordMapper = examRecordMapper;
        this.examSessionMapper = examSessionMapper;
        this.userMapper = userMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "exam_stats";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("exam_score_stats", "exam_statistics");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        AuthUtils.LoginUser user = UserContext.get();
        QueryWrapper<ExamRecord> query = new QueryWrapper<>();
        List<Integer> visibleSessionIds = visibleSessionIds(user, context.stringArgument("examTitle"));
        if (visibleSessionIds != null && visibleSessionIds.isEmpty()) {
            return AgentToolResult.read(name(), "未查询到当前账号可见的匹配考试。", Map.of("count", 0));
        }
        if (visibleSessionIds != null) {
            query.in("exam_session_id", visibleSessionIds);
        }
        if (support.isStudent(user)) {
            query.eq("student_id", user.getUserId());
        } else if (!support.isTeacher(user) && !support.isAdmin(user)) {
            return AgentToolResult.failure(name(), declaredOperationType(), "当前账号无权查询考试统计。");
        }

        String studentName = context.stringArgument("studentName");
        if (!studentName.isBlank()) {
            List<Integer> studentIds = studentIds(studentName);
            if (studentIds.isEmpty()) {
                return AgentToolResult.read(name(), "未找到匹配学生。", Map.of("count", 0));
            }
            query.in("student_id", studentIds);
        }
        query.orderByDesc("submit_time").orderByDesc("id").last("LIMIT " + context.maxItems());
        List<ExamRecord> records = examRecordMapper.selectList(query);
        List<BigDecimal> scores = records == null ? List.of() : records.stream()
                .map(ExamRecord::getTotalScore)
                .filter(score -> score != null)
                .toList();
        if (records == null || records.isEmpty()) {
            return AgentToolResult.read(name(), "未查询到匹配考试记录，无法统计。", Map.of("count", 0));
        }
        if (scores.isEmpty()) {
            return AgentToolResult.read(name(), "匹配到 " + records.size() + " 条考试记录，但暂无有效分数。",
                    Map.of("count", records.size(), "scoredCount", 0));
        }

        BigDecimal total = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = total.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
        BigDecimal max = scores.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal min = scores.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        String content = "考试统计：记录数=" + records.size()
                + "；有效分数=" + scores.size()
                + "；平均分=" + average.stripTrailingZeros().toPlainString()
                + "；最高分=" + max.stripTrailingZeros().toPlainString()
                + "；最低分=" + min.stripTrailingZeros().toPlainString();
        return AgentToolResult.read(name(), content,
                Map.of("count", records.size(), "scoredCount", scores.size(),
                        "average", average, "maximum", max, "minimum", min));
    }

    private List<Integer> visibleSessionIds(AuthUtils.LoginUser user, String examTitle) {
        QueryWrapper<ExamSession> query = new QueryWrapper<>();
        if (support.isTeacher(user)) {
            query.eq("teacher_id", user.getUserId());
        } else if (support.isStudent(user)) {
            List<Integer> classIds = support.findVisibleClassIds();
            if (classIds.isEmpty()) {
                return List.of();
            }
            query.in("class_id", classIds);
        } else if (!support.isAdmin(user)) {
            return List.of();
        }
        if (!examTitle.isBlank()) {
            query.like("title", examTitle);
        }
        List<ExamSession> sessions = examSessionMapper.selectList(query);
        return sessions == null ? List.of() : sessions.stream()
                .map(ExamSession::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private List<Integer> studentIds(String studentName) {
        QueryWrapper<User> query = new QueryWrapper<>();
        query.like("username", studentName).or().like("nickname", studentName).last("LIMIT 20");
        List<User> users = userMapper.selectList(query);
        return users == null ? List.of() : users.stream().map(User::getId).toList();
    }
}
