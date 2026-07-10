package com.cupk.aiacademy.agent.action;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AgentActionSpecRegistry {
    private final Map<String, AgentActionSpec> specsByToolName;
    private final Map<String, AgentActionSpec> specsByActionCode;

    public AgentActionSpecRegistry() {
        Map<String, AgentActionSpec> byToolName = new LinkedHashMap<>();
        Map<String, AgentActionSpec> byActionCode = new LinkedHashMap<>();
        registerLegacySpecs(byToolName, byActionCode);
        this.specsByToolName = Collections.unmodifiableMap(byToolName);
        this.specsByActionCode = Collections.unmodifiableMap(byActionCode);
    }

    public Optional<AgentActionSpec> findByToolName(String toolName) {
        return Optional.ofNullable(specsByToolName.get(normalizeToolName(toolName)));
    }

    public Optional<AgentActionSpec> findByActionCode(String actionCode) {
        return Optional.ofNullable(specsByActionCode.get(normalizeActionCode(actionCode)));
    }

    public List<AgentActionSpec> allSpecs() {
        return specsByActionCode.values().stream().toList();
    }

    private void registerLegacySpecs(Map<String, AgentActionSpec> byToolName,
                                     Map<String, AgentActionSpec> byActionCode) {
        register(byToolName, byActionCode, spec(
                        "chapter.update", "修改章节", "course", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备修改已有章节；执行前必须定位章节，并提供至少一个可修改字段。",
                        List.of(
                                id("chapterId", "章节ID", false, "优先使用章节ID定位章节", "章节编号", "id"),
                                text("chapterTitle", "原章节标题", false, "没有 chapterId 时用于唯一定位章节", 100, "原章节名", "章节名称", "章节名"),
                                id("courseId", "课程ID", false, "按章节标题定位时可用于消歧", "课程编号"),
                                text("courseTitle", "课程名称", false, "按章节标题定位时可用于消歧", 100, "课程名", "课程标题"),
                                text("title", "新的章节标题", false, "修改后的章节标题", 100, "新标题", "标题"),
                                text("description", "章节描述", false, "修改后的章节描述", 1000, "描述"),
                                intParam("sortOrder", "排序值", false, "章节在课程内的排序值", 0, null, "排序", "顺序"),
                                statusParam("status", "章节状态", false, "0 表示删除/停用，1 表示正常")
                        ),
                        List.of(List.of("chapterId", "chapterTitle"), List.of("title", "description", "sortOrder", "status")),
                        List.of("会修改章节基础信息或排序"), Duration.ofMinutes(10)),
                "chapter_update_plan", "course_chapter_update_plan", "chapter_modify_plan", "chapter_edit_plan");

        register(byToolName, byActionCode, spec(
                        "chapter.delete", "删除章节", "course", AgentActionRiskLevel.HIGH, "确认删除章节",
                        "根据用户自然语言请求准备软删除课程章节；执行前必须定位章节，并输入二次确认文本。",
                        List.of(
                                id("chapterId", "章节ID", false, "优先使用章节ID定位章节", "章节编号", "id"),
                                text("chapterTitle", "章节标题", false, "没有 chapterId 时用于唯一定位章节", 100, "章节名称", "章节名", "标题"),
                                id("courseId", "课程ID", false, "按章节标题定位时可用于消歧", "课程编号"),
                                text("courseTitle", "课程名称", false, "按章节标题定位时可用于消歧", 100, "课程名", "课程标题"),
                                text("reason", "删除原因", false, "删除或下线原因", 255)
                        ),
                        List.of(List.of("chapterId", "chapterTitle")),
                        List.of("会将章节状态改为删除或不可见", "可能影响章节下课时入口"), Duration.ofMinutes(10)),
                "chapter_delete_plan", "course_chapter_delete_plan", "chapter_remove_plan", "course_chapter_remove_plan");

        register(byToolName, byActionCode, spec(
                        "chapter.sort", "调整章节排序", "course", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备调整课程章节顺序；执行前必须定位课程并提供排序项列表。",
                        List.of(
                                id("courseId", "课程ID", false, "优先使用课程ID定位课程", "课程编号"),
                                text("courseTitle", "课程名称", false, "没有 courseId 时用于唯一定位课程", 100, "课程名", "课程标题"),
                                arrayParam("items", "排序项列表", true, "数组，每项包含 chapterId 和 sortOrder")
                        ),
                        List.of(List.of("courseId", "courseTitle")),
                        List.of("会批量修改章节排序"), Duration.ofMinutes(10)),
                "chapter_sort_plan", "course_chapter_sort_plan", "chapter_reorder_plan");

        register(byToolName, byActionCode, spec(
                        "lesson.create", "创建课时", "course", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备创建课程课时；执行前必须定位章节并补齐课时标题。",
                        lessonParameters(true, false),
                        List.of(List.of("chapterId", "chapterTitle")),
                        List.of("会新增一条课时记录", "课时会进入指定章节的内容结构"), Duration.ofMinutes(10)),
                "lesson_create_plan", "course_lesson_create_plan", "lesson_add_plan");

        register(byToolName, byActionCode, spec(
                        "lesson.update", "修改课时", "course", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备修改已有课时；执行前必须定位课时，并提供至少一个可修改字段。",
                        lessonParameters(false, true),
                        List.of(List.of("lessonId", "lessonTitle"),
                                List.of("chapterId", "title", "resourceType", "resourceUrl", "content", "videoUrl", "duration", "sortOrder", "status")),
                        List.of("会修改课时基础信息、资源或状态"), Duration.ofMinutes(10)),
                "lesson_update_plan", "course_lesson_update_plan", "lesson_modify_plan", "lesson_edit_plan");

        register(byToolName, byActionCode, spec(
                        "lesson.sort", "调整课时排序", "course", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备调整章节内课时顺序；执行前必须定位章节并提供排序项列表。",
                        List.of(
                                id("chapterId", "章节ID", false, "优先使用章节ID定位章节", "章节编号"),
                                text("chapterTitle", "章节标题", false, "没有 chapterId 时用于唯一定位章节", 100, "章节名", "章节名称"),
                                id("courseId", "课程ID", false, "按章节标题定位时可用于消歧", "课程编号"),
                                text("courseTitle", "课程名称", false, "按章节标题定位时可用于消歧", 100, "课程名", "课程标题"),
                                arrayParam("items", "排序项列表", true, "数组，每项包含 lessonId 和 sortOrder")
                        ),
                        List.of(List.of("chapterId", "chapterTitle")),
                        List.of("会批量修改课时排序"), Duration.ofMinutes(10)),
                "lesson_sort_plan", "course_lesson_sort_plan", "lesson_reorder_plan");

        register(byToolName, byActionCode, spec(
                        "lesson.status", "变更课时状态", "course", AgentActionRiskLevel.HIGH, "确认变更课时状态",
                        "根据用户自然语言请求准备上下架、停用或恢复课时；执行前必须定位课时并确认目标状态。",
                        List.of(
                                id("lessonId", "课时ID", false, "优先使用课时ID定位课时", "课时编号", "id"),
                                text("lessonTitle", "课时标题", false, "没有 lessonId 时用于唯一定位课时", 100, "课时名", "标题"),
                                id("chapterId", "章节ID", false, "按课时标题定位时可用于消歧", "章节编号"),
                                text("chapterTitle", "章节标题", false, "按课时标题定位时可用于消歧", 100, "章节名", "章节名称"),
                                statusParam("status", "目标状态", true, "只能是 0、1 或 2；0 表示下线/停用，1 表示上架/启用，2 表示草稿"),
                                text("reason", "变更原因", false, "状态变更原因", 255)
                        ),
                        List.of(List.of("lessonId", "lessonTitle")),
                        List.of("会改变课时可见或可用状态"), Duration.ofMinutes(10)),
                "lesson_status_plan", "course_lesson_status_plan", "lesson_archive_plan", "lesson_delete_plan", "lesson_remove_plan");

        register(byToolName, byActionCode, spec(
                        "course.resource.attach", "挂载课程资源", "course", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备给课时挂载视频、文档、资料链接或正文内容。",
                        List.of(
                                id("lessonId", "课时ID", false, "优先使用课时ID定位课时", "课时编号", "id"),
                                text("lessonTitle", "课时标题", false, "没有 lessonId 时用于唯一定位课时", 100, "课时名", "标题"),
                                id("chapterId", "章节ID", false, "按课时标题定位时可用于消歧", "章节编号"),
                                text("resourceType", "资源类型", false, "例如 video、document、link、text", 30, "类型"),
                                text("resourceUrl", "资源地址", false, "视频、文档或链接地址", 255, "资源链接", "url"),
                                text("videoUrl", "视频地址", false, "视频播放地址", 255, "视频链接"),
                                text("content", "正文内容", false, "课时正文内容", null, "正文")
                        ),
                        List.of(List.of("lessonId", "lessonTitle"), List.of("resourceUrl", "videoUrl", "content")),
                        List.of("会修改课时资源字段"), Duration.ofMinutes(10)),
                "course_resource_attach_plan", "lesson_resource_attach_plan", "course_resource_bind_plan", "lesson_resource_bind_plan");

        register(byToolName, byActionCode, spec(
                        "course.resource.remove", "移除课程资源", "course", AgentActionRiskLevel.HIGH, "确认移除课程资源",
                        "根据用户自然语言请求准备移除课时上的视频、文档、资料链接或正文资源。",
                        List.of(
                                id("lessonId", "课时ID", false, "优先使用课时ID定位课时", "课时编号", "id"),
                                text("lessonTitle", "课时标题", false, "没有 lessonId 时用于唯一定位课时", 100, "课时名", "标题"),
                                id("chapterId", "章节ID", false, "按课时标题定位时可用于消歧", "章节编号"),
                                text("reason", "移除原因", false, "资源移除原因", 255)
                        ),
                        List.of(List.of("lessonId", "lessonTitle")),
                        List.of("会清空课时资源字段"), Duration.ofMinutes(10)),
                "course_resource_remove_plan", "lesson_resource_remove_plan", "course_resource_delete_plan", "lesson_resource_delete_plan");

        registerCourseClassSpecs(byToolName, byActionCode);
        registerHomeworkSpecs(byToolName, byActionCode);
        registerExamSpecs(byToolName, byActionCode);
        registerGradeSpecs(byToolName, byActionCode);
    }

    private void registerCourseClassSpecs(Map<String, AgentActionSpec> byToolName,
                                          Map<String, AgentActionSpec> byActionCode) {
        register(byToolName, byActionCode, spec(
                        "teaching.class.create", "创建教学班", "course", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备创建教学班；执行前必须定位课程并补齐教学班名称。",
                        List.of(
                                id("courseId", "课程ID", false, "优先使用课程ID定位课程", "课程编号"),
                                text("courseTitle", "课程名称", false, "没有 courseId 时用于唯一定位课程", 100, "课程名", "课程标题"),
                                text("className", "教学班名称", true, "要创建的教学班名称", 100, "班级名", "班级名称"),
                                id("teacherId", "教师ID", false, "未填写时通常使用当前登录教师", "教师编号"),
                                text("teacherName", "教师名称", false, "没有 teacherId 时用于定位教师", 50, "教师", "老师"),
                                text("semester", "学期", false, "例如 2026 春", 50),
                                text("description", "教学班描述", false, "教学班说明", 255),
                                statusParam("status", "教学班状态", false, "0 表示停用，1 表示启用")
                        ),
                        List.of(List.of("courseId", "courseTitle")),
                        List.of("会新增一个教学班"), Duration.ofMinutes(10)),
                "teaching_class_create_plan", "class_create_plan", "teaching_class_add_plan", "class_add_plan");

        register(byToolName, byActionCode, spec(
                        "teaching.class.update", "修改教学班", "course", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备修改教学班；执行前必须定位教学班，并提供至少一个可修改字段。",
                        List.of(
                                id("classId", "教学班ID", false, "优先使用教学班ID定位", "班级ID", "教学班编号", "id"),
                                text("originalClassName", "原教学班名称", false, "没有 classId 时用于唯一定位教学班", 100, "原班级名", "原名称"),
                                text("className", "新的教学班名称", false, "修改后的教学班名称", 100, "新班级名", "新名称", "班级名", "班级名称"),
                                id("courseId", "课程ID", false, "新的课程ID", "课程编号"),
                                text("courseTitle", "课程名称", false, "没有 courseId 时用于定位课程", 100, "课程名", "课程标题"),
                                id("teacherId", "教师ID", false, "新的教师ID", "教师编号"),
                                text("teacherName", "教师名称", false, "没有 teacherId 时用于定位教师", 50, "教师", "老师"),
                                text("semester", "学期", false, "例如 2026 春", 50),
                                text("description", "教学班描述", false, "教学班说明", 255),
                                statusParam("status", "教学班状态", false, "0 表示停用，1 表示启用")
                        ),
                        List.of(List.of("classId", "originalClassName"),
                                List.of("className", "courseId", "courseTitle", "teacherId", "teacherName", "semester", "description", "status")),
                        List.of("会修改教学班基础信息"), Duration.ofMinutes(10)),
                "teaching_class_update_plan", "class_update_plan", "teaching_class_modify_plan", "class_modify_plan", "teaching_class_edit_plan");

        register(byToolName, byActionCode, spec(
                        "teaching.class.status", "变更教学班状态", "course", AgentActionRiskLevel.HIGH, "确认变更教学班状态",
                        "根据用户自然语言请求准备结束、停用或恢复教学班；执行前必须定位教学班并确认目标状态。",
                        List.of(
                                id("classId", "教学班ID", false, "优先使用教学班ID定位", "班级ID", "教学班编号", "id"),
                                text("className", "教学班名称", false, "没有 classId 时用于唯一定位教学班", 100, "班级名", "班级名称"),
                                statusParam("status", "目标状态", true, "0 表示停用，1 表示启用"),
                                text("reason", "变更原因", false, "状态变更原因", 255)
                        ),
                        List.of(List.of("classId", "className", "courseId", "courseTitle")),
                        List.of("会改变教学班可用状态"), Duration.ofMinutes(10)),
                "teaching_class_status_plan", "class_status_plan", "teaching_class_archive_plan", "teaching_class_delete_plan", "class_delete_plan");

        register(byToolName, byActionCode, spec(
                        "class.student.add", "添加班级学生", "course", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备把学生加入教学班；执行前必须定位教学班和学生。",
                        classStudentParameters(false, false),
                        List.of(List.of("classId", "className"), List.of("studentId", "studentName")),
                        List.of("会新增一条教学班学生关系"), Duration.ofMinutes(10)),
                "class_student_add_plan", "teaching_class_student_add_plan", "class_member_add_plan", "student_enroll_plan");

        register(byToolName, byActionCode, spec(
                        "class.student.remove", "移除班级学生", "course", AgentActionRiskLevel.HIGH, "确认移除班级学生",
                        "根据用户自然语言请求准备从教学班学生名单中移除学生；执行前必须定位名单记录。",
                        classStudentParameters(true, false),
                        List.of(List.of("classStudentId", "classId", "className"),
                                List.of("classStudentId", "studentId", "studentName")),
                        List.of("会移除学生与教学班的关联"), Duration.ofMinutes(10)),
                "class_student_remove_plan", "class_student_delete_plan", "teaching_class_student_remove_plan", "class_member_remove_plan", "student_unenroll_plan");

        register(byToolName, byActionCode, spec(
                        "class.student.status", "变更班级学生状态", "course", AgentActionRiskLevel.HIGH, "确认变更班级学生状态",
                        "根据用户自然语言请求准备调整教学班学生状态；执行前必须定位名单记录并确认目标状态。",
                        classStudentParameters(true, true),
                        List.of(List.of("classStudentId", "classId", "className"),
                                List.of("classStudentId", "studentId", "studentName")),
                        List.of("会改变学生在教学班内的状态"), Duration.ofMinutes(10)),
                "class_student_status_plan", "class_member_status_plan", "class_student_update_status_plan", "student_enrollment_status_plan");
    }

    private void registerHomeworkSpecs(Map<String, AgentActionSpec> byToolName,
                                       Map<String, AgentActionSpec> byActionCode) {
        register(byToolName, byActionCode, spec(
                        "homework.create", "发布作业", "homework", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备发布作业；执行前必须定位教学班并补齐作业标题。",
                        List.of(
                                id("classId", "教学班ID", false, "优先使用教学班ID定位", "班级ID", "教学班编号"),
                                text("className", "教学班名称", false, "没有 classId 时用于唯一定位教学班", 100, "班级名", "班级名称"),
                                id("courseId", "课程ID", false, "按课程定位教学班时使用", "课程编号"),
                                text("courseTitle", "课程名称", false, "没有 courseId 时用于定位课程，并尝试定位唯一教学班", 100,
                                        "courseName", "课程名", "课程标题", "课名"),
                                id("teacherId", "教师ID", false, "未填写时通常使用当前登录教师", "教师编号"),
                                text("teacherName", "教师名称", false, "没有 teacherId 时用于定位教师", 50, "教师", "老师"),
                                text("title", "作业标题", true, "要发布的作业标题", 100, "作业名", "标题"),
                                text("content", "作业内容", false, "作业正文", null, "正文"),
                                text("attachmentUrl", "附件地址", false, "作业附件地址", 255, "附件"),
                                numberParam("fullScore", "满分", false, "作业满分", BigDecimal.ZERO, null, "总分"),
                                dateTime("startTime", "开始时间", false),
                                dateTime("deadline", "截止时间", false, "endTime"),
                                statusParam("status", "作业状态", false, "0 表示停用，1 表示发布")
                        ),
                        List.of(List.of("classId", "className", "courseId", "courseTitle")),
                        List.of("会新增一条作业记录"), Duration.ofMinutes(10)),
                "homework_create_plan", "homework_publish_plan", "assignment_create_plan");

        register(byToolName, byActionCode, spec(
                        "homework.update", "修改作业", "homework", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备修改已有作业；执行前必须定位作业，并提供至少一个可修改字段。",
                        homeworkUpdateParameters(),
                        List.of(List.of("homeworkId", "homeworkTitle"),
                                List.of("title", "content", "attachmentUrl", "fullScore", "startTime", "deadline", "status")),
                        List.of("会修改作业基础信息"), Duration.ofMinutes(10)),
                "homework_update_plan", "homework_modify_plan", "assignment_update_plan", "assignment_modify_plan");

        register(byToolName, byActionCode, spec(
                        "homework.delete", "删除作业", "homework", AgentActionRiskLevel.HIGH, "确认删除作业",
                        "根据用户自然语言请求准备删除已有作业；执行前必须定位作业。",
                        List.of(
                                id("homeworkId", "作业ID", false, "优先使用作业ID定位作业", "作业编号", "id"),
                                text("homeworkTitle", "作业标题", false, "没有 homeworkId 时用于唯一定位作业", 100, "作业名", "标题"),
                                id("classId", "教学班ID", false, "按作业标题定位时可用于消歧", "班级ID"),
                                text("className", "教学班名称", false, "按作业标题定位时可用于消歧", 100, "班级名"),
                                id("courseId", "课程ID", false, "按作业标题定位时可用于消歧", "课程编号"),
                                text("courseTitle", "课程名称", false, "按作业标题定位时可用于消歧", 100,
                                        "courseName", "课程名", "课程标题", "课名"),
                                text("reason", "删除原因", false, "删除原因", 255)
                        ),
                        List.of(List.of("homeworkId", "homeworkTitle")),
                        List.of("会删除或下线作业记录"), Duration.ofMinutes(10)),
                "homework_delete_plan", "homework_remove_plan", "assignment_delete_plan", "assignment_remove_plan");

        register(byToolName, byActionCode, spec(
                        "homework.submission.grade", "批改作业提交", "homework", AgentActionRiskLevel.HIGH, "确认批改作业",
                        "根据用户自然语言请求准备批改一条作业提交；执行前必须定位提交并补齐得分。",
                        List.of(
                                id("homeworkSubmitId", "作业提交ID", true, "作业提交记录ID", "提交ID", "submitId"),
                                numberParam("score", "得分", true, "批改分数", BigDecimal.ZERO, null, "分数"),
                                text("feedback", "反馈", false, "批改反馈", 1000, "评语")
                        ),
                        List.of(),
                        List.of("会写入作业提交分数和反馈"), Duration.ofMinutes(10)),
                "homework_submission_grade_plan", "homework_grade_plan", "homework_submit_grade_plan", "assignment_grade_plan");
    }

    private void registerExamSpecs(Map<String, AgentActionSpec> byToolName,
                                   Map<String, AgentActionSpec> byActionCode) {
        register(byToolName, byActionCode, spec(
                        "exam.session.create", "创建考试安排", "exam", AgentActionRiskLevel.HIGH, "确认创建考试",
                        "根据用户自然语言请求准备创建考试安排；执行前必须定位教学班、试卷并补齐考试名称、时间和时长。",
                        List.of(
                                id("classId", "教学班ID", false, "优先使用教学班ID定位", "班级ID"),
                                text("className", "教学班名称", false, "没有 classId 时用于唯一定位教学班", 100, "班级名"),
                                id("paperId", "试卷ID", false, "优先使用试卷ID定位试卷", "试卷编号"),
                                text("paperTitle", "试卷名称", false, "没有 paperId 时用于唯一定位试卷", 100, "试卷标题"),
                                id("teacherId", "教师ID", false, "未填写时通常使用当前登录教师", "教师编号"),
                                text("teacherName", "教师名称", false, "没有 teacherId 时用于定位教师", 50, "教师", "老师"),
                                text("name", "考试名称", true, "考试安排名称", 100, "标题", "考试标题"),
                                text("description", "考试说明", false, "考试说明", 255),
                                dateTime("startTime", "开始时间", true),
                                dateTime("endTime", "结束时间", true),
                                intParam("durationMinutes", "考试时长分钟", true, "考试时长，单位分钟", 1, null, "duration")
                        ),
                        List.of(List.of("classId", "className"), List.of("paperId", "paperTitle")),
                        List.of("会创建考试安排并影响学生考试入口"), Duration.ofMinutes(10)),
                "exam_session_create_plan", "exam_create_plan", "exam_publish_plan", "exam_session_publish_plan");

        register(byToolName, byActionCode, spec(
                        "exam.session.close", "关闭考试安排", "exam", AgentActionRiskLevel.HIGH, "确认关闭考试",
                        "根据用户自然语言请求准备关闭考试安排；执行前必须定位考试安排。",
                        List.of(
                                id("examSessionId", "考试安排ID", false, "优先使用考试安排ID定位", "考试ID", "id"),
                                text("examSessionTitle", "考试安排名称", false, "没有 examSessionId 时用于唯一定位考试安排", 100, "考试名称", "标题"),
                                text("reason", "关闭原因", false, "关闭原因", 255)
                        ),
                        List.of(List.of("examSessionId", "examSessionTitle")),
                        List.of("会关闭考试安排"), Duration.ofMinutes(10)),
                "exam_session_close_plan", "exam_close_plan", "exam_end_plan", "exam_session_end_plan", "exam_session_finish_plan");

        register(byToolName, byActionCode, spec(
                        "exam.answer.grade", "批改考试答题", "exam", AgentActionRiskLevel.HIGH, "确认批改考试",
                        "根据用户自然语言请求准备批改考试答题；执行前必须补齐答卷ID、答题记录ID和分数。",
                        List.of(
                                id("submissionId", "考试提交ID", true, "考试提交或答卷ID", "提交ID", "recordId"),
                                id("submissionAnswerId", "答题记录ID", true, "单题答题记录ID", "answerId"),
                                numberParam("score", "得分", true, "批改分数", BigDecimal.ZERO, null, "分数"),
                                text("feedback", "反馈", false, "批改反馈", 1000, "评语")
                        ),
                        List.of(),
                        List.of("会写入考试答题分数和反馈"), Duration.ofMinutes(10)),
                "exam_answer_grade_plan", "exam_manual_grade_plan", "exam_submission_grade_plan", "exam_answer_score_plan");

        registerPaperSpecs(byToolName, byActionCode);
        registerQuestionSpecs(byToolName, byActionCode);
    }

    private void registerPaperSpecs(Map<String, AgentActionSpec> byToolName,
                                    Map<String, AgentActionSpec> byActionCode) {
        register(byToolName, byActionCode, spec(
                        "exam.paper.create", "创建试卷", "exam", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备创建考试试卷；执行前必须定位课程并补齐试卷名称。",
                        paperParameters(true, false),
                        List.of(List.of("courseId", "courseTitle")),
                        List.of("会新增一张试卷"), Duration.ofMinutes(10)),
                "exam_paper_create_plan", "exam_paper_add_plan", "paper_create_plan");

        register(byToolName, byActionCode, spec(
                        "exam.paper.update", "修改试卷", "exam", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备修改考试试卷基础信息；执行前必须定位试卷，并提供至少一个可修改字段。",
                        paperParameters(false, true),
                        List.of(List.of("paperId", "paperTitle"),
                                List.of("courseId", "courseTitle", "teacherId", "teacherName", "title", "description", "totalScore", "durationMinutes", "status")),
                        List.of("会修改试卷基础信息"), Duration.ofMinutes(10)),
                "exam_paper_update_plan", "exam_paper_modify_plan", "paper_update_plan");

        register(byToolName, byActionCode, spec(
                        "exam.paper.delete", "删除试卷", "exam", AgentActionRiskLevel.HIGH, "确认删除试卷",
                        "根据用户自然语言请求准备软删除考试试卷；执行前必须定位试卷。",
                        List.of(
                                id("paperId", "试卷ID", false, "优先使用试卷ID定位", "试卷编号", "id"),
                                text("paperTitle", "试卷名称", false, "没有 paperId 时用于唯一定位试卷", 100, "试卷标题", "标题"),
                                id("courseId", "课程ID", false, "按试卷名称定位时可用于消歧", "课程编号"),
                                text("courseTitle", "课程名称", false, "按试卷名称定位时可用于消歧", 100, "课程名", "课程标题"),
                                text("reason", "删除原因", false, "删除原因", 255)
                        ),
                        List.of(List.of("paperId", "paperTitle")),
                        List.of("会删除或下线试卷"), Duration.ofMinutes(10)),
                "exam_paper_delete_plan", "exam_paper_remove_plan", "paper_delete_plan");

        register(byToolName, byActionCode, spec(
                        "exam.paper.question.add", "试卷添加题目", "exam", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备把题库题目加入试卷；执行前必须定位试卷和题目。",
                        paperQuestionParameters(false, false),
                        List.of(List.of("paperId", "paperTitle"), List.of("questionId", "questionTitle")),
                        List.of("会新增试卷题目关系"), Duration.ofMinutes(10)),
                "exam_paper_question_add_plan", "paper_question_add_plan", "exam_question_add_to_paper_plan");

        register(byToolName, byActionCode, spec(
                        "exam.paper.question.remove", "试卷移除题目", "exam", AgentActionRiskLevel.HIGH, "确认移除试卷题目",
                        "根据用户自然语言请求准备从试卷移除题目；执行前必须定位试卷题目关系。",
                        paperQuestionParameters(true, false),
                        List.of(List.of("paperId", "paperTitle"),
                                List.of("paperQuestionId", "questionId", "questionTitle")),
                        List.of("会从试卷中移除题目"), Duration.ofMinutes(10)),
                "exam_paper_question_remove_plan", "paper_question_remove_plan", "exam_question_remove_from_paper_plan");

        register(byToolName, byActionCode, spec(
                        "exam.paper.question.update", "调整试卷题目", "exam", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备调整试卷题目分值、顺序或必答设置。",
                        paperQuestionParameters(true, true),
                        List.of(List.of("paperId", "paperTitle"),
                                List.of("paperQuestionId", "questionId", "questionTitle"),
                                List.of("score", "sortOrder", "requiredAnswer")),
                        List.of("会修改试卷题目配置"), Duration.ofMinutes(10)),
                "exam_paper_question_update_plan", "paper_question_update_plan", "exam_paper_question_modify_plan");
    }

    private void registerQuestionSpecs(Map<String, AgentActionSpec> byToolName,
                                       Map<String, AgentActionSpec> byActionCode) {
        register(byToolName, byActionCode, spec(
                        "question.create", "创建试题", "exam", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备创建题库试题；执行前必须定位课程、补齐题型、题干和标准答案。",
                        questionParameters(true, false),
                        List.of(List.of("courseId", "courseTitle")),
                        List.of("会新增一条题库记录"), Duration.ofMinutes(10)),
                "question_create_plan", "exam_question_create_plan", "question_add_plan", "question_bank_create_plan");

        register(byToolName, byActionCode, spec(
                        "question.update", "修改试题", "exam", AgentActionRiskLevel.MEDIUM, "",
                        "根据用户自然语言请求准备修改题库试题；执行前必须定位试题，并提供至少一个可修改字段。",
                        questionParameters(false, true),
                        List.of(List.of("questionId", "questionTitle"),
                                List.of("courseId", "courseTitle", "type", "content", "options", "answers", "difficulty", "score", "knowledgePoint", "analysis", "status")),
                        List.of("会修改题库试题内容或答案"), Duration.ofMinutes(10)),
                "question_update_plan", "exam_question_update_plan", "question_modify_plan", "question_edit_plan");

        register(byToolName, byActionCode, spec(
                        "question.delete", "删除试题", "exam", AgentActionRiskLevel.HIGH, "确认删除试题",
                        "根据用户自然语言请求准备删除题库试题；执行前必须定位试题。",
                        List.of(
                                id("questionId", "试题ID", false, "优先使用试题ID定位", "题目ID", "id"),
                                text("questionTitle", "试题标题或题干", false, "没有 questionId 时用于唯一定位试题", 500, "题干", "题目"),
                                id("courseId", "课程ID", false, "按试题标题定位时可用于消歧", "课程编号"),
                                text("courseTitle", "课程名称", false, "按试题标题定位时可用于消歧", 100, "课程名", "课程标题"),
                                text("reason", "删除原因", false, "删除原因", 255)
                        ),
                        List.of(List.of("questionId", "questionTitle")),
                        List.of("会删除或下线题库试题"), Duration.ofMinutes(10)),
                "question_delete_plan", "exam_question_delete_plan", "question_remove_plan", "question_bank_delete_plan");
    }

    private void registerGradeSpecs(Map<String, AgentActionSpec> byToolName,
                                    Map<String, AgentActionSpec> byActionCode) {
        register(byToolName, byActionCode, spec(
                        "grade.record.create", "录入成绩记录", "grade", AgentActionRiskLevel.HIGH, "确认录入成绩",
                        "根据用户自然语言请求准备录入一条成绩记录；执行前必须定位学生、教学班、课程并补齐成绩名称和分数。",
                        List.of(
                                id("studentId", "学生ID", false, "优先使用学生ID定位", "学生编号"),
                                text("studentName", "学生名称", false, "没有 studentId 时用于定位学生", 50, "学生", "学生姓名"),
                                id("classId", "教学班ID", false, "优先使用教学班ID定位", "班级ID"),
                                text("className", "教学班名称", false, "没有 classId 时用于定位教学班", 100, "班级名"),
                                id("courseId", "课程ID", false, "优先使用课程ID定位", "课程编号"),
                                text("courseTitle", "课程名称", false, "没有 courseId 时用于定位课程", 100, "课程名", "课程标题"),
                                id("teacherId", "教师ID", false, "未填写时通常使用当前登录教师", "教师编号"),
                                text("teacherName", "教师名称", false, "没有 teacherId 时用于定位教师", 50, "教师", "老师"),
                                text("gradeName", "成绩名称", true, "例如 第一次测验、期末成绩", 100, "名称"),
                                numberParam("score", "分数", true, "成绩分数", BigDecimal.ZERO, null, "得分"),
                                text("comment", "评语", false, "成绩备注", 255, "备注")
                        ),
                        List.of(List.of("studentId", "studentName"), List.of("classId", "className"), List.of("courseId", "courseTitle")),
                        List.of("会新增一条成绩记录"), Duration.ofMinutes(10)),
                "grade_record_create_plan", "grade_create_plan", "score_create_plan", "grade_input_plan", "score_input_plan");

        register(byToolName, byActionCode, spec(
                        "grade.record.update", "修改成绩记录", "grade", AgentActionRiskLevel.HIGH, "确认修改成绩",
                        "根据用户自然语言请求准备修改已有成绩记录；执行前必须定位成绩记录并补齐新分数。",
                        List.of(
                                id("gradeRecordId", "成绩记录ID", true, "成绩记录ID", "成绩ID", "id"),
                                numberParam("score", "分数", true, "新的成绩分数", BigDecimal.ZERO, null, "得分"),
                                text("comment", "评语", false, "成绩备注", 255, "备注")
                        ),
                        List.of(),
                        List.of("会修改成绩记录分数或备注"), Duration.ofMinutes(10)),
                "grade_record_update_plan", "grade_update_plan", "score_update_plan", "score_modify_plan", "grade_modify_plan");

        register(byToolName, byActionCode, spec(
                        "grade.record.delete", "删除成绩记录", "grade", AgentActionRiskLevel.HIGH, "确认删除成绩",
                        "根据用户自然语言请求准备删除一条成绩记录；执行前必须补齐成绩记录ID。",
                        List.of(
                                id("gradeRecordId", "成绩记录ID", true, "成绩记录ID", "成绩ID", "id"),
                                text("reason", "删除原因", false, "删除原因", 255)
                        ),
                        List.of(),
                        List.of("会删除成绩记录"), Duration.ofMinutes(10)),
                "grade_record_delete_plan", "grade_delete_plan", "score_delete_plan", "grade_remove_plan", "score_remove_plan");
    }

    private List<AgentActionParameterSpec> lessonParameters(boolean create, boolean update) {
        return List.of(
                id("lessonId", "课时ID", !create && !update, "优先使用课时ID定位课时", "课时编号", "id"),
                text("lessonTitle", "原课时标题", false, "没有 lessonId 时用于唯一定位课时", 100, "课时名", "原标题"),
                id("chapterId", "章节ID", false, "优先使用章节ID定位章节", "章节编号"),
                text("chapterTitle", "章节标题", false, "没有 chapterId 时用于唯一定位章节", 100, "章节名", "章节名称"),
                id("courseId", "课程ID", false, "按章节或课时标题定位时可用于消歧", "课程编号"),
                text("courseTitle", "课程名称", false, "按章节或课时标题定位时可用于消歧", 100, "课程名", "课程标题"),
                text("title", "课时标题", create, create ? "要创建的课时标题" : "新的课时标题", 100, "标题", "名称"),
                text("resourceType", "资源类型", false, "例如 video、document、link、text", 30, "类型"),
                text("resourceUrl", "资源地址", false, "视频、文档或链接地址", 255, "资源链接", "url"),
                text("content", "正文内容", false, "课时正文内容", null, "正文"),
                text("videoUrl", "视频地址", false, "视频播放地址", 255, "视频链接"),
                intParam("duration", "时长秒", false, "课时时长，单位秒", 0, null, "时长"),
                intParam("sortOrder", "排序值", false, "课时在章节内的排序值", 0, null, "排序", "顺序"),
                statusParam("status", "课时状态", false, "0 表示下线/停用，1 表示上架/启用，2 表示草稿")
        );
    }

    private List<AgentActionParameterSpec> classStudentParameters(boolean includeRelationId, boolean includeStatus) {
        List<AgentActionParameterSpec> base = new java.util.ArrayList<>();
        if (includeRelationId) {
            base.add(id("classStudentId", "班级学生关系ID", false, "优先使用关系ID定位", "关系ID", "id"));
        }
        base.add(id("classId", "教学班ID", false, "优先使用教学班ID定位", "班级ID"));
        base.add(text("className", "教学班名称", false, "没有 classId 时用于定位教学班", 100, "班级名"));
        base.add(id("studentId", "学生ID", false, "优先使用学生ID定位", "学生编号"));
        base.add(text("studentName", "学生名称", false, "没有 studentId 时用于定位学生", 50, "学生", "学生姓名"));
        if (includeStatus) {
            base.add(statusParam("status", "目标状态", true, "0 表示停用，1 表示启用"));
        }
        return List.copyOf(base);
    }

    private List<AgentActionParameterSpec> homeworkUpdateParameters() {
        return List.of(
                id("homeworkId", "作业ID", false, "优先使用作业ID定位作业", "作业编号", "id"),
                text("homeworkTitle", "原作业标题", false, "没有 homeworkId 时用于唯一定位作业", 100, "作业名", "原标题"),
                id("classId", "教学班ID", false, "按作业标题定位时可用于消歧", "班级ID"),
                text("className", "教学班名称", false, "按作业标题定位时可用于消歧", 100, "班级名"),
                id("courseId", "课程ID", false, "按作业标题定位时可用于消歧", "课程编号"),
                text("courseTitle", "课程名称", false, "按作业标题定位时可用于消歧", 100,
                        "courseName", "课程名", "课程标题", "课名"),
                text("title", "新的作业标题", false, "修改后的作业标题", 100, "新标题", "标题"),
                text("content", "作业内容", false, "作业正文", null, "正文"),
                text("attachmentUrl", "附件地址", false, "作业附件地址", 255, "附件"),
                numberParam("fullScore", "满分", false, "作业满分", BigDecimal.ZERO, null, "总分"),
                dateTime("startTime", "开始时间", false),
                dateTime("deadline", "截止时间", false, "endTime"),
                statusParam("status", "作业状态", false, "0 表示停用，1 表示发布")
        );
    }

    private List<AgentActionParameterSpec> paperParameters(boolean create, boolean update) {
        return List.of(
                id("paperId", "试卷ID", !create && !update, "优先使用试卷ID定位试卷", "试卷编号", "id"),
                text("paperTitle", "原试卷名称", false, "没有 paperId 时用于唯一定位试卷", 100, "试卷标题", "原标题"),
                id("courseId", "课程ID", false, "优先使用课程ID定位课程", "课程编号"),
                text("courseTitle", "课程名称", false, "没有 courseId 时用于唯一定位课程", 100, "课程名", "课程标题"),
                id("teacherId", "教师ID", false, "未填写时通常使用当前登录教师", "教师编号"),
                text("teacherName", "教师名称", false, "没有 teacherId 时用于定位教师", 50, "教师", "老师"),
                text("title", "试卷名称", create, create ? "要创建的试卷名称" : "新的试卷名称", 100, "标题", "名称"),
                text("description", "试卷描述", false, "试卷说明", 255),
                numberParam("totalScore", "总分", false, "试卷总分", BigDecimal.ZERO, null, "fullScore", "score"),
                intParam("durationMinutes", "考试时长分钟", false, "考试时长，单位分钟", 1, null, "duration"),
                statusParam("status", "试卷状态", false, "0 表示停用，1 表示启用")
        );
    }

    private List<AgentActionParameterSpec> paperQuestionParameters(boolean includePaperQuestionId, boolean update) {
        List<AgentActionParameterSpec> base = new java.util.ArrayList<>();
        base.add(id("paperId", "试卷ID", false, "优先使用试卷ID定位试卷", "试卷编号"));
        base.add(text("paperTitle", "试卷名称", false, "没有 paperId 时用于唯一定位试卷", 100, "试卷标题"));
        if (includePaperQuestionId) {
            base.add(id("paperQuestionId", "试卷题目关系ID", false, "优先使用关系ID定位", "关系ID", "id"));
        }
        base.add(id("questionId", "试题ID", false, "优先使用试题ID定位试题", "题目ID"));
        base.add(text("questionTitle", "试题标题或题干", false, "没有 questionId 时用于唯一定位试题", 500, "题干", "题目"));
        base.add(intParam("sortOrder", "排序值", false, "题目在试卷中的排序值", 0, null, "排序", "顺序"));
        base.add(numberParam("score", "题目分值", false, "该题在试卷中的分值", BigDecimal.ZERO, null, "分值"));
        base.add(intParam("requiredAnswer", "是否必答", false, "0 表示非必答，1 表示必答", 0, 1, "required"));
        return List.copyOf(base);
    }

    private List<AgentActionParameterSpec> questionParameters(boolean create, boolean update) {
        return List.of(
                id("questionId", "试题ID", !create && !update, "优先使用试题ID定位试题", "题目ID", "id"),
                text("questionTitle", "原试题标题或题干", false, "没有 questionId 时用于唯一定位试题", 500, "题干", "题目", "原标题"),
                id("courseId", "课程ID", false, "优先使用课程ID定位课程", "课程编号"),
                text("courseTitle", "课程名称", false, "没有 courseId 时用于唯一定位课程", 100, "课程名", "课程标题"),
                id("teacherId", "教师ID", false, "未填写时通常使用当前登录教师", "教师编号"),
                text("teacherName", "教师名称", false, "没有 teacherId 时用于定位教师", 50, "教师", "老师"),
                text("type", "题型", create, "例如 单选题、多选题、判断题、填空题、简答题", 50, "questionType"),
                text("content", "题干", create, "试题题干内容", 500, "title", "questionContent"),
                text("options", "选项", false, "选择题选项，可填 JSON 数组或文本", null, "choices"),
                text("answers", "标准答案", create, "标准答案，可填 A 或 JSON 数组", null, "answer", "correctAnswers"),
                text("difficulty", "难度", false, "例如 EASY、MEDIUM、HARD 或 简单、中等、困难", 30, "level"),
                numberParam("score", "分值", false, "试题默认分值", BigDecimal.ZERO, null, "points"),
                text("knowledgePoint", "知识点", false, "关联知识点", 200, "knowledge_point"),
                text("analysis", "解析", false, "答案解析", 1000, "explanation"),
                statusParam("status", "试题状态", false, "0 表示停用，1 表示启用")
        );
    }

    private AgentActionSpec spec(String actionCode,
                                 String actionName,
                                 String domain,
                                 AgentActionRiskLevel riskLevel,
                                 String confirmationText,
                                 String summary,
                                 List<AgentActionParameterSpec> parameters,
                                 List<List<String>> requiredAnyOf,
                                 List<String> impacts,
                                 Duration pendingTtl) {
        return new AgentActionSpec(actionCode, actionName, domain, riskLevel, true,
                confirmationText, summary, parameters, requiredAnyOf, impacts, pendingTtl);
    }

    private AgentActionParameterSpec id(String name, String label, boolean required, String description, String... aliases) {
        return new AgentActionParameterSpec(name, label, required, description,
                AgentActionParameterType.INTEGER, List.of(), List.of(aliases), List.of(), null, BigDecimal.ONE, null, "");
    }

    private AgentActionParameterSpec text(String name,
                                          String label,
                                          boolean required,
                                          String description,
                                          Integer maxLength,
                                          String... aliases) {
        return new AgentActionParameterSpec(name, label, required, description,
                AgentActionParameterType.STRING, List.of(), List.of(aliases), List.of(), maxLength, null, null, "");
    }

    private AgentActionParameterSpec dateTime(String name,
                                              String label,
                                              boolean required,
                                              String... aliases) {
        return new AgentActionParameterSpec(name, label, required,
                "绝对时间，格式 yyyy-MM-dd HH:mm:ss",
                AgentActionParameterType.STRING,
                List.of(),
                List.of(aliases),
                List.of("2026-07-17 20:00:00"),
                30,
                null,
                null,
                "^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(?::\\d{2})?$");
    }

    private AgentActionParameterSpec intParam(String name,
                                             String label,
                                             boolean required,
                                             String description,
                                             Integer minimum,
                                             Integer maximum,
                                             String... aliases) {
        return new AgentActionParameterSpec(name, label, required, description,
                AgentActionParameterType.INTEGER, List.of(), List.of(aliases), List.of(), null,
                minimum == null ? null : BigDecimal.valueOf(minimum),
                maximum == null ? null : BigDecimal.valueOf(maximum), "");
    }

    private AgentActionParameterSpec numberParam(String name,
                                                String label,
                                                boolean required,
                                                String description,
                                                BigDecimal minimum,
                                                BigDecimal maximum,
                                                String... aliases) {
        return new AgentActionParameterSpec(name, label, required, description,
                AgentActionParameterType.NUMBER, List.of(), List.of(aliases), List.of(), null, minimum, maximum, "");
    }

    private AgentActionParameterSpec statusParam(String name, String label, boolean required, String description) {
        return new AgentActionParameterSpec(name, label, required, description,
                AgentActionParameterType.INTEGER, List.of("0", "1", "2"), List.of("状态"), List.of(), null,
                BigDecimal.ZERO, BigDecimal.valueOf(2), "");
    }

    private AgentActionParameterSpec arrayParam(String name, String label, boolean required, String description) {
        return new AgentActionParameterSpec(name, label, required, description,
                AgentActionParameterType.ARRAY, List.of(), List.of(), List.of());
    }

    private void register(Map<String, AgentActionSpec> byToolName,
                          Map<String, AgentActionSpec> byActionCode,
                          AgentActionSpec spec,
                          String... toolNames) {
        byActionCode.putIfAbsent(normalizeActionCode(spec.actionCode()), spec);
        if (toolNames == null) {
            return;
        }
        for (String toolName : toolNames) {
            String normalized = normalizeToolName(toolName);
            if (!normalized.isEmpty()) {
                byToolName.putIfAbsent(normalized, spec);
            }
        }
    }

    private String normalizeToolName(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return "";
        }
        return toolName.trim()
                .toLowerCase(java.util.Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
    }

    private String normalizeActionCode(String actionCode) {
        if (actionCode == null || actionCode.trim().isEmpty()) {
            return "";
        }
        return actionCode.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
