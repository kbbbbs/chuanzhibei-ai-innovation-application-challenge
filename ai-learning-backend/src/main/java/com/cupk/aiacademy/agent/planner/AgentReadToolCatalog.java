package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionParameterSpec;
import com.cupk.aiacademy.agent.action.AgentActionParameterType;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolOperationType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AgentReadToolCatalog {
    private static final Map<String, String> DESCRIPTIONS = descriptions();

    private final Map<String, AgentTool> toolsByName = new LinkedHashMap<>();

    public AgentReadToolCatalog(List<AgentTool> tools) {
        if (tools == null) {
            return;
        }
        for (AgentTool tool : tools) {
            if (tool == null || tool.declaredOperationType() != AgentToolOperationType.READ) {
                continue;
            }
            register(tool.name(), tool);
            for (String alias : tool.aliases()) {
                register(alias, tool);
            }
        }
    }

    public List<AgentReadToolSpec> enabledSpecs(List<String> enabledTools) {
        if (enabledTools == null || enabledTools.isEmpty()) {
            return List.of();
        }
        Set<AgentTool> selected = new LinkedHashSet<>();
        for (String enabledTool : enabledTools) {
            AgentTool tool = toolsByName.get(normalize(enabledTool));
            if (tool != null) {
                selected.add(tool);
            }
        }
        List<AgentReadToolSpec> specs = new ArrayList<>();
        for (AgentTool tool : selected) {
            String name = normalize(tool.name());
            specs.add(specFor(name,
                    DESCRIPTIONS.getOrDefault(name, "读取平台中的" + name + "相关数据")));
        }
        return List.copyOf(specs);
    }

    public List<Map<String, Object>> enabledIntentSummaries(List<String> enabledTools) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (AgentReadToolSpec spec : enabledSpecs(enabledTools)) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("code", spec.getToolName());
            summary.put("summary", spec.getDescription());
            summaries.add(Map.copyOf(summary));
        }
        return List.copyOf(summaries);
    }

    private AgentReadToolSpec specFor(String name, String description) {
        return switch (name) {
            case "learning_analysis_report" -> new AgentReadToolSpec(name, description,
                    List.of(integer("courseId", "课程ID", false, "明确提供课程ID时填写", 1, Integer.MAX_VALUE),
                            text("courseTitle", "课程名称", false, "课程准确名称，课程ID与名称至少提供一项"),
                            integer("targetWords", "报告目标字数", false, "300至5000字，默认1000字", 300, 5000),
                            text("templateId", "模板ID", false, "使用已上传模板的ID，默认default")),
                    List.of(List.of("courseId", "courseTitle")));
            case "course_category_search" -> new AgentReadToolSpec(name, description,
                    List.of(
                            text("name", "分类名称", false, "按名称筛选；列出全部分类时不填写"),
                            bool("listAll", "是否列出全部", false, "列出全部分类时设置 true"),
                            maxItems()
                    ), List.of());
            case "course_by_category_search" -> new AgentReadToolSpec(name, description,
                    List.of(
                            text("categoryName", "分类名称", true, "要查询其课程的分类名称"),
                            maxItems()
                    ), List.of());
            case "teacher_application_search" -> new AgentReadToolSpec(name, description,
                    List.of(
                            text("keyword", "申请人关键词", false, "用户名、昵称、邮箱、手机号或申请原因"),
                            integer("status", "审核状态", false, "0待审核、1通过、2拒绝", 0, 2),
                            maxItems()
                    ), List.of());
            case "import_log_search" -> new AgentReadToolSpec(name, description,
                    List.of(
                            text("keyword", "导入关键词", false, "导入类型或接口关键词"),
                            integer("httpStatus", "HTTP状态码", false, "例如 200、400、500", 100, 599),
                            maxItems()
                    ), List.of());
            case "course_stats" -> new AgentReadToolSpec(name, description,
                    List.of(text("courseTitle", "课程名称", true, "要统计的课程名称"), maxItems()),
                    List.of());
            case "course_structure_search" -> new AgentReadToolSpec(name, description,
                    List.of(text("courseTitle", "课程名称", true, "要查询章节和课时结构的课程名称"), maxItems()),
                    List.of());
            case "homework_submission_stats" -> new AgentReadToolSpec(name, description,
                    List.of(
                            text("homeworkTitle", "作业名称", false, "要统计提交情况的作业"),
                            text("className", "教学班名称", false, "用于限定教学班"),
                            maxItems()
                    ), List.of(List.of("homeworkTitle", "className")));
            case "exam_stats" -> new AgentReadToolSpec(name, description,
                    List.of(
                            text("examTitle", "考试名称", false, "要统计的考试安排"),
                            text("studentName", "学生名称", false, "只统计指定学生时填写"),
                            maxItems()
                    ), List.of());
            case "question_search" -> new AgentReadToolSpec(name, description,
                    List.of(
                            text("keyword", "题目关键词", false, "题干、知识点或解析关键词"),
                            text("courseTitle", "课程名称", false, "限定所属课程"),
                            text("questionType", "题型", false, "例如 single、multiple、judge、blank、short"),
                            integer("status", "状态", false, "0停用、1启用", 0, 1),
                            maxItems()
                    ), List.of());
            default -> new AgentReadToolSpec(name, description);
        };
    }

    private AgentActionParameterSpec text(String name, String label, boolean required, String description) {
        return new AgentActionParameterSpec(name, label, required, description,
                AgentActionParameterType.STRING, List.of(), List.of(), List.of(),
                200, null, null, "");
    }

    private AgentActionParameterSpec bool(String name, String label, boolean required, String description) {
        return new AgentActionParameterSpec(name, label, required, description,
                AgentActionParameterType.BOOLEAN, List.of(), List.of(), List.of());
    }

    private AgentActionParameterSpec integer(String name,
                                             String label,
                                             boolean required,
                                             String description,
                                             int minimum,
                                             int maximum) {
        return new AgentActionParameterSpec(name, label, required, description,
                AgentActionParameterType.INTEGER, List.of(), List.of(), List.of(),
                null, BigDecimal.valueOf(minimum), BigDecimal.valueOf(maximum), "");
    }

    private AgentActionParameterSpec maxItems() {
        return integer("maxItems", "最大返回条数", false, "1 到 50，默认 10", 1, 50);
    }

    private void register(String name, AgentTool tool) {
        String normalized = normalize(name);
        if (!normalized.isBlank()) {
            toolsByName.putIfAbsent(normalized, tool);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private static Map<String, String> descriptions() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("learning_analysis_report", "生成指定课程全部教学班的学情分析报告，汇总课程、教学班、作业与考试，支持指定字数和Word模板");
        values.put("course_search", "查询课程名称、教师、难度、简介和可见课程列表");
        values.put("course_category_search", "查询课程分类；query 使用分类名称，列出全部分类时使用星号 *");
        values.put("course_by_category_search", "查询某个课程分类下的课程；query 只填写分类名称");
        values.put("chapter_search", "查询课程章节及章节顺序");
        values.put("lesson_search", "查询课时、课时内容和所属章节");
        values.put("course_structure_search", "按课程名称查询课程的章节和课时结构");
        values.put("course_resource_search", "查询课程或课时关联的教学资源");
        values.put("teaching_class_search", "查询教学班、授课教师、学期和课程关系");
        values.put("homework_search", "查询作业、截止时间、状态和所属教学班");
        values.put("exam_session_search", "查询考试安排、时间、试卷和教学班");
        values.put("exam_record_search", "查询考试记录、学生考试结果和得分");
        values.put("exam_paper_search", "查询考试试卷及其课程、状态和总分");
        values.put("notice_search", "查询教学班通知及发布时间");
        values.put("grade_record_search", "查询课程成绩记录");
        values.put("learning_progress", "查询学生课时学习进度");
        values.put("learning_plan", "查询学生学习计划");
        values.put("paper_local_search", "查询本地学术论文元数据");
        values.put("teacher_application_search", "管理员查询教师入驻申请和审核状态");
        values.put("import_log_search", "管理员或教师查询导入任务及HTTP状态");
        values.put("course_stats", "统计指定课程的章节、课时、教学班和作业数量");
        values.put("homework_submission_stats", "统计作业应交、已交、未交人数和提交率");
        values.put("exam_stats", "统计考试记录数量、平均分、最高分和最低分");
        values.put("question_search", "查询题库题目、题型、知识点、难度和状态");
        return Map.copyOf(values);
    }
}
