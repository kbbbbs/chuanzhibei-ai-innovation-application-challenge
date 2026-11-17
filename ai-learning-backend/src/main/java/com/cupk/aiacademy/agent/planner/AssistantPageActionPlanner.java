package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.*;
import com.cupk.aiacademy.agent.orchestrator.AgentDirectActionReply;
import com.cupk.aiacademy.agent.kernel.AgentExecutionProgress;
import com.cupk.aiacademy.agent.tool.support.AgentToolScope;
import com.cupk.aiacademy.analysis.LearningAnalysisSnapshotService;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/** Prepares client form values without creating a course, report, or pending write. */
@Component
public class AssistantPageActionPlanner {
    private static final Set<String> CREATE_FIELDS = Set.of("title", "categoryId", "categoryName", "difficulty", "description", "coverUrl");
    private final PlannerModelClient model;
    private final ObjectMapper json;
    private final AgentActionCatalog catalog;
    private final LlmReadToolDecisionParser parser;
    private final LlmReadToolDecisionValidator validator;
    private final PermissionService permissions;
    private final LearningAnalysisSnapshotService snapshots;
    private final JdbcTemplate db;

    public AssistantPageActionPlanner(PlannerModelClient model, ObjectMapper json, AgentActionCatalog catalog,
                                     LlmReadToolDecisionParser parser, LlmReadToolDecisionValidator validator,
                                     PermissionService permissions, LearningAnalysisSnapshotService snapshots, JdbcTemplate db) {
        this.model = model;
        this.json = json;
        this.catalog = catalog;
        this.parser = parser;
        this.validator = validator;
        this.permissions = permissions;
        this.snapshots = snapshots;
        this.db = db;
    }

    public AgentPlanningOutcome prepareCreation(String userText) {
        if (!permissions.isTeacher()) return clarification("请使用教师账号进入新建课程。");
        var parameters = catalog.findByActionCode("course.create").orElseThrow()
                .parameters().stream().filter(field -> CREATE_FIELDS.contains(field.name()))
                .map(field -> new AgentActionParameterSpec(field.name(), field.label(), false, field.description(),
                        field.type(), "difficulty".equals(field.name()) ? List.of("beginner", "intermediate", "advanced") : field.enumValues(),
                        field.aliases(), field.examples(), field.maxLength(), field.minimum(), field.maximum(), field.pattern()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        parameters.add(new AgentActionParameterSpec("coverUrl", "封面URL", false, "用户明确提供的课程封面地址",
                AgentActionParameterType.STRING, List.of(), List.of(), List.of(), 255, null, null, ""));
        try {
            var fields = parse(userText, parameters, "准备新建课程表单，只抽取用户已提供的信息。缺少名称或分类也可返回空字段。"
                    + "难度入门=beginner、基础=intermediate、进阶=advanced。不得填入发布状态、教师身份或学习人数。"
                    + "现有分类=" + json.writeValueAsString(db.queryForList("SELECT id,name FROM t_course_category ORDER BY id")));
            fields.remove("maxItems");
            boolean categoryMissing = !resolveCategory(fields);
            return AgentPlanningOutcome.page("课程信息已准备，请核对填写内容后点击“下一步”。"
                    + (categoryMissing ? "提供的分类尚未匹配，请在页面选择分类。" : ""),
                    Map.of("type", "course-create", "fields", fields));
        } catch (Exception error) {
            return clarification(error instanceof BusinessException ? error.getMessage() : "课程信息准备失败，请重新描述要填写的信息。");
        }
    }

    public AgentPlanningOutcome prepareAnalysis(String userText) {
        try {
            permissions.requireTeacherOrAdmin();
            var fields = parse(userText, List.of(
                    new AgentActionParameterSpec("courseId", "课程ID", false, "仅当用户明确提供数字课程ID时填写", AgentActionParameterType.INTEGER),
                    new AgentActionParameterSpec("courseTitle", "课程名称", false, "用户提供的完整课程名称")),
                    "准备进入课程学情分析。仅抽取用户指定的课程名称或ID，不得选取其他课程或生成报告。"
                    + "未指定课程且未提及当前课程时返回空字段。");
            Integer requested = fields.get("courseId") instanceof Number number ? number.intValue() : null;
            String title = text(fields.get("courseTitle"));
            Integer scope = AgentToolScope.courseId();
            if (requested != null && !java.util.regex.Pattern.compile("(?<!\\d)" + requested + "(?!\\d)").matcher(userText).find())
                throw new BusinessException("请提供准确的课程名称或课程ID。");
            int courseId = snapshots.resolveCourse(title.isBlank() && requested == null ? scope : requested, title);
            if (scope != null && scope != courseId) throw new BusinessException("当前对话属于另一门课程，请在通用助手中选择目标课程。");
            permissions.requireCourseTeacher(courseId);
            var course = permissions.getCourse(courseId);
            if (!title.isBlank() && !title.equals(course.getTitle()))
                throw new BusinessException("提供的课程名称与课程ID不一致，请核对目标课程。");
            return AgentPlanningOutcome.page("已定位《" + course.getTitle() + "》，请在学情分析页面核对课程后点击“一键生成学情报告”。",
                    Map.of("type", "learning-analysis", "courseId", courseId, "courseTitle", course.getTitle()));
        } catch (Exception error) {
            return clarification(error instanceof BusinessException ? error.getMessage() : "课程定位失败，请提供准确的课程名称或课程ID。");
        }
    }

    private Map<String, Object> parse(String userText, List<AgentActionParameterSpec> fields, String instruction) throws Exception {
        var spec = new AgentReadToolSpec("page_fields", "提取页面预填信息", fields, List.of());
        AgentExecutionProgress.emit("MODEL_REQUEST", null);
        var decision = parser.parse(model.call("你是教学平台页面信息规划器。" + instruction
                + "只返回JSON，不输出解释。只抽取明确提供的字段，未知值省略，禁止臆造实体或ID。"
                + "格式为{\"decision\":\"TOOL\",\"toolName\":\"page_fields\",\"confidence\":0.99,\"arguments\":{}}。"
                + "字段schema=" + json.writeValueAsString(spec.toData()) + "\n用户请求=" + userText));
        var validated = validator.validate(decision, List.of(spec));
        if (!validated.isValid() || validated.isChat()) throw new BusinessException("页面填写信息校验失败，请重新描述课程信息。");
        return new LinkedHashMap<>(validated.getNormalizedArguments());
    }

    private boolean resolveCategory(Map<String, Object> fields) {
        String name = text(fields.get("categoryName"));
        Object id = fields.get("categoryId");
        if (name.isBlank() && id == null) return true;
        var matches = !name.isBlank() ? db.queryForList("SELECT id,name FROM t_course_category WHERE name=?", name)
                : db.queryForList("SELECT id,name FROM t_course_category WHERE id=?", id);
        if (matches.size() != 1 || id != null && !Objects.equals(((Number) matches.getFirst().get("id")).intValue(), ((Number) id).intValue())) {
            fields.remove("categoryId");
            return false;
        }
        fields.put("categoryId", ((Number) matches.getFirst().get("id")).intValue());
        fields.put("categoryName", matches.getFirst().get("name"));
        return true;
    }

    private AgentPlanningOutcome clarification(String content) {
        return AgentPlanningOutcome.write(new AgentDirectActionReply(content, null));
    }

    private String text(Object value) { return value == null ? "" : value.toString().trim(); }

}
