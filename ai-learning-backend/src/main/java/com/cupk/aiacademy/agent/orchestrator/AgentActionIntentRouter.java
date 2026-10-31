package com.cupk.aiacademy.agent.orchestrator;

import com.cupk.aiacademy.agent.core.config.AgentConfigurationParser;
import com.cupk.aiacademy.agent.action.AgentActionCatalog;
import com.cupk.aiacademy.agent.action.AgentActionPlanFactory;
import com.cupk.aiacademy.agent.action.AgentPendingActionResponse;
import com.cupk.aiacademy.agent.action.AgentActionSelectionService;
import com.cupk.aiacademy.agent.tool.AgentToolExecutor;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolOperationType;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.pojo.AgentDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Routes clear natural-language write intents to safe write-plan tools before
 * the request reaches free-form model chat.
 */
public class AgentActionIntentRouter {
    private static final Logger log = LoggerFactory.getLogger(AgentActionIntentRouter.class);

    private final AgentConfigurationParser configurationParser;
    private final AgentToolExecutor agentToolExecutor;
    private final AgentActionSelectionService actionSelectionService;
    private final AgentActionCatalog actionCatalog;
    private final AgentActionPlanFactory actionPlanFactory;

    public AgentActionIntentRouter(AgentConfigurationParser configurationParser,
                                   AgentToolExecutor agentToolExecutor) {
        this(configurationParser, agentToolExecutor, null, null, null);
    }

    public AgentActionIntentRouter(AgentConfigurationParser configurationParser,
                                   AgentToolExecutor agentToolExecutor,
                                   AgentActionSelectionService actionSelectionService) {
        this(configurationParser, agentToolExecutor, actionSelectionService, null, null);
    }

    public AgentActionIntentRouter(AgentConfigurationParser configurationParser,
                                   AgentToolExecutor agentToolExecutor,
                                   AgentActionSelectionService actionSelectionService,
                                   AgentActionCatalog actionCatalog,
                                   AgentActionPlanFactory actionPlanFactory) {
        this.configurationParser = configurationParser == null
                ? new AgentConfigurationParser(null)
                : configurationParser;
        this.agentToolExecutor = agentToolExecutor;
        this.actionSelectionService = actionSelectionService;
        this.actionCatalog = actionCatalog;
        this.actionPlanFactory = actionPlanFactory;
    }

    public Optional<AgentDirectActionReply> buildDirectActionReply(AgentDefinition agent, String currentUserContent) {
        if (agentToolExecutor == null || isBlank(currentUserContent)) {
            return Optional.empty();
        }
        List<String> enabledTools = configurationParser.parseEnabledTools(agent.getEnabledTools());
        if (actionSelectionService != null) {
            Optional<AgentDirectActionReply> selectedReply = actionSelectionService.select(enabledTools, currentUserContent)
                    .flatMap(selection -> executePlanTool(selection.toolName(), currentUserContent));
            if (selectedReply.isPresent()) {
                return selectedReply;
            }
        }
        Optional<String> planToolName = directPlanToolName(enabledTools, currentUserContent);
        if (planToolName.isEmpty()) {
            return Optional.empty();
        }
        return executePlanTool(planToolName.get(), currentUserContent);
    }

    private Optional<AgentDirectActionReply> executePlanTool(String planToolName, String currentUserContent) {
        if (isBlank(planToolName)) {
            return Optional.empty();
        }
        try {
            if (actionCatalog != null && actionPlanFactory != null) {
                Optional<AgentToolResult> result = actionCatalog.findByToolName(planToolName)
                        .map(spec -> actionPlanFactory.createWritePlan(
                                planToolName,
                                spec,
                                new AgentToolContext(currentUserContent.trim(), 1)
                        ));
                if (result.isPresent()) {
                    return result.map(this::toDirectActionReply);
                }
            }
            return agentToolExecutor.executeTools(List.of(planToolName),
                            currentUserContent.trim(),
                            1)
                    .stream()
                    .filter(result -> result.operationType() == AgentToolOperationType.WRITE_PLAN)
                    .findFirst()
                    .map(this::toDirectActionReply);
        } catch (Exception e) {
            log.warn("Agent direct action routing failed, fallback to model chat", e);
            return Optional.empty();
        }
    }

    private AgentDirectActionReply toDirectActionReply(AgentToolResult result) {
        return new AgentDirectActionReply(
                formatWritePlanReply(result),
                AgentPendingActionResponse.fromToolData(result.data())
        );
    }

    private Optional<String> directPlanToolName(List<String> enabledTools, String currentUserContent) {
        if (enabledTools.isEmpty()) {
            return Optional.empty();
        }
        if (isEnabledTool(enabledTools, "notice_create_plan", "notice_publish_plan", "announcement_create_plan")
                && looksLikeNoticeCreateRequest(currentUserContent)) {
            return Optional.of("notice_create_plan");
        }
        if (isEnabledTool(enabledTools, "notice_status_plan", "notice_archive_plan", "notice_withdraw_plan",
                "notice_delete_plan", "notice_remove_plan")
                && looksLikeNoticeStatusRequest(currentUserContent)) {
            return Optional.of("notice_status_plan");
        }
        if (isEnabledTool(enabledTools, "notice_update_plan", "notice_modify_plan", "notice_edit_plan",
                "announcement_update_plan")
                && looksLikeNoticeUpdateRequest(currentUserContent)) {
            return Optional.of("notice_update_plan");
        }
        if (isEnabledTool(enabledTools, "teacher_application_review_plan", "teacher_apply_review_plan",
                "teacher_application_approve_plan", "teacher_application_reject_plan")
                && looksLikeTeacherApplicationReviewRequest(currentUserContent)) {
            return Optional.of("teacher_application_review_plan");
        }
        if (isEnabledTool(enabledTools, "course_resource_remove_plan", "lesson_resource_remove_plan",
                "course_resource_delete_plan", "lesson_resource_delete_plan")
                && looksLikeCourseResourceRemoveRequest(currentUserContent)) {
            return Optional.of("course_resource_remove_plan");
        }
        if (isEnabledTool(enabledTools, "course_resource_attach_plan", "lesson_resource_attach_plan",
                "course_resource_bind_plan", "lesson_resource_bind_plan")
                && looksLikeCourseResourceAttachRequest(currentUserContent)) {
            return Optional.of("course_resource_attach_plan");
        }
        if (isEnabledTool(enabledTools, "lesson_status_plan", "course_lesson_status_plan",
                "lesson_archive_plan", "lesson_delete_plan", "lesson_remove_plan")
                && looksLikeLessonStatusRequest(currentUserContent)) {
            return Optional.of("lesson_status_plan");
        }
        if (isEnabledTool(enabledTools, "lesson_sort_plan", "course_lesson_sort_plan", "lesson_reorder_plan")
                && looksLikeLessonSortRequest(currentUserContent)) {
            return Optional.of("lesson_sort_plan");
        }
        if (isEnabledTool(enabledTools, "lesson_create_plan", "course_lesson_create_plan", "lesson_add_plan")
                && looksLikeLessonCreateRequest(currentUserContent)) {
            return Optional.of("lesson_create_plan");
        }
        if (isEnabledTool(enabledTools, "lesson_update_plan", "course_lesson_update_plan",
                "lesson_modify_plan", "lesson_edit_plan")
                && looksLikeLessonUpdateRequest(currentUserContent)) {
            return Optional.of("lesson_update_plan");
        }
        if (isEnabledTool(enabledTools, "chapter_sort_plan", "course_chapter_sort_plan", "chapter_reorder_plan")
                && looksLikeChapterSortRequest(currentUserContent)) {
            return Optional.of("chapter_sort_plan");
        }
        if (isEnabledTool(enabledTools, "chapter_delete_plan", "course_chapter_delete_plan",
                "chapter_remove_plan", "course_chapter_remove_plan")
                && looksLikeChapterDeleteRequest(currentUserContent)) {
            return Optional.of("chapter_delete_plan");
        }
        if (isEnabledTool(enabledTools, "chapter_create_plan", "course_chapter_create_plan", "chapter_add_plan")
                && looksLikeChapterCreateRequest(currentUserContent)) {
            return Optional.of("chapter_create_plan");
        }
        if (isEnabledTool(enabledTools, "chapter_update_plan", "course_chapter_update_plan",
                "chapter_modify_plan", "chapter_edit_plan")
                && looksLikeChapterUpdateRequest(currentUserContent)) {
            return Optional.of("chapter_update_plan");
        }
        if (isEnabledTool(enabledTools, "class_student_remove_plan", "class_student_delete_plan",
                "teaching_class_student_remove_plan", "class_member_remove_plan", "student_unenroll_plan")
                && looksLikeClassStudentRemoveRequest(currentUserContent)) {
            return Optional.of("class_student_remove_plan");
        }
        if (isEnabledTool(enabledTools, "class_student_status_plan", "class_member_status_plan",
                "class_student_update_status_plan", "student_enrollment_status_plan")
                && looksLikeClassStudentStatusRequest(currentUserContent)) {
            return Optional.of("class_student_status_plan");
        }
        if (isEnabledTool(enabledTools, "class_student_add_plan", "teaching_class_student_add_plan",
                "class_member_add_plan", "student_enroll_plan")
                && looksLikeClassStudentAddRequest(currentUserContent)) {
            return Optional.of("class_student_add_plan");
        }
        if (isEnabledTool(enabledTools, "teaching_class_status_plan", "class_status_plan",
                "teaching_class_archive_plan", "teaching_class_delete_plan", "class_delete_plan")
                && looksLikeTeachingClassStatusRequest(currentUserContent)) {
            return Optional.of("teaching_class_status_plan");
        }
        if (isEnabledTool(enabledTools, "teaching_class_create_plan", "class_create_plan",
                "teaching_class_add_plan", "class_add_plan")
                && looksLikeTeachingClassCreateRequest(currentUserContent)) {
            return Optional.of("teaching_class_create_plan");
        }
        if (isEnabledTool(enabledTools, "teaching_class_update_plan", "class_update_plan",
                "teaching_class_modify_plan", "class_modify_plan", "teaching_class_edit_plan")
                && looksLikeTeachingClassUpdateRequest(currentUserContent)) {
            return Optional.of("teaching_class_update_plan");
        }
        if (isEnabledTool(enabledTools, "course_category_delete_plan", "category_delete_plan",
                "course_category_remove_plan", "category_remove_plan")
                && looksLikeCourseCategoryDeleteRequest(currentUserContent)) {
            return Optional.of("course_category_delete_plan");
        }
        if (isEnabledTool(enabledTools, "course_category_create_plan", "category_create_plan",
                "course_category_add_plan", "category_add_plan")
                && looksLikeCourseCategoryCreateRequest(currentUserContent)) {
            return Optional.of("course_category_create_plan");
        }
        if (isEnabledTool(enabledTools, "course_category_update_plan", "category_update_plan",
                "course_category_modify_plan", "category_modify_plan")
                && looksLikeCourseCategoryUpdateRequest(currentUserContent)) {
            return Optional.of("course_category_update_plan");
        }
        if (isEnabledTool(enabledTools, "course_delete_plan", "course_remove_plan")
                && looksLikeCourseDeleteRequest(currentUserContent)) {
            return Optional.of("course_delete_plan");
        }
        if (isEnabledTool(enabledTools, "course_status_plan", "course_publish_status_plan",
                "course_archive_plan")
                && looksLikeCourseStatusRequest(currentUserContent)) {
            return Optional.of("course_status_plan");
        }
        if (isEnabledTool(enabledTools, "course_create_plan", "course_publish_plan", "course_add_plan")
                && looksLikeCourseCreateRequest(currentUserContent)) {
            return Optional.of("course_create_plan");
        }
        if (isEnabledTool(enabledTools, "course_update_plan", "course_modify_plan", "course_edit_plan")
                && looksLikeCourseUpdateRequest(currentUserContent)) {
            return Optional.of("course_update_plan");
        }
        if (isEnabledTool(enabledTools, "homework_submission_grade_plan", "homework_grade_plan",
                "homework_submit_grade_plan", "assignment_grade_plan")
                && looksLikeHomeworkSubmissionGradeRequest(currentUserContent)) {
            return Optional.of("homework_submission_grade_plan");
        }
        if (isEnabledTool(enabledTools, "homework_delete_plan", "homework_remove_plan",
                "assignment_delete_plan", "assignment_remove_plan")
                && looksLikeHomeworkDeleteRequest(currentUserContent)) {
            return Optional.of("homework_delete_plan");
        }
        if (isEnabledTool(enabledTools, "homework_create_plan", "homework_publish_plan", "assignment_create_plan")
                && looksLikeHomeworkCreateRequest(currentUserContent)) {
            return Optional.of("homework_create_plan");
        }
        if (isEnabledTool(enabledTools, "homework_update_plan", "homework_modify_plan",
                "assignment_update_plan", "assignment_modify_plan")
                && looksLikeHomeworkUpdateRequest(currentUserContent)) {
            return Optional.of("homework_update_plan");
        }
        if (isEnabledTool(enabledTools, "exam_answer_grade_plan", "exam_manual_grade_plan", "exam_submission_grade_plan",
                "exam_answer_score_plan")
                && looksLikeExamAnswerGradeRequest(currentUserContent)) {
            return Optional.of("exam_answer_grade_plan");
        }
        if (isEnabledTool(enabledTools, "question_create_plan", "exam_question_create_plan",
                "question_add_plan", "question_bank_create_plan")
                && looksLikeQuestionCreateRequest(currentUserContent)) {
            return Optional.of("question_create_plan");
        }
        if (isEnabledTool(enabledTools, "question_update_plan", "exam_question_update_plan",
                "question_modify_plan", "question_edit_plan")
                && looksLikeQuestionUpdateRequest(currentUserContent)) {
            return Optional.of("question_update_plan");
        }
        if (isEnabledTool(enabledTools, "question_delete_plan", "exam_question_delete_plan",
                "question_remove_plan", "question_bank_delete_plan")
                && looksLikeQuestionDeleteRequest(currentUserContent)) {
            return Optional.of("question_delete_plan");
        }
        if (isEnabledTool(enabledTools, "grade_record_delete_plan", "grade_delete_plan", "score_delete_plan",
                "grade_remove_plan", "score_remove_plan")
                && looksLikeGradeRecordDeleteRequest(currentUserContent)) {
            return Optional.of("grade_record_delete_plan");
        }
        if (isEnabledTool(enabledTools, "grade_record_create_plan", "grade_create_plan", "score_create_plan",
                "grade_input_plan", "score_input_plan")
                && looksLikeGradeRecordCreateRequest(currentUserContent)) {
            return Optional.of("grade_record_create_plan");
        }
        if (isEnabledTool(enabledTools, "grade_record_update_plan", "grade_update_plan", "score_update_plan",
                "score_modify_plan", "grade_modify_plan")
                && looksLikeGradeRecordUpdateRequest(currentUserContent)) {
            return Optional.of("grade_record_update_plan");
        }
        if (isEnabledTool(enabledTools, "exam_session_close_plan", "exam_close_plan", "exam_end_plan",
                "exam_session_end_plan", "exam_session_finish_plan")
                && looksLikeExamSessionCloseRequest(currentUserContent)) {
            return Optional.of("exam_session_close_plan");
        }
        if (isEnabledTool(enabledTools, "exam_session_create_plan", "exam_create_plan", "exam_publish_plan", "exam_session_publish_plan")
                && looksLikeExamSessionCreateRequest(currentUserContent)) {
            return Optional.of("exam_session_create_plan");
        }
        return Optional.empty();
    }

    private String formatWritePlanReply(AgentToolResult result) {
        return "动作待确认";
    }

    private String crudType(AgentToolResult result) {
        String actionCode = stringValue(result.data().get("actionCode")).toLowerCase(Locale.ROOT);
        String operation = actionCode.contains(".") ? actionCode.substring(actionCode.lastIndexOf('.') + 1) : actionCode;
        return switch (operation) {
            case "create", "add", "attach", "input" -> "CREATE（新增）";
            case "status" -> "UPDATE（状态变更）";
            case "update", "sort", "grade", "close" -> "UPDATE（修改）";
            case "delete", "remove" -> "DELETE（删除）";
            case "read", "query", "search" -> "READ（查询）";
            default -> "WRITE（写操作）";
        };
    }

    private String instructionContent(AgentToolResult result) {
        Object parameters = result.data().get("parameters");
        if (parameters instanceof Map<?, ?> parameterMap) {
            String rawRequest = stringValue(parameterMap.get("rawRequest"));
            if (!rawRequest.isBlank()) {
                return rawRequest;
            }
        }
        String actionName = stringValue(result.data().get("actionName"));
        if (!actionName.isBlank()) {
            return actionName;
        }
        return "已识别系统指令，请确认后执行";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean looksLikeNoticeCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "通知", "公告")
                && containsAny(value, "发布", "创建", "新建", "发送", "发一条", "发个");
    }

    private boolean looksLikeNoticeStatusRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "通知", "公告")
                && containsAny(value, "撤回", "下线", "恢复", "删除", "移除", "作废", "撤销",
                        "状态改为", "状态改成", "调整状态", "变更状态", "删掉", "删去");
    }

    private boolean looksLikeNoticeUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        if (!containsAny(value, "通知", "公告")) {
            return false;
        }
        if (!containsAny(value, "修改", "调整", "更改", "更新", "编辑", "改成", "改为", "改名")) {
            return false;
        }
        return !containsAny(value, "撤回", "删除", "移除", "恢复", "发布", "创建", "新建", "发送",
                "状态改为", "状态改成", "调整状态", "变更状态");
    }

    private boolean looksLikeTeacherApplicationReviewRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "教师申请", "老师申请", "教师入驻", "入驻申请", "申请当老师")
                && containsAny(value, "审核", "通过", "拒绝", "驳回", "同意", "批准", "不通过");
    }

    private boolean looksLikeHomeworkCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "发布", "布置", "创建", "新建", "发送", "留一份", "留个");
    }

    private boolean looksLikeCourseCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        if (looksLikeCourseCategoryObjectTarget(value)) {
            return false;
        }
        return containsAny(value, "课程", "门课")
                && containsAny(value, "创建", "新建", "新增", "添加", "开设", "发布", "建一门", "加一门");
    }

    private boolean looksLikeLessonCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeLessonTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "创建", "新建", "新增", "添加", "加一个", "建一个", "上传");
    }

    private boolean looksLikeCourseResourceAttachRequest(String content) {
        String value = content == null ? "" : content.trim();
        return !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "课时", "小节", "课程资源", "资源")
                && containsAny(value, "资源", "视频", "文档", "资料", "链接", "课件", "附件")
                && containsAny(value, "挂载", "绑定", "关联", "附加", "上传", "添加", "设置", "替换", "换成");
    }

    private boolean looksLikeCourseResourceRemoveRequest(String content) {
        String value = content == null ? "" : content.trim();
        return !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "课时", "小节", "课程资源", "资源")
                && containsAny(value, "资源", "视频", "文档", "资料", "链接", "课件", "附件", "正文", "内容")
                && containsAny(value, "移除", "删除", "清空", "解绑", "取消绑定", "解除绑定", "去掉", "删掉", "删去", "撤掉");
    }

    private boolean looksLikeLessonUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeLessonTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "修改", "调整", "更改", "更新", "编辑", "改成", "改为", "改名", "替换");
    }

    private boolean looksLikeLessonSortRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeLessonTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "排序", "顺序", "调整顺序", "调序", "重新排序", "排到", "移到", "移动");
    }

    private boolean looksLikeLessonStatusRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeLessonTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "上架", "下架", "停用", "启用", "恢复", "归档", "关闭",
                "删除", "移除", "撤销", "作废", "删掉", "删去");
    }

    private boolean looksLikeLessonTarget(String value) {
        return value != null && containsAny(value, "课时", "小节", "课程内容", "视频课");
    }

    private boolean looksLikeChapterCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeChapterTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "创建", "新建", "新增", "添加", "加一章", "建一章");
    }

    private boolean looksLikeChapterUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeChapterTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "修改", "调整", "更改", "更新", "编辑", "改成", "改为", "改名");
    }

    private boolean looksLikeChapterSortRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeChapterTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "排序", "顺序", "调整顺序", "调序", "重新排序", "排到", "移到", "移动");
    }

    private boolean looksLikeChapterDeleteRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeChapterTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "删除", "移除", "撤销", "作废", "删掉", "删去");
    }

    private boolean looksLikeChapterTarget(String value) {
        return value != null && (value.contains("章节") || (value.contains("课程") && value.contains("章")));
    }

    private boolean looksLikeTeachingClassCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeTeachingClassTarget(value)
                && containsAny(value, "创建", "新建", "新增", "添加", "开设", "建班", "建一个");
    }

    private boolean looksLikeTeachingClassUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeTeachingClassTarget(value)
                && containsAny(value, "修改", "调整", "更改", "更新", "编辑", "改成", "改为", "改名");
    }

    private boolean looksLikeTeachingClassStatusRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeTeachingClassTarget(value)
                && containsAny(value, "结束", "关闭", "停用", "启用", "恢复", "归档", "删除",
                "移除", "撤销", "作废", "删掉", "删去");
    }

    private boolean looksLikeTeachingClassTarget(String value) {
        return value != null && containsAny(value, "教学班", "班级", "授课班", "课程班");
    }

    private boolean looksLikeClassStudentAddRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeClassStudentTarget(value)
                && containsAny(value, "加入", "添加", "新增", "录入", "纳入", "加到", "加进");
    }

    private boolean looksLikeClassStudentRemoveRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeClassStudentTarget(value)
                && containsAny(value, "移除", "删除", "移出", "踢出", "退班", "删掉", "删去");
    }

    private boolean looksLikeClassStudentStatusRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeClassStudentTarget(value)
                && containsAny(value, "休学", "恢复", "正常", "停用", "启用", "调整状态", "变更状态",
                "状态改为", "状态改成");
    }

    private boolean looksLikeClassStudentTarget(String value) {
        return value != null && (containsAny(value, "班级学生", "教学班学生", "班级成员", "学生名单")
                || (containsAny(value, "教学班", "班级", "授课班", "课程班")
                && containsAny(value, "学生", "成员")));
    }

    private boolean looksLikeCourseUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return (looksLikeCourseTarget(value) || looksLikeCourseTitleOnlyUpdateRequest(value))
                && containsAny(value, "修改", "调整", "更改", "更新", "编辑", "改成", "改为", "改名");
    }

    private boolean looksLikeCourseTitleOnlyUpdateRequest(String value) {
        if (value == null || !containsAny(value, "标题", "名称", "名字")) {
            return false;
        }
        if (!containsAny(value, "修改", "调整", "更改", "更新", "编辑", "改成", "改为", "改名")) {
            return false;
        }
        return !containsAny(value, "通知", "公告", "作业", "练习", "任务", "章节", "章",
                "课时", "小节", "教学班", "班级", "授课班", "考试", "试卷", "题目", "试题",
                "成绩", "分数");
    }

    private boolean looksLikeCourseStatusRequest(String content) {
        String value = content == null ? "" : content.trim();
        if (looksLikeCourseCategoryObjectTarget(value)) {
            return false;
        }
        return looksLikeCourseTarget(value)
                && containsAny(value, "上架", "下架", "停用", "启用", "恢复", "归档", "关闭");
    }

    private boolean looksLikeCourseDeleteRequest(String content) {
        String value = content == null ? "" : content.trim();
        if (looksLikeCourseCategoryObjectTarget(value)) {
            return false;
        }
        return looksLikeCourseTarget(value)
                && containsAny(value, "删除", "移除", "删掉", "删去", "去掉");
    }

    private boolean looksLikeCourseCategoryCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeCourseCategoryObjectTarget(value)
                && containsAny(value, "创建", "新建", "新增", "添加", "建一个", "加一个");
    }

    private boolean looksLikeCourseCategoryUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        if (!looksLikeCourseCategoryObjectTarget(value)) {
            return false;
        }
        if (containsAny(value, "课程分类改成", "课程分类改为", "课程分类调整为")) {
            return false;
        }
        return containsAny(value, "修改", "调整", "更改", "更新", "编辑", "改名", "重命名",
                "改成", "改为", "描述", "说明", "排序");
    }

    private boolean looksLikeCourseCategoryDeleteRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeCourseCategoryObjectTarget(value)
                && containsAny(value, "删除", "移除", "删掉", "删去", "去掉", "级联删除");
    }

    private boolean looksLikeCourseCategoryObjectTarget(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (looksLikeCourseEntityTargetWithCategoryParameter(value)) {
            return false;
        }
        if (containsAny(value, "课程分类", "课程类别")) {
            return true;
        }
        return containsAny(value, "分类", "类别") && !looksLikeCourseTarget(value);
    }

    private boolean looksLikeCourseEntityTargetWithCategoryParameter(String value) {
        if (!containsAny(value, "课程分类", "课程类别", "分类ID", "分类id", "分类Id",
                "分类编号", "类别ID", "类别id", "类别Id", "类别编号")) {
            return false;
        }
        if (containsAny(value, "创建课程分类", "新建课程分类", "新增课程分类", "添加课程分类",
                "创建课程类别", "新建课程类别", "新增课程类别", "添加课程类别")) {
            return false;
        }
        return containsAny(value, "一门课程", "一门课", "门课程", "门课", "建一门", "加一门",
                "创建课程", "新建课程", "新增课程", "添加课程", "开设课程", "发布课程");
    }

    private boolean looksLikeCourseTarget(String value) {
        if (value == null || containsAny(value, "课时", "小节", "章节")) {
            return false;
        }
        return containsAny(value, "课程", "门课", "一门课", "的课", "号课");
    }

    private boolean looksLikeHomeworkDeleteRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "删除", "移除", "撤销", "作废", "删掉", "删去");
    }

    private boolean looksLikeHomeworkUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "修改", "调整", "更改", "更新", "改成", "改为", "改到", "延后", "提前");
    }

    private boolean looksLikeHomeworkSubmissionGradeRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "作业", "练习", "任务", "提交")
                && containsAny(value, "批改", "评分", "打分", "给分", "判分", "评语", "反馈");
    }

    private boolean looksLikeExamSessionCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "考试", "测验", "考核")
                && containsAny(value, "创建", "新建", "安排", "发布", "开启", "开一场");
    }

    private boolean looksLikeQuestionCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "题目", "试题", "题库", "考题", "单选题", "多选题", "判断题", "填空题", "简答题")
                && containsAny(value, "创建", "新建", "新增", "添加", "录入", "出一道", "加一道");
    }

    private boolean looksLikeQuestionUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "题目", "试题", "题库", "考题", "单选题", "多选题", "判断题", "填空题", "简答题")
                && containsAny(value, "修改", "调整", "更改", "更新", "编辑", "修正", "改成", "改为", "设置");
    }

    private boolean looksLikeQuestionDeleteRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "题目", "试题", "题库", "考题", "单选题", "多选题", "判断题", "填空题", "简答题")
                && containsAny(value, "删除", "移除", "撤销", "作废", "删掉", "删去");
    }

    private boolean looksLikeGradeRecordUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "成绩", "分数", "得分", "分值")
                && containsAny(value, "修改", "调整", "更正", "改成", "改为", "更新", "记为", "设为");
    }

    private boolean looksLikeGradeRecordCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "成绩", "分数", "得分", "分值")
                && containsAny(value, "录入", "登记", "新增", "添加", "创建", "加一条", "记一条");
    }

    private boolean looksLikeGradeRecordDeleteRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "成绩", "分数", "得分", "分值")
                && containsAny(value, "删除", "移除", "撤销", "作废", "删掉", "删去");
    }

    private boolean looksLikeExamAnswerGradeRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "考试", "答卷", "答题", "主观题", "试题")
                && containsAny(value, "批改", "评分", "打分", "给分", "判分", "阅卷", "复核");
    }

    private boolean looksLikeExamSessionCloseRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "考试", "测验", "考核")
                && containsAny(value, "关闭", "结束", "终止", "停止", "收卷");
    }

    private boolean isEnabledTool(List<String> enabledTools, String... names) {
        Set<String> normalizedEnabledTools = new HashSet<>();
        for (String enabledTool : enabledTools) {
            normalizedEnabledTools.add(normalizeToolName(enabledTool));
        }
        for (String name : names) {
            if (normalizedEnabledTools.contains(normalizeToolName(name))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeToolName(String toolName) {
        if (isBlank(toolName)) {
            return "";
        }
        return toolName.trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
    }

    private boolean containsAny(String value, String... candidates) {
        if (value == null || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
