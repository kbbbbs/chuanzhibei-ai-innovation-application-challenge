from __future__ import annotations

import copy
import re
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from docx import Document
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt


ROOT = Path(r"D:\ai-learning")
INPUT = Path(r"D:\PPT\web应用开发综合实践\最终版_第三章更新.docx")
OUTPUT = ROOT / "outputs" / "appendix_refresh" / "final_appendix_updated.docx"
JAVA_ROOT = ROOT / "ai-learning-backend" / "src" / "main" / "java"
SCHEMA = ROOT / "database" / "ai_academy_schema.sql"


@dataclass(frozen=True)
class Endpoint:
    controller: str
    http_method: str
    path: str
    method_name: str
    source_order: int


@dataclass(frozen=True)
class Meta:
    module_no: int
    module_name: str
    tables: tuple[str, ...]
    pages: str
    components: tuple[str, ...]


META: dict[str, Meta] = {
    "AuthController": Meta(1, "用户与权限", ("t_user", "t_role", "t_user_role"), "登录、注册", ("Login.vue", "Register.vue")),
    "UserController": Meta(1, "用户与权限", ("t_user", "t_role", "t_user_role"), "用户管理、个人设置", ("UserManage.vue", "ProfileSettings.vue")),
    "RoleController": Meta(1, "用户与权限", ("t_role", "t_user_role"), "用户管理", ("UserManage.vue",)),
    "TeacherApplicationController": Meta(1, "用户与权限", ("t_teacher_application", "t_user", "t_role", "t_user_role"), "注册、教师审核", ("Register.vue", "TeacherApplications.vue")),
    "DashboardController": Meta(2, "首页统计", ("t_teaching_class", "t_exam_session", "t_exam_record", "t_homework_session", "t_homework_record"), "控制台", ("Home.vue",)),
    "CategoryController": Meta(3, "课程与分类", ("t_course_category", "t_course"), "分类管理、课程管理", ("CategoryManage.vue", "CourseList.vue")),
    "CourseController": Meta(3, "课程与分类", ("t_course", "t_course_category"), "课程管理、课程详情、课程学习", ("CourseList.vue", "CourseForm.vue", "CourseDetail.vue", "CourseLearn.vue")),
    "ChapterController": Meta(4, "课程资源与学习", ("t_chapter", "t_course"), "课程详情、课程学习", ("CourseDetail.vue", "CourseLearn.vue")),
    "LessonController": Meta(4, "课程资源与学习", ("t_lesson", "t_chapter", "t_learning_progress", "video_resource"), "课程详情、课程学习", ("CourseDetail.vue", "CourseLearn.vue")),
    "CourseResourceController": Meta(4, "课程资源与学习", ("t_course", "t_chapter", "t_lesson", "t_learning_progress", "t_learning_record"), "课程详情、课程学习、学习地图", ("CourseDetail.vue", "CourseLearn.vue", "LearningMap.vue")),
    "VideoResourceController": Meta(4, "课程资源与学习", ("video_resource", "t_lesson"), "课程详情、课程学习", ("CourseDetail.vue", "CourseLearn.vue")),
    "LessonCommentController": Meta(4, "课程资源与学习", ("lesson_comment", "t_lesson", "t_user"), "课程学习", ("CourseLearn.vue",)),
    "StudyNoteController": Meta(4, "课程资源与学习", ("study_note", "t_lesson", "t_user"), "课程学习", ("CourseLearn.vue",)),
    "LearningProgressController": Meta(4, "课程资源与学习", ("t_learning_progress", "t_learning_record", "t_teaching_class", "t_lesson"), "课程学习、学习路线、学习地图", ("CourseLearn.vue", "LearningRoutes.vue", "LearningMap.vue")),
    "LearningExperienceController": Meta(4, "课程资源与学习", ("t_course", "t_chapter", "t_lesson", "t_learning_progress", "t_learning_record", "study_note"), "课程学习、学习路线", ("CourseLearn.vue", "LearningRoutes.vue")),
    "TeachingClassController": Meta(5, "教学班管理", ("t_teaching_class", "t_course", "t_user", "t_class_student"), "教学班管理、我的班级", ("TeachingClassList.vue", "MyClassList.vue")),
    "ClassStudentController": Meta(5, "教学班管理", ("t_class_student", "t_teaching_class", "t_user"), "教学班管理、我的班级", ("TeachingClassList.vue", "MyClassList.vue")),
    "AdministrativeClassController": Meta(6, "行政班管理", ("t_administrative_class", "t_administrative_class_student", "t_course", "t_user"), "行政班管理", ("AdministrativeClassManage.vue",)),
    "ApiQuestionController": Meta(7, "题库管理", ("t_question", "t_course", "t_user", "t_agent"), "题库管理", ("QuestionList.vue",)),
    "ImportController": Meta(8, "批量导入导出", ("t_question", "t_exam_paper", "t_exam_paper_question", "t_homework", "t_teaching_class"), "题库管理、试卷管理、作业管理", ("QuestionList.vue", "PaperList.vue", "ExamList.vue")),
    "PaperController": Meta(9, "试卷管理", ("t_exam_paper", "t_exam_paper_question", "t_question", "t_exam_answer"), "试卷管理", ("PaperList.vue",)),
    "ExamController": Meta(10, "考试与批改", ("t_exam_session", "t_exam_paper", "t_teaching_class", "t_exam_record", "t_exam_answer"), "考试管理、我的考试、考试结果", ("ExamList.vue", "MyExamList.vue", "ExamResults.vue")),
    "ExamWorkflowController": Meta(10, "考试与批改", ("t_exam_session", "t_exam_record", "t_exam_answer", "t_exam_paper_question", "t_question"), "考试答题、考试批改、考试结果", ("ExamTaking.vue", "ExamGrading.vue", "ExamResult.vue")),
    "SubmissionController": Meta(10, "考试与批改", ("t_exam_session", "t_exam_record", "t_exam_answer", "t_exam_paper_question", "t_question"), "考试答题、考试批改、考试结果", ("ExamTaking.vue", "ExamGrading.vue", "ExamResult.vue")),
    "HomeworkController": Meta(11, "作业与批改", ("t_homework", "t_homework_submit", "t_teaching_class"), "作业管理、我的作业", ("ExamList.vue", "MyExamList.vue")),
    "HomeworkSubmitController": Meta(11, "作业与批改", ("t_homework_submit", "t_homework", "t_user"), "我的作业、作业批改", ("MyExamList.vue", "ExamGrading.vue")),
    "HomeworkPaperController": Meta(11, "作业与批改", ("t_homework_paper", "t_homework_paper_question", "t_question"), "作业题卷", ("PaperList.vue",)),
    "HomeworkAssessmentController": Meta(11, "作业与批改", ("t_homework_session", "t_homework_record", "t_homework_answer", "t_homework_paper", "t_homework_paper_question", "t_question", "t_homework_assessment_migration"), "作业管理、我的作业、作业批改、作业结果", ("ExamList.vue", "MyExamList.vue", "ExamGrading.vue", "ExamResult.vue")),
    "GradeRecordController": Meta(12, "成绩管理", ("t_grade_record", "t_course", "t_teaching_class", "t_user"), "我的成绩、课程详情", ("MyGrades.vue", "CourseDetail.vue")),
    "GradeImportLogController": Meta(12, "成绩管理", ("t_grade_import_log", "t_grade_record", "t_teaching_class"), "我的成绩、课程详情", ("MyGrades.vue", "CourseDetail.vue")),
    "NoticeController": Meta(13, "通知公告", ("t_notice", "t_teaching_class", "t_user"), "通知管理、控制台", ("NoticeList.vue", "Home.vue")),
    "FileController": Meta(14, "文件管理", (), "课程、题库、知识库文件上传", ("CourseForm.vue", "CourseDetail.vue", "QuestionList.vue", "KnowledgeBase.vue")),
    "PaperSearchController": Meta(15, "论文检索与收藏", ("t_paper", "t_paper_author", "paper_user_mark"), "论文查询", ("PaperSearch.vue",)),
    "RecommendationController": Meta(16, "智能推荐", ("t_course", "t_lesson", "t_learning_progress", "t_exam_record", "t_exam_answer", "t_question", "t_paper"), "智能推荐", ("IntelligentRecommendation.vue",)),
    "WeKnoraController": Meta(17, "知识库与图谱", (), "智能学伴、知识库管理、知识图谱", ("KnowledgeBase.vue", "TeacherKnowledgeBase.vue", "KnowledgeGraph.vue", "AiChatWidget.vue")),
    "ChatController": Meta(18, "Agent助手", ("t_agent", "t_agent_session", "t_agent_message"), "全局Agent助手", ("AiChat.vue", "AgentFloatingAssistant.vue")),
    "AgentDefinitionController": Meta(18, "Agent助手", ("t_agent",), "全局Agent助手、知识库管理", ("AiChat.vue", "TeacherKnowledgeBase.vue")),
    "AgentActionController": Meta(18, "Agent助手", ("t_agent_pending_action", "t_course", "t_homework", "t_exam_session", "t_grade_record", "t_notice"), "全局Agent助手", ("AiChat.vue", "AgentFloatingAssistant.vue")),
    "AgentModelController": Meta(18, "Agent助手", (), "全局Agent助手", ("AiChat.vue", "AgentFloatingAssistant.vue")),
    "PreparationController": Meta(19, "AI备课", ("t_ai_preparation", "t_user", "t_course", "t_chapter", "t_lesson", "t_question"), "智能资源生成", ("IntelligentResourceGen.vue",)),
    "WorkStudyController": Meta(20, "勤工俭学", ("t_work_study", "t_work_study_student", "t_user"), "勤工俭学岗位、劳务费名单、我的岗位", ("WorkStudyManage.vue", "WorkStudyStudentManage.vue", "WorkStudyList.vue", "MyWorkStudy.vue")),
    "OperationLogController": Meta(21, "系统审计", ("t_operation_log", "t_user"), "操作日志", ("OperationLogList.vue",)),
}


CONTROLLER_ORDER = [
    "AuthController", "UserController", "RoleController", "TeacherApplicationController",
    "DashboardController", "CategoryController", "CourseController", "ChapterController",
    "LessonController", "CourseResourceController", "VideoResourceController",
    "LessonCommentController", "StudyNoteController", "LearningProgressController",
    "LearningExperienceController", "TeachingClassController", "ClassStudentController",
    "AdministrativeClassController", "ApiQuestionController", "ImportController",
    "PaperController", "ExamController", "ExamWorkflowController", "SubmissionController",
    "HomeworkController", "HomeworkSubmitController", "HomeworkPaperController",
    "HomeworkAssessmentController", "GradeRecordController", "GradeImportLogController",
    "NoticeController", "FileController", "PaperSearchController", "RecommendationController",
    "WeKnoraController", "ChatController", "AgentDefinitionController",
    "AgentActionController", "AgentModelController", "PreparationController",
    "WorkStudyController", "OperationLogController",
]


NEW_LABELS: dict[tuple[str, str], str] = {
    ("GET", "/administrative-classes"): "分页查询行政班",
    ("GET", "/administrative-classes/{id}"): "查询行政班详情",
    ("GET", "/administrative-classes/{classId}/students"): "查询行政班学生",
    ("POST", "/administrative-classes"): "新增行政班",
    ("PUT", "/administrative-classes/{id}"): "修改行政班",
    ("DELETE", "/administrative-classes/{id}"): "删除行政班",
    ("POST", "/administrative-classes/{classId}/students"): "添加行政班学生",
    ("PUT", "/administrative-classes/{classId}/students/{id}"): "修改行政班学生",
    ("DELETE", "/administrative-classes/{classId}/students/{id}"): "移除行政班学生",
    ("POST", "/api/questions/generate-from-kb"): "从知识库生成题目",
    ("POST", "/courses/with-outline"): "上传大纲并创建课程",
    ("PUT", "/courses/{id}/enable"): "启用课程",
    ("GET", "/dashboard/summary"): "查询控制台汇总数据",
    ("POST", "/api/exams/{id}/results/submit"): "提交考试结果",
    ("GET", "/api/exams/{id}/results"): "查询考试结果",
    ("GET", "/api/exams/{id}/results/export"): "导出考试结果",
    ("POST", "/api/homeworks"): "创建作业考核",
    ("PUT", "/api/homeworks/{id}"): "修改作业考核",
    ("GET", "/api/homeworks/{id}"): "查询作业考核详情",
    ("GET", "/api/homeworks"): "查询作业考核列表",
    ("POST", "/api/homeworks/{id}/publish"): "发布作业考核",
    ("POST", "/api/homeworks/{id}/close"): "关闭作业考核",
    ("DELETE", "/api/homeworks/{id}"): "删除作业考核",
    ("POST", "/api/homeworks/{id}/submissions/start"): "开始作业答卷",
    ("GET", "/api/homework-submissions"): "查询作业答卷列表",
    ("GET", "/api/homework-submissions/{id}"): "查询作业答卷详情",
    ("GET", "/api/homework-submissions/{id}/grading"): "查询作业批改视图",
    ("GET", "/api/homework-submissions/{id}/student"): "查询学生作业视图",
    ("POST", "/api/homework-submissions/{id}/answers"): "保存作业答案",
    ("POST", "/api/homework-submissions/{id}/submit"): "提交作业答卷",
    ("POST", "/api/homework-submissions/{id}/grade-subjective"): "人工批改作业主观题",
    ("POST", "/api/homework-papers"): "创建作业题卷",
    ("POST", "/api/homework-papers/preflight"): "作业组卷预检",
    ("PUT", "/api/homework-papers/{id}"): "修改作业题卷",
    ("GET", "/api/homework-papers/{id}"): "查询作业题卷详情",
    ("GET", "/api/homework-papers"): "查询作业题卷列表",
    ("POST", "/api/homework-papers/{id}/publish"): "发布作业题卷",
    ("DELETE", "/api/homework-papers/{id}"): "删除作业题卷",
    ("DELETE", "/api/homework-papers/{id}/questions/{paperQuestionId}"): "从作业题卷移除题目",
    ("GET", "/api/imports/questions/template"): "下载题目导入模板",
    ("GET", "/api/imports/papers/template"): "下载试卷导入模板",
    ("GET", "/api/imports/homeworks/template"): "下载作业导入模板",
    ("POST", "/api/imports/questions/preview"): "预览题目导入数据",
    ("POST", "/api/imports/papers/preview"): "预览试卷导入数据",
    ("POST", "/api/imports/homeworks/preview"): "预览作业导入数据",
    ("POST", "/api/imports/questions/confirm"): "确认导入题目",
    ("POST", "/api/imports/papers/confirm"): "确认导入试卷",
    ("POST", "/api/imports/homeworks/confirm"): "确认导入作业",
    ("GET", "/api/imports/{type}/export"): "按类型导出业务数据",
    ("GET", "/lessons/{lessonId}/comments"): "查询课时评论",
    ("POST", "/lessons/{lessonId}/comments"): "发布课时评论",
    ("DELETE", "/lesson-comments/{commentId}"): "删除课时评论",
    ("GET", "/lessons/{lessonId}/playback"): "查询课时播放信息",
    ("POST", "/api/papers/preflight"): "试卷发布前校验",
    ("POST", "/api/papers/{id}/publish"): "发布试卷",
    ("GET", "/api/paper/marks"): "分页查询论文标记",
    ("GET", "/api/paper/marks/batch"): "批量查询论文标记",
    ("GET", "/api/paper/marks/{paperId}"): "查询论文标记详情",
    ("PUT", "/api/paper/marks/{paperId}"): "保存论文收藏与笔记",
    ("DELETE", "/api/paper/marks/{paperId}"): "删除论文标记",
    ("GET", "/api/recommendations/my"): "查询我的智能推荐",
    ("GET", "/lessons/{lessonId}/note"): "查询课时学习笔记",
    ("PUT", "/lessons/{lessonId}/note"): "保存课时学习笔记",
    ("GET", "/lessons/{lessonId}/note/download"): "下载课时学习笔记",
    ("GET", "/api/submissions/{id}/grading"): "查询考试批改详情",
    ("GET", "/users/me"): "查询当前用户资料",
    ("PUT", "/users/me"): "修改当前用户资料",
    ("DELETE", "/users/me"): "注销当前账号",
    ("POST", "/video-resources/url"): "提交视频地址解析",
    ("GET", "/video-resources/{id}"): "查询视频资源状态",
    ("POST", "/video-resources/{id}/retry"): "重试视频资源处理",
    ("GET", "/work-studies"): "分页查询勤工俭学岗位",
    ("GET", "/work-studies/{id}"): "查询勤工俭学岗位详情",
    ("POST", "/work-studies"): "新增勤工俭学岗位",
    ("PUT", "/work-studies/{id}"): "修改勤工俭学岗位",
    ("DELETE", "/work-studies/{id}"): "删除勤工俭学岗位",
    ("POST", "/work-studies/apply"): "申请勤工俭学岗位",
    ("PUT", "/work-studies/audit"): "审核勤工俭学申请",
    ("GET", "/work-studies/my-applications"): "查询我的岗位申请",
    ("GET", "/work-studies/pending-applications"): "查询待审核岗位申请",
    ("GET", "/work-studies/students"): "分页查询劳务费名单",
    ("GET", "/work-studies/students/{id}"): "查询劳务费记录详情",
    ("POST", "/work-studies/students"): "新增劳务费记录",
    ("PUT", "/work-studies/students/{id}"): "修改劳务费记录",
    ("DELETE", "/work-studies/students/{id}"): "删除劳务费记录",
    ("POST", "/work-studies/students/import"): "导入劳务费名单",
    ("GET", "/work-studies/students/export"): "导出劳务费名单",
    ("GET", "/courses/{courseId}/experience"): "查询课程学习体验",
    ("GET", "/learning-content/templates"): "查询互动学习模板",
    ("GET", "/learning-content/templates/{key}"): "查询互动模板详情",
    ("POST", "/code-lab/run"): "运行代码实验",
    ("POST", "/api/preparations"): "创建智能备课项目",
    ("GET", "/api/preparations"): "查询智能备课项目列表",
    ("GET", "/api/preparations/{id}"): "查询智能备课项目详情",
    ("PUT", "/api/preparations/{id}/config"): "修改智能备课配置",
    ("POST", "/api/preparations/{id}/sources"): "上传智能备课资料",
    ("POST", "/api/preparations/{id}/generate-outline"): "生成课程提纲",
    ("PUT", "/api/preparations/{id}/outline"): "保存课程提纲",
    ("POST", "/api/preparations/{id}/generate-lessons"): "生成课时内容",
    ("PUT", "/api/preparations/{id}/lessons"): "保存课时内容",
    ("POST", "/api/preparations/{id}/generate-questions"): "生成课程题目",
    ("PUT", "/api/preparations/{id}/questions"): "保存课程题目",
    ("POST", "/api/preparations/{id}/publish"): "发布智能备课课程",
    ("POST", "/teacher-applications"): "提交教师申请",
    ("GET", "/teacher-applications"): "分页查询教师申请",
    ("POST", "/teacher-applications/{id}/review"): "审核教师申请",
    ("GET", "/api/agent/actions/pending"): "查询待确认Agent动作",
    ("GET", "/api/agent/actions/pending/{actionId}"): "查询待确认动作详情",
    ("GET", "/api/agent/model/health"): "检查Agent模型状态",
    ("DELETE", "/api/weknora/knowledge-bases/{kbId}"): "删除知识库",
    ("GET", "/api/weknora/knowledge-bases/{kbId}/knowledge"): "查询知识库文件",
    ("DELETE", "/api/weknora/knowledge/{knowledgeId}"): "删除知识文件",
    ("PUT", "/api/weknora/knowledge-bases/{kbId}"): "修改知识库",
    ("GET", "/api/weknora/knowledge-bases/{kbId}/graph"): "查询知识图谱",
    ("GET", "/api/weknora/knowledge-bases/{kbId}/wiki-page"): "查询知识库Wiki页面",
    ("GET", "/api/weknora/knowledge-bases/{kbId}/knowledge/{knowledgeId}/content"): "查询知识文件内容",
    ("GET", "/api/weknora/knowledge/{knowledgeId}/file"): "下载知识文件",
    ("GET", "/api/weknora/local-file/**"): "代理访问知识库本地文件",
}


MAPPING_RE = re.compile(
    r"@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)"
    r"\s*(?:\((.*?)\))?",
    re.S,
)


def mapping_paths(args: str | None) -> list[str]:
    if not args:
        return [""]
    text = args.strip()
    match = re.search(r'(?:value|path)\s*=\s*(\{[^}]*\}|"[^"]*")', text, re.S)
    target = match.group(1) if match else text.split(",", 1)[0]
    values = re.findall(r'"([^"]*)"', target)
    return values or [""]


def mapping_http(kind: str, args: str | None) -> str:
    methods = {
        "GetMapping": "GET",
        "PostMapping": "POST",
        "PutMapping": "PUT",
        "DeleteMapping": "DELETE",
        "PatchMapping": "PATCH",
    }
    if kind in methods:
        return methods[kind]
    values = re.findall(r"RequestMethod\.([A-Z]+)", args or "")
    return "/".join(values) if values else "ANY"


def join_path(base: str, child: str) -> str:
    value = ("/" + base.strip("/") + "/" + child.strip("/")).replace("//", "/")
    return value[:-1] if value != "/" and value.endswith("/") else value


def extract_endpoints() -> list[Endpoint]:
    endpoints: list[Endpoint] = []
    for path in sorted(JAVA_ROOT.rglob("*Controller.java")):
        source = path.read_text(encoding="utf-8")
        class_match = re.search(r"public\s+class\s+(\w+Controller)\b", source)
        if not class_match or "@RestController" not in source[: class_match.start()]:
            continue
        controller = class_match.group(1)
        base = ""
        for match in MAPPING_RE.finditer(source[: class_match.start()]):
            if match.group(1) == "RequestMapping":
                base = mapping_paths(match.group(2))[0]
        class_body = source[class_match.end() :]
        for match in MAPPING_RE.finditer(class_body):
            tail = class_body[match.end() :]
            method_match = re.search(
                r"(?:public|protected)\s+[\w<>?,.\[\]\s]+?\s+(\w+)\s*\(",
                tail,
            )
            if not method_match:
                continue
            between = tail[: method_match.start()]
            if any(
                token in between
                for token in (
                    "@GetMapping",
                    "@PostMapping",
                    "@PutMapping",
                    "@DeleteMapping",
                    "@PatchMapping",
                    "@RequestMapping",
                )
            ):
                continue
            for child in mapping_paths(match.group(2)):
                endpoints.append(
                    Endpoint(
                        controller=controller,
                        http_method=mapping_http(match.group(1), match.group(2)),
                        path=join_path(base, child),
                        method_name=method_match.group(1),
                        source_order=match.start(),
                    )
                )
    return endpoints


def old_rows(document: Document) -> dict[tuple[str, str], list[str]]:
    result: dict[tuple[str, str], list[str]] = {}
    for row in document.tables[11].rows[1:]:
        values = [" ".join(cell.text.split()) for cell in row.cells]
        result[(values[5], values[4])] = values
    return result


def special_meta(endpoint: Endpoint, default: Meta) -> Meta:
    if endpoint.controller != "ImportController":
        return default
    if "/questions/" in endpoint.path:
        return Meta(8, "批量导入导出", ("t_question",), "题库管理", ("QuestionList.vue",))
    if "/papers/" in endpoint.path:
        return Meta(8, "批量导入导出", ("t_exam_paper", "t_exam_paper_question", "t_question"), "试卷管理", ("PaperList.vue",))
    if "/homeworks/" in endpoint.path:
        return Meta(8, "批量导入导出", ("t_homework", "t_teaching_class"), "作业管理", ("ExamList.vue",))
    return default


def table_names(meta: Meta, controller: str) -> str:
    if meta.tables:
        return "\n".join(meta.tables)
    if controller == "FileController":
        return "本地文件系统\n（uploads）"
    if controller == "WeKnoraController":
        return "WeKnora\n外部知识库"
    return "无独立业务表"


def replace_paragraph(paragraph, text: str, *, code: bool = False) -> None:
    source_rpr = None
    if paragraph.runs and paragraph.runs[0]._r.rPr is not None:
        source_rpr = copy.deepcopy(paragraph.runs[0]._r.rPr)
    for run in list(paragraph._p.findall(qn("w:r"))):
        paragraph._p.remove(run)
    lines = text.split("\n")
    for index, line in enumerate(lines):
        run = paragraph.add_run()
        if source_rpr is not None:
            run._r.insert(0, copy.deepcopy(source_rpr))
        if code:
            run.font.name = "Consolas"
            r_pr = run._element.get_or_add_rPr()
            r_fonts = r_pr.find(qn("w:rFonts"))
            if r_fonts is None:
                r_fonts = OxmlElement("w:rFonts")
                r_pr.insert(0, r_fonts)
            r_fonts.set(qn("w:eastAsia"), "等线")
            run.font.size = Pt(8.5)
        if index:
            run.add_break()
        run.add_text(line)


def set_repeat_header(row) -> None:
    tr_pr = row._tr.get_or_add_trPr()
    node = tr_pr.find(qn("w:tblHeader"))
    if node is None:
        node = OxmlElement("w:tblHeader")
        tr_pr.append(node)
    node.set(qn("w:val"), "true")


def set_cant_split(row) -> None:
    tr_pr = row._tr.get_or_add_trPr()
    if tr_pr.find(qn("w:cantSplit")) is None:
        tr_pr.append(OxmlElement("w:cantSplit"))


def set_table_geometry(table, widths: list[int]) -> None:
    table.autofit = False
    tbl_pr = table._tbl.tblPr
    tbl_w = tbl_pr.find(qn("w:tblW"))
    if tbl_w is None:
        tbl_w = OxmlElement("w:tblW")
        tbl_pr.append(tbl_w)
    tbl_w.set(qn("w:type"), "dxa")
    tbl_w.set(qn("w:w"), str(sum(widths)))
    layout = tbl_pr.find(qn("w:tblLayout"))
    if layout is None:
        layout = OxmlElement("w:tblLayout")
        tbl_pr.append(layout)
    layout.set(qn("w:type"), "fixed")
    for grid_col, width in zip(table._tbl.tblGrid.gridCol_lst, widths):
        grid_col.set(qn("w:w"), str(width))
    for row in table.rows:
        for cell, width in zip(row.cells, widths):
            tc_w = cell._tc.get_or_add_tcPr().get_or_add_tcW()
            tc_w.set(qn("w:type"), "dxa")
            tc_w.set(qn("w:w"), str(width))


def style_table_row(row, header: bool = False) -> None:
    set_cant_split(row)
    for index, cell in enumerate(row.cells):
        cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
        for paragraph in cell.paragraphs:
            paragraph.paragraph_format.space_before = Pt(0)
            paragraph.paragraph_format.space_after = Pt(0)
            paragraph.paragraph_format.line_spacing = 1
            paragraph.alignment = (
                WD_ALIGN_PARAGRAPH.CENTER if index in (0, 5) else WD_ALIGN_PARAGRAPH.LEFT
            )
            for run in paragraph.runs:
                run.font.size = Pt(9)
                run.bold = header
                r_pr = run._element.get_or_add_rPr()
                r_fonts = r_pr.find(qn("w:rFonts"))
                if r_fonts is None:
                    r_fonts = OxmlElement("w:rFonts")
                    r_pr.insert(0, r_fonts)
                r_fonts.set(qn("w:eastAsia"), "宋体")


def build_rows(document: Document, endpoints: list[Endpoint]) -> tuple[list[list[str]], set[str]]:
    old = old_rows(document)
    order = {name: index for index, name in enumerate(CONTROLLER_ORDER)}
    endpoints = sorted(
        endpoints,
        key=lambda item: (
            special_meta(item, META[item.controller]).module_no,
            order[item.controller],
            item.source_order,
            item.http_method,
            item.path,
        ),
    )
    rows: list[list[str]] = []
    used_tables: set[str] = set()
    missing_labels: list[Endpoint] = []
    for endpoint in endpoints:
        meta = special_meta(endpoint, META[endpoint.controller])
        used_tables.update(meta.tables)
        key = (endpoint.http_method, endpoint.path)
        label = old.get(key, ["", "", "", ""])[3] or NEW_LABELS.get(key)
        if not label:
            missing_labels.append(endpoint)
            continue
        rows.append(
            [
                str(meta.module_no),
                meta.module_name,
                table_names(meta, endpoint.controller),
                label,
                endpoint.path,
                endpoint.http_method,
                meta.pages,
                "\n".join(meta.components),
                endpoint.controller,
            ]
        )
    if missing_labels:
        formatted = "\n".join(
            f"{item.controller} {item.http_method} {item.path} {item.method_name}"
            for item in missing_labels
        )
        raise RuntimeError(f"Missing Chinese labels for endpoints:\n{formatted}")
    return rows, used_tables


def update_document(document: Document, rows: list[list[str]]) -> None:
    table = document.tables[11]
    for row in list(table.rows[1:]):
        table._tbl.remove(row._tr)
    headers = ["序号", "模块名称", "数据表", "功能点", "API接口", "请求方式", "对应页面", "前端组件", "后端控制器"]
    for cell, value in zip(table.rows[0].cells, headers):
        cell.text = value
    set_repeat_header(table.rows[0])
    style_table_row(table.rows[0], header=True)
    for values in rows:
        row = table.add_row()
        for cell, value in zip(row.cells, values):
            cell.text = value
        style_table_row(row)
    set_table_geometry(table, [394, 652, 1918, 1048, 3486, 889, 652, 2507, 2412])

    replace_paragraph(
        document.paragraphs[368],
        "说明：接口以当前42个带@RestController的控制器为准，已排除6个仅保留源码但未注册的@Deprecated兼容控制器；数据表以运行库和database/ai_academy_schema.sql为准；页面以Vue Router当前45个命名路由为准。",
    )
    replace_paragraph(
        document.paragraphs[370],
        "（1）系统功能：按当前源码统计，功能模块21个，实际注册HTTP接口279个，数据库表45张，前端命名路由45条（42条MainLayout子路由，另含登录、注册和404）。",
    )
    replace_paragraph(
        document.paragraphs[375],
        "统计说明：t_learning_record和t_homework_assessment_migration已建表，但当前没有独立实体、Mapper、接口或页面；作业路由复用考试评测页面，旧MyHomeworkList.vue、HomeworkList.vue和HomeworkSubmissions.vue不列入当前页面。",
    )

    replace_paragraph(
        document.paragraphs[385],
        "来源：controller/PaperSearchController.java。接口按source选择本地、arXiv、OpenAlex或Semantic Scholar，并支持作者、来源、年份、引用量、开放获取和排序条件。",
    )
    replace_paragraph(
        document.paragraphs[386],
        '''@GetMapping("/search")
public Result search(@RequestParam String keyword,
        @RequestParam(defaultValue = "local") String source,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer size,
        @RequestParam(required = false) String author,
        @RequestParam(required = false) String venue,
        @RequestParam(required = false) Integer yearFrom,
        @RequestParam(required = false) Integer yearTo,
        @RequestParam(required = false) Integer minCitations,
        @RequestParam(required = false, defaultValue = "relevance") String sortBy,
        @RequestParam(required = false) Boolean openAccessOnly) {
    return switch (source) {
        case "arxiv" -> arxivSearchService.search(keyword, page, size, sortBy);
        case "openalex" -> openAlexSearchService.search(keyword, page, size, sortBy);
        case "semantic_scholar" -> paperSearchService.searchPapers(
            keyword, page, size, sortBy);
        default -> paperLocalService.search(keyword, page, size, author, venue,
            yearFrom, yearTo, minCitations, sortBy, openAccessOnly);
    };
}''',
        code=True,
    )
    replace_paragraph(
        document.paragraphs[388],
        "来源：agent/action/AgentActionController.java。模型生成的写操作仅允许教师或管理员确认；控制器先校验角色，再把确认请求和客户端IP交给执行服务。",
    )
    replace_paragraph(
        document.paragraphs[389],
        '''@PostMapping("/api/agent/actions/confirm")
public Result confirm(@Valid @RequestBody AgentActionConfirmRequest request,
                      HttpServletRequest servletRequest) {
    Result forbidden = requireOperationRole();
    if (forbidden != null) {
        return forbidden;
    }
    return Result.success(actionExecutionService.confirm(
        request, clientIp(servletRequest)));
}

private Result requireOperationRole() {
    String roleCode = UserContext.getCurrentRoleCode();
    if (!"teacher".equals(roleCode) && !"admin".equals(roleCode)) {
        return Result.error("403", "学生账号仅开放 Agent 对话权限");
    }
    return null;
}

private String clientIp(HttpServletRequest request) {
    if (request == null) {
        return "";
    }
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
        return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
}''',
        code=True,
    )


def main() -> None:
    document = Document(INPUT)
    endpoints = extract_endpoints()
    if len(endpoints) != 279:
        raise RuntimeError(f"Expected 279 registered endpoints, got {len(endpoints)}")
    if len({item.controller for item in endpoints}) != 42:
        raise RuntimeError("Registered controller count drifted")
    if len({(item.http_method, item.path) for item in endpoints}) != len(endpoints):
        raise RuntimeError("Duplicate HTTP method/path pairs detected")
    unknown_controllers = sorted({item.controller for item in endpoints} - META.keys())
    if unknown_controllers:
        raise RuntimeError(f"Missing controller metadata: {unknown_controllers}")

    rows, used_tables = build_rows(document, endpoints)
    schema_tables = set(re.findall(r"CREATE TABLE `([^`]+)`", SCHEMA.read_text(encoding="utf-8")))
    if len(schema_tables) != 45:
        raise RuntimeError(f"Expected 45 schema tables, got {len(schema_tables)}")
    if used_tables != schema_tables:
        raise RuntimeError(
            f"Database-table coverage mismatch; missing={sorted(schema_tables-used_tables)}, "
            f"extra={sorted(used_tables-schema_tables)}"
        )
    if len(rows) != 279:
        raise RuntimeError(f"Expected 279 appendix rows, got {len(rows)}")
    if len({int(row[0]) for row in rows}) != 21:
        raise RuntimeError("Module count drifted")
    if any(name in "\n".join(row[7] for row in rows) for name in ("MyHomeworkList.vue", "HomeworkList.vue", "HomeworkSubmissions.vue")):
        raise RuntimeError("Unrouted legacy homework page leaked into appendix")

    update_document(document, rows)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    document.save(OUTPUT)

    counts = Counter(item.controller for item in endpoints)
    print({
        "output": str(OUTPUT),
        "registered_controllers": len(counts),
        "registered_endpoints": len(rows),
        "modules": len({row[0] for row in rows}),
        "schema_tables": len(schema_tables),
        "table_rows_with_header": len(document.tables[11].rows),
        "paragraphs": len(document.paragraphs),
        "tables": len(document.tables),
        "inline_shapes": len(document.inline_shapes),
    })


if __name__ == "__main__":
    main()
