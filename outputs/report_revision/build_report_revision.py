from __future__ import annotations

from copy import deepcopy
from pathlib import Path
from zipfile import ZipFile
import hashlib
import re
import sys

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.table import Table
from docx.text.paragraph import Paragraph
from docx.shared import Cm, Pt


ROOT = Path(__file__).resolve().parents[2]
DOC_DIR = Path(r"D:\PPT\web应用开发综合实践")
SOURCE = DOC_DIR / "最终版_第567章更新.docx"
TARGET = Path(sys.argv[1]) if len(sys.argv) > 1 else DOC_DIR / "最终版_正文一致性修订.docx"
CHAPTER34_REFERENCE = ROOT / "outputs" / "chapter3_refresh" / "final_chapter3_updated_v2.docx"
APPENDIX_REFERENCE = ROOT / "outputs" / "appendix_refresh" / "final_appendix_updated.docx"
SCHEMA = ROOT / "database" / "ai_academy_schema.sql"


def element_text(element) -> str:
    return "".join(element.xpath('.//*[local-name()="t"]/text()')).strip()


def is_drawing_paragraph(element) -> bool:
    return element.tag.endswith("}p") and bool(
        element.xpath('.//*[local-name()="drawing" or local-name()="pict"]')
    )


def find_body_paragraph(document: Document, predicate):
    for element in document._body._element:
        if element.tag.endswith("}p") and predicate(element_text(element)):
            return element
    raise ValueError("Body paragraph not found")


def find_paragraph(document: Document, predicate) -> Paragraph:
    for paragraph in document.paragraphs:
        if predicate(paragraph.text.strip()):
            return paragraph
    raise ValueError("Paragraph not found")


def set_text_keep_format(paragraph: Paragraph, text: str):
    if paragraph.runs:
        paragraph.runs[0].text = text
        for run in paragraph.runs[1:]:
            run.text = ""
    else:
        paragraph.add_run(text)


def set_element_text(element, text: str):
    paragraph = Paragraph(element, element.getparent())
    set_text_keep_format(paragraph, text)


def clone_paragraph(template: Paragraph, text: str, keep_with_next: bool = False):
    element = deepcopy(template._p)
    set_element_text(element, text)
    paragraph = Paragraph(element, template._parent)
    if keep_with_next:
        paragraph.paragraph_format.keep_with_next = True
    return element


def replace_body_block(document: Document, start_element, end_element, replacement_elements):
    body = document._body._element
    elements = list(body)
    start_index = elements.index(start_element)
    end_index = elements.index(end_element)
    for element in elements[start_index:end_index]:
        body.remove(element)
    for element in replacement_elements:
        end_element.addprevious(element)


def transplant_chapters_3_and_4(document: Document, reference: Document):
    source_start = find_body_paragraph(document, lambda t: t.startswith("3  "))
    source_end = find_body_paragraph(document, lambda t: t.startswith("5  "))
    ref_start = find_body_paragraph(reference, lambda t: t.startswith("3  "))
    ref_end = find_body_paragraph(reference, lambda t: t.startswith("5  "))

    source_elements = list(document._body._element)
    source_block = source_elements[
        source_elements.index(source_start):source_elements.index(source_end)
    ]
    source_drawings = [deepcopy(e) for e in source_block if is_drawing_paragraph(e)]

    ref_elements = list(reference._body._element)
    ref_block = ref_elements[ref_elements.index(ref_start):ref_elements.index(ref_end)]
    ref_drawing_count = sum(is_drawing_paragraph(e) for e in ref_block)
    if ref_drawing_count != len(source_drawings):
        raise AssertionError(
            f"Drawing count mismatch: source={len(source_drawings)}, reference={ref_drawing_count}"
        )

    replacements = []
    drawing_index = 0
    for element in ref_block:
        if is_drawing_paragraph(element):
            replacements.append(source_drawings[drawing_index])
            drawing_index += 1
        else:
            replacements.append(deepcopy(element))
    replace_body_block(document, source_start, source_end, replacements)


def replace_chapter_texts(document: Document, chapter_start: str, chapter_end: str, texts: list[str]):
    paragraphs = document.paragraphs
    start = next(i for i, p in enumerate(paragraphs) if p.text.strip().startswith(chapter_start))
    end = next(i for i, p in enumerate(paragraphs) if p.text.strip().startswith(chapter_end))
    block = paragraphs[start:end]
    if len(block) != len(texts):
        raise AssertionError(
            f"Paragraph count mismatch for {chapter_start}: current={len(block)}, desired={len(texts)}"
        )
    for paragraph, text in zip(block, texts):
        set_text_keep_format(paragraph, text)


def configure_run_font(run, size: float | None = None, bold: bool | None = None):
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold
    run.font.name = "Times New Roman"
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.rFonts
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.insert(0, rfonts)
    rfonts.set(qn("w:eastAsia"), "宋体")
    rfonts.set(qn("w:ascii"), "Times New Roman")
    rfonts.set(qn("w:hAnsi"), "Times New Roman")


def shade_cell(cell, fill="D9EAF7"):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_width(cell, width_cm: float):
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_w = tc_pr.find(qn("w:tcW"))
    if tc_w is None:
        tc_w = OxmlElement("w:tcW")
        tc_pr.append(tc_w)
    tc_w.set(qn("w:w"), str(Cm(width_cm).twips))
    tc_w.set(qn("w:type"), "dxa")


def prevent_row_split(row, repeat_header=False):
    tr_pr = row._tr.get_or_add_trPr()
    if tr_pr.find(qn("w:cantSplit")) is None:
        tr_pr.append(OxmlElement("w:cantSplit"))
    if repeat_header and tr_pr.find(qn("w:tblHeader")) is None:
        tr_pr.append(OxmlElement("w:tblHeader"))


def create_table(document: Document, rows: list[list[str]], widths: list[float] | None = None) -> Table:
    table = document.add_table(rows=1, cols=len(rows[0]))
    table.style = "Table Grid"
    table.autofit = False
    for row_index, values in enumerate(rows):
        cells = table.rows[0].cells if row_index == 0 else table.add_row().cells
        for column_index, value in enumerate(values):
            cells[column_index].text = str(value)
            cells[column_index].vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            if widths:
                set_cell_width(cells[column_index], widths[column_index])
            for paragraph in cells[column_index].paragraphs:
                paragraph.paragraph_format.space_after = Pt(0)
                paragraph.paragraph_format.line_spacing = 1
                for run in paragraph.runs:
                    configure_run_font(run, 8.5, bold=(row_index == 0))
            if row_index == 0:
                shade_cell(cells[column_index])
        prevent_row_split(table.rows[row_index], repeat_header=(row_index == 0))
    return table


def insert_elements_before(anchor_element, elements):
    for element in elements:
        anchor_element.addprevious(element)


def parse_schema(schema_path: Path):
    sql = schema_path.read_text(encoding="utf-8")
    tables = {}
    pattern = re.compile(
        r"CREATE TABLE\s+`(?P<name>[^`]+)`\s*\((?P<body>.*?)\)\s*ENGINE=",
        re.S | re.I,
    )
    for match in pattern.finditer(sql):
        name = match.group("name")
        body = match.group("body")
        primary = set()
        unique_constraints = []
        foreign = {}
        for line in body.splitlines():
            primary_match = re.search(r"PRIMARY KEY\s*\(([^)]+)\)", line, re.I)
            if primary_match:
                primary.update(re.findall(r"`([^`]+)`", primary_match.group(1)))
            unique_match = re.search(r"UNIQUE (?:KEY|INDEX)\s+`([^`]+)`\s*\(([^)]+)\)", line, re.I)
            if unique_match:
                unique_constraints.append(
                    (unique_match.group(1), re.findall(r"`([^`]+)`", unique_match.group(2)))
                )
            foreign_match = re.search(
                r"FOREIGN KEY\s*\(`([^`]+)`\)\s*REFERENCES\s*`([^`]+)`\s*\(`([^`]+)`\)",
                line,
                re.I,
            )
            if foreign_match:
                foreign[foreign_match.group(1)] = (
                    foreign_match.group(2),
                    foreign_match.group(3),
                )

        fields = []
        for raw_line in body.splitlines():
            line = raw_line.strip().rstrip(",")
            field_match = re.match(r"`([^`]+)`\s+(.+)$", line)
            if not field_match:
                continue
            field_name = field_match.group(1)
            definition = field_match.group(2)
            comment_match = re.search(r"\bCOMMENT\s+'((?:''|[^'])*)'", definition, re.I)
            comment = comment_match.group(1).replace("''", "'") if comment_match else ""
            type_part = re.split(
                r"\s+(?:NOT NULL|NULL|DEFAULT|AUTO_INCREMENT|COMMENT|COLLATE|CHARACTER SET|ON UPDATE)\b",
                definition,
                maxsplit=1,
                flags=re.I,
            )[0]
            nullable = "否" if re.search(r"\bNOT NULL\b", definition, re.I) else "是"
            keys = []
            if field_name in primary:
                keys.append("主键")
            for constraint_name, columns in unique_constraints:
                if field_name in columns:
                    keys.append("唯一" if len(columns) == 1 else f"联合唯一({constraint_name})")
            if field_name in foreign:
                ref_table, ref_column = foreign[field_name]
                keys.append(f"外键→{ref_table}.{ref_column}")
            if re.search(r"\bAUTO_INCREMENT\b", definition, re.I):
                keys.append("自增")
            fields.append(
                {
                    "name": field_name,
                    "type": type_part,
                    "nullable": nullable,
                    "key": "；".join(keys),
                    "comment": comment,
                }
            )
        tables[name] = fields
    return sql, tables


def build_chapter_6(document: Document, schema_sql: str, schema_tables: dict[str, list[dict]]):
    start = find_body_paragraph(document, lambda t: t.startswith("6  "))
    end = find_body_paragraph(document, lambda t: t.startswith("7  "))
    chapter_template = find_paragraph(document, lambda t: t.startswith("6  "))
    level2_template = find_paragraph(document, lambda t: t.startswith("6.1"))
    body_template = find_paragraph(document, lambda t: t.startswith("数据库按照业务边界"))
    caption_template = find_paragraph(document, lambda t: t.startswith("图6-1"))
    table_caption_template = find_paragraph(document, lambda t: t.startswith("表6-1"))

    groups = {
        "用户权限": ["t_user", "t_role", "t_user_role", "t_teacher_application"],
        "课程教学与学习": [
            "t_course_category", "t_course", "t_chapter", "t_lesson", "video_resource",
            "t_administrative_class", "t_administrative_class_student", "t_teaching_class",
            "t_class_student", "t_learning_progress", "t_learning_record", "study_note",
            "lesson_comment",
        ],
        "作业考试与成绩": [
            "t_question", "t_exam_paper", "t_exam_paper_question", "t_exam_session",
            "t_exam_record", "t_exam_answer", "t_homework", "t_homework_submit",
            "t_homework_paper", "t_homework_paper_question", "t_homework_session",
            "t_homework_record", "t_homework_answer", "t_homework_assessment_migration",
            "t_grade_record", "t_grade_import_log",
        ],
        "AI与学术服务": [
            "t_agent", "t_agent_session", "t_agent_message", "t_agent_pending_action",
            "t_ai_preparation", "t_paper", "t_paper_author", "paper_user_mark",
        ],
        "系统运营": ["t_notice", "t_operation_log", "t_work_study", "t_work_study_student"],
    }
    grouped = [name for names in groups.values() for name in names]
    if len(schema_tables) != 45 or set(grouped) != set(schema_tables):
        missing = sorted(set(schema_tables) - set(grouped))
        extra = sorted(set(grouped) - set(schema_tables))
        raise AssertionError(
            f"Database grouping mismatch: tables={len(schema_tables)}, missing={missing}, extra={extra}"
        )

    foreign_key_count = len(re.findall(r"\bFOREIGN KEY\b", schema_sql, re.I))
    unique_count = len(re.findall(r"\bUNIQUE (?:KEY|INDEX)\b", schema_sql, re.I))

    elements = [
        clone_paragraph(chapter_template, "6  数据库设计", True),
        clone_paragraph(level2_template, "6.1  概念结构设计", True),
        clone_paragraph(
            body_template,
            "当前建库脚本共定义45张物理表，按照业务边界划分为用户权限、课程教学与学习、作业考试与成绩、AI与学术服务、系统运营五个实体域。用户编号贯穿身份、教学、评测和运营数据，课程编号与教学班编号连接教学内容及学习活动；外部WeKnora知识库正文不落入本地业务表，仅通过接口维护知识库编号、会话和文档状态。",
        ),
        clone_paragraph(
            body_template,
            "用户权限域以t_user、t_role和t_user_role构成RBAC关系，并由t_teacher_application保存教师申请；课程教学域按照分类—课程—章节—课时组织资源，同时区分行政班与教学班，学生成员关系分别由中间表维护。学习进度、学习记录、笔记和评论与学生及课时关联，视频资源单独记录处理状态和播放地址。",
        ),
        clone_paragraph(
            body_template,
            "评测域同时保留考试和作业数据链路：题目与题卷通过中间表形成多对多关系，评测场次关联教学班和题卷，学生答卷下保存答案明细；客观题自动判分、主观题人工批改后形成成绩记录。Agent定义、会话、消息和待确认动作形成AI交互链路，论文与作者、用户收藏形成学术服务链路；通知、操作日志和勤工俭学数据承担系统运营职责。",
        ),
        clone_paragraph(caption_template, "图6-1 AI智学平台分域ER图", True),
        clone_paragraph(level2_template, "6.2  逻辑结构设计", True),
        clone_paragraph(
            body_template,
            f"逻辑结构采用MySQL 8.0和utf8mb4字符集，当前脚本包含45张物理表、{foreign_key_count}处外键约束和{unique_count}处唯一约束。表名以t_前缀为主，历史辅助表保留既有命名；主键通常为自增id，核心业务表记录创建或更新时间。外键、状态、时间和排序字段建立索引，用户角色、班级成员、题卷题目等关系使用联合唯一约束防止重复。",
        ),
        clone_paragraph(
            body_template,
            "考试提交、批改汇总、成绩生成、批量导入和Agent确认执行等多表写入由服务层事务保证一致性。删除课程、题卷、班级等被引用数据前先校验业务状态或使用明确的级联策略；文件和视频正文保存于本地资源目录，数据库仅保存访问路径、大小、处理状态和关联业务编号。t_learning_record与t_homework_assessment_migration已经建表，但当前主要用于记录或迁移，不单独暴露页面和业务接口。",
        ),
        clone_paragraph(table_caption_template, "表6-1 当前版本45张物理表分域统计", True),
    ]

    summary_rows = [["业务域", "数量", "物理表"]]
    for group_name, names in groups.items():
        summary_rows.append([group_name, str(len(names)), "、".join(names)])
    summary_rows.append(["合计", "45", "以database/ai_academy_schema.sql中的CREATE TABLE语句为准"])
    summary = create_table(document, summary_rows, [2.5, 1.2, 12.0])
    elements.append(summary._tbl)

    detailed_tables = [
        ("t_user", "用户表"),
        ("t_user_role", "用户角色关系表"),
        ("t_course", "课程表"),
        ("t_lesson", "课时表"),
        ("t_teaching_class", "教学班表"),
        ("t_class_student", "教学班学生关系表"),
        ("t_learning_progress", "学习进度表"),
        ("t_homework", "作业表"),
        ("t_exam_paper", "考试试卷表"),
        ("t_exam_session", "考试场次表"),
        ("t_exam_record", "考试记录表"),
        ("t_grade_record", "成绩记录表"),
        ("t_agent_session", "Agent会话表"),
        ("t_agent_pending_action", "Agent待确认动作表"),
        ("t_paper", "论文表"),
        ("t_operation_log", "操作日志表"),
        ("t_work_study_student", "勤工俭学学生记录表"),
    ]
    for number, (table_name, chinese_name) in enumerate(detailed_tables, start=2):
        elements.append(
            clone_paragraph(
                table_caption_template,
                f"表6-{number} {chinese_name}（{table_name}）",
                True,
            )
        )
        rows = [["字段名", "数据类型", "可空", "键/参照", "说明"]]
        for field in schema_tables[table_name]:
            rows.append(
                [
                    field["name"],
                    field["type"],
                    field["nullable"],
                    field["key"],
                    field["comment"],
                ]
            )
        table = create_table(document, rows, [2.6, 2.6, 1.2, 4.4, 5.0])
        elements.append(table._tbl)

    replace_body_block(document, start, end, elements)


def build_chapter_7(document: Document):
    start = find_body_paragraph(document, lambda t: t.startswith("7  "))
    end = find_body_paragraph(document, lambda t: t.startswith("8  "))
    chapter_template = find_paragraph(document, lambda t: t.startswith("7  "))
    level2_template = find_paragraph(document, lambda t: t.startswith("7.1"))
    level3_template = find_paragraph(document, lambda t: t.startswith("7.2.1"))
    body_template = find_paragraph(document, lambda t: t.startswith("项目已经形成"))
    caption_template = find_paragraph(document, lambda t: t.startswith("图7-1"))
    table_caption_template = find_paragraph(document, lambda t: t.startswith("表6-1"))

    elements = [
        clone_paragraph(chapter_template, "7  完成情况", True),
        clone_paragraph(level2_template, "7.1  系统描述", True),
        clone_paragraph(
            body_template,
            "项目已经形成可运行的前后端分离系统。按当前源码统计，系统划分为21个功能模块，后端包含48个Controller源码文件，其中42个带@RestController并实际注册279个方法级HTTP接口；前端src/views目录包含45个Vue视图文件，Vue Router定义46条命名路由。279表示已注册接口数量，不等同于279项彼此独立的业务功能。",
        ),
        clone_paragraph(
            body_template,
            "后端以Java 21和Spring Boot 3.5.16为基础，按controller、service、mapper、pojo、dto、config、interceptor和agent等目录组织；前端基于Vue 3.5.38和Vite 8.1.0，按views、components、api、router、store、composables、utils及assets组织。数据库脚本集中在database目录，当前定义45张物理表。",
        ),
        clone_paragraph(
            body_template,
            "当前主后端测试源码包含2个Java测试文件和18处@Test，主要覆盖考试状态流转、文件导入及部分服务逻辑。backend-api.md记录的最近一次Apifox回归包含4个场景、176个自定义HTTP步骤，覆盖正常、空值、非法输入和完整性约束；原始HTML、JSON和JUnit报告仍需在提交前重新导出并随项目保存。并发用户数、P95响应时间和长期可用性尚未形成正式压力测试报告。",
        ),
        clone_paragraph(
            body_template,
            "本地核心业务由Spring Boot、Vue和MySQL独立完成；DeepSeek、WeKnora、arXiv、OpenAlex、Semantic Scholar及yt-dlp视频服务属于外部或辅助依赖。第三方不可用时，课程、班级、学习记录、作业、考试、成绩、通知和勤工俭学仍可运行，受影响的智能问答、外部论文检索、知识库及视频下载应显示超时、失败和重试状态。",
        ),
        clone_paragraph(caption_template, "图7-1 后端源码目录结构", True),
        clone_paragraph(caption_template, "图7-2 前端源码目录结构", True),
        clone_paragraph(caption_template, "图7-3 MySQL物理表与约束截图", True),
        clone_paragraph(
            body_template,
            "验收证据应形成同一次操作的闭环：页面显示操作对象和状态，浏览器网络面板显示请求方法、接口和响应，白底后端日志显示对应SQL及参数，数据库结果能够再次查询。涉及外部服务的功能还应保留成功与不可用两种结果，证明降级提示不会阻塞本地教学主流程。",
        ),
        clone_paragraph(table_caption_template, "表7-1 当前版本21个功能模块完成状态", True),
    ]

    status_rows = [
        ["序号", "功能模块", "当前状态", "主要页面或依赖说明"],
        ["1", "用户与权限", "本地完整运行", "Login、Register、UserManage、TeacherApplications、ProfileSettings"],
        ["2", "首页统计", "本地完整运行", "Home按管理员、教师角色汇总业务数据"],
        ["3", "课程与分类", "本地完整运行", "CategoryManage、CourseList、CourseForm、CourseDetail"],
        ["4", "课程资源与学习", "核心本地运行", "CourseLearn、学习笔记与评论；视频URL下载依赖yt-dlp服务"],
        ["5", "教学班管理", "本地完整运行", "TeachingClassList、MyClassList"],
        ["6", "行政班管理", "本地完整运行", "AdministrativeClassManage"],
        ["7", "题库管理", "本地完整运行", "QuestionList，支持多题型与导入"],
        ["8", "批量导入导出", "本地完整运行", "题目、成绩和劳务费文件处理"],
        ["9", "试卷管理", "本地完整运行", "PaperList及题目顺序、分值配置"],
        ["10", "考试与批改", "本地完整运行", "ExamList、ExamTaking、ExamGrading、ExamResults"],
        ["11", "作业与批改", "本地完整运行", "作业路由复用统一评测页面，保留兼容读取"],
        ["12", "成绩管理", "本地完整运行", "MyGrades、GradeAnalysis及成绩导出"],
        ["13", "通知公告", "本地完整运行", "NoticeList按用户、课程或班级筛选"],
        ["14", "文件管理", "本地完整运行", "统一上传与删除接口，本地目录保存文件"],
        ["15", "论文检索与收藏", "本地库完整，外部依赖", "PaperSearch；外部检索依赖网络和第三方API"],
        ["16", "智能推荐", "本地规则运行", "IntelligentRecommendation按课程、进度和成绩评分"],
        ["17", "知识库与图谱", "依赖外部服务", "TeacherKnowledgeBase、KnowledgeBase、KnowledgeGraph依赖WeKnora"],
        ["18", "Agent助手", "本地编排+模型依赖", "AiChat与浮动助手；写操作需教师或管理员确认"],
        ["19", "AI备课", "依赖模型或知识库", "IntelligentResourceGen，生成内容需人工审核后发布"],
        ["20", "勤工俭学", "本地完整运行", "岗位、申请、审核、劳务费导入导出"],
        ["21", "系统审计", "本地完整运行", "OperationLogList查询请求状态、耗时和操作人"],
    ]
    status_table = create_table(document, status_rows, [1.0, 3.0, 3.0, 8.2])
    elements.append(status_table._tbl)

    elements.extend(
        [
            clone_paragraph(level2_template, "7.2  功能展示", True),
            clone_paragraph(
                body_template,
                "以下按照当前路由与页面归并展示主要功能。图片由小组使用真实运行环境补充，同一组截图应展示角色、页面地址、业务对象、请求结果和必要日志，避免只截静态页面而不能证明功能闭环。",
            ),
            clone_paragraph(level3_template, "7.2.1  登录、注册、用户与首页统计", True),
            clone_paragraph(
                body_template,
                "公共入口包括/、/home、/login和/register；登录后进入/console。管理员在/users维护账号和角色，在/teacher-applications审核教师申请；三类用户在/profile维护个人资料。首页根据角色调用不同统计接口，前端路由守卫控制菜单，后端通过JWT、用户上下文和资源归属再次校验。",
            ),
            clone_paragraph(
                body_template,
                "核心接口包括/auth/login、/auth/register、/auth/info、/users、/teacher-applications及首页统计接口，涉及t_user、t_role、t_user_role和t_teacher_application。截图应同时展示登录成功、无权限访问被拒绝和首页统计结果。",
            ),
            clone_paragraph(caption_template, "图7-4 登录、用户管理与首页统计页面", True),
            clone_paragraph(level3_template, "7.2.2  课程、行政班、教学班与学习过程", True),
            clone_paragraph(
                body_template,
                "管理员维护课程分类和行政班，教师创建课程、章节、课时与教学班并组织学生。课时支持Markdown、互动内容、附件、本地视频和公开视频URL；学生从/my-classes、/learning-routes或学习地图进入课程，系统恢复上次位置并上报进度、时长，笔记和评论与课时关联。",
            ),
            clone_paragraph(
                body_template,
                "核心接口覆盖/categories、/courses、/chapters、/lessons、/administrative-classes、/teaching-classes、学习进度、笔记、评论和视频资源。截图应包含课程结构、班级成员、至少一种课时内容及断点续学前后结果。",
            ),
            clone_paragraph(caption_template, "图7-5 课程、班级与课程学习页面", True),
            clone_paragraph(level3_template, "7.2.3  题库、作业、考试与成绩", True),
            clone_paragraph(
                body_template,
                "教师维护题库和题卷，分别创建作业或考试评测活动并发布；学生开始答题后持续保存答案，提交时系统校验时间、状态与班级身份，客观题自动判分，主观题由教师批改。批改完成后生成成绩与统计，学生在/my-grades查看趋势和详情，教师在结果页导出成绩。",
            ),
            clone_paragraph(
                body_template,
                "核心接口包括题目、考试题卷、作业题卷、评测场次、答卷、答案、批改、成绩和导入导出接口。截图应使用同一学生与评测编号串联发布、答题、交卷、批改和成绩结果。",
            ),
            clone_paragraph(caption_template, "图7-6 题库、作业考试、批改与成绩页面", True),
            clone_paragraph(level3_template, "7.2.4  Agent、AI备课与动作确认", True),
            clone_paragraph(
                body_template,
                "登录用户可使用Agent助手保存会话和消息；教师或管理员可以调用平台读工具，并在自然语言写计划生成后查看动作类型、目标和参数。写操作只保存为待确认动作，用户确认时再次校验角色和业务状态。AI备课按大纲、课时和题目阶段生成草稿，人工修改后才发布到课程和题库。",
            ),
            clone_paragraph(
                body_template,
                "核心接口包括/api/agent/definitions、/api/agent/sessions、会话chat接口、/api/agent/actions/confirm和AI备课接口，涉及t_agent、t_agent_session、t_agent_message、t_agent_pending_action和t_ai_preparation。截图应展示普通问答、待确认动作及确认后的业务结果。",
            ),
            clone_paragraph(caption_template, "图7-7 Agent会话、动作确认与AI备课页面", True),
            clone_paragraph(level3_template, "7.2.5  论文、智能推荐、知识库与图谱", True),
            clone_paragraph(
                body_template,
                "论文页面支持本地、arXiv、OpenAlex和Semantic Scholar检索，以及详情、导入、收藏、已读和Markdown笔记。智能推荐由后端依据课程分类、进度、成绩和论文数据计算分数及理由。教师管理WeKnora知识库和文档，学生选择知识库会话进行检索增强问答，并查看知识图谱。",
            ),
            clone_paragraph(
                body_template,
                "核心接口覆盖/api/paper、智能推荐以及/api/weknora下的知识库、文档、会话、搜索和SSE问答接口。截图需要标明本地与外部数据来源，并补充WeKnora不可用时的错误提示。",
            ),
            clone_paragraph(caption_template, "图7-8 论文、推荐、知识库问答与图谱页面", True),
            clone_paragraph(level3_template, "7.2.6  通知、文件与操作日志", True),
            clone_paragraph(
                body_template,
                "教师或管理员发布系统、课程或班级通知，用户只能查看与本人数据范围相符的内容。统一文件接口服务课程、作业和知识库上传；管理请求与Agent确认执行由日志模块记录操作人、方法、地址、状态和耗时，管理员与教师在/operation-logs查询详情。",
            ),
            clone_paragraph(
                body_template,
                "核心接口包括/notices、/files和/operation-logs，涉及t_notice、t_operation_log及本地uploads目录。截图应避免显示Token和密钥，并让日志中的地址、时间和状态与页面操作对应。",
            ),
            clone_paragraph(caption_template, "图7-9 通知、文件与操作日志页面", True),
            clone_paragraph(level3_template, "7.2.7  勤工俭学管理", True),
            clone_paragraph(
                body_template,
                "教师或管理员维护岗位和招聘状态、审核学生申请并管理劳务费名单；学生查看招聘岗位、提交本人申请并在/my-work-study查询进度。名单支持手动维护和Excel/CSV导入导出，金额由工作时长与适用时薪计算，错误行返回学号、行号或格式原因。",
            ),
            clone_paragraph(
                body_template,
                "核心接口为/work-studies及其application、students、import和export子接口，涉及t_work_study和t_work_study_student。截图应串联岗位发布、学生申请、审核和劳务费结果。",
            ),
            clone_paragraph(caption_template, "图7-10 勤工俭学岗位、申请与劳务费页面", True),
            clone_paragraph(caption_template, "图7-11 功能操作对应的白底后端SQL日志", True),
            clone_paragraph(level2_template, "7.3  遗留问题", True),
            clone_paragraph(
                body_template,
                "外部模型、WeKnora、学术数据源和视频下载服务仍受网络、密钥、限流与服务状态影响。当前已提供超时和失败提示，但还需补充统一的健康检查、重试策略和外部服务监控。",
            ),
            clone_paragraph(
                body_template,
                "文件与视频主要保存在本地磁盘，适合课程项目和单机演示。长期运行需要对象存储、病毒扫描、分片上传、备份恢复和生命周期管理，并避免在报告截图或日志中泄露密钥、Token及个人信息。",
            ),
            clone_paragraph(
                body_template,
                "工程仍保留部分未注册的兼容Controller和新旧作业评测数据结构。当前主线已统一到新的评测页面和接口，后续应明确迁移完成条件、删除计划和接口版本，减少重复模型与维护成本。",
            ),
            clone_paragraph(
                body_template,
                "当前只有2个Java测试文件和18处@Test，虽然接口文档记录过Apifox回归，但原始报告需要重新生成并归档。并发考试、批量导入、文件上传、Agent超时和SSE长连接仍需在接近真实班级规模的数据上完成压力与恢复测试。",
            ),
            clone_paragraph(
                body_template,
                "AI回答与备课质量依赖提示词、知识库资料和模型状态。后续应完善回答引用、知识库权限隔离、敏感内容检查、生成结果版本记录及效果评测，所有生成内容继续由教师人工确认后使用。",
            ),
        ]
    )
    replace_body_block(document, start, end, elements)


def build_chapter_8(document: Document):
    start = find_body_paragraph(document, lambda t: t.startswith("8  "))
    end = find_body_paragraph(document, lambda t: t.startswith("9  "))
    chapter_template = find_paragraph(document, lambda t: t.startswith("8  "))
    level2_template = find_paragraph(document, lambda t: t.startswith("8.1"))
    level3_template = find_paragraph(document, lambda t: t.startswith("8.2.1"))
    body_template = find_paragraph(document, lambda t: t.startswith("本项目中的AI"))
    caption_template = find_paragraph(document, lambda t: t.startswith("图8-1"))
    table_caption_template = find_paragraph(document, lambda t: t.startswith("表8-1"))
    ai_table = next(
        deepcopy(table._tbl)
        for table in document.tables
        if table.cell(0, 0).text.strip() == "序号" and table.cell(0, 1).text.strip() == "项目阶段"
    )

    elements = [
        clone_paragraph(chapter_template, "8  AI辅助", True),
        clone_paragraph(
            body_template,
            "本项目中的AI分为两类：一类是系统向用户提供的Agent、知识库问答、论文检索辅助和智能备课功能；另一类是开发过程中用于需求核对、报错解释、测试补充和文档校对的局部辅助。业务规则、数据结构、最终代码、测试结论和报告事实均由成员结合源码、日志和运行结果确认。",
        ),
        clone_paragraph(
            body_template,
            "AI输出按“直接采纳、人工修改后采纳、不采纳”三类记录。只有能够被源码、接口响应、数据库脚本或实际操作证明的内容才写入报告；无法验证的字段、功能数量、测试数字和性能结论不采用。AI不会代替成员完成整套系统、批量生成未经理解的代码，也不会把生成内容直接作为最终教学材料。",
        ),
        clone_paragraph(level2_template, "8.1  AI工具介绍", True),
        clone_paragraph(
            body_template,
            "开发与文档核对主要使用Codex类代码助手和DeepSeek。Codex用于跨文件检索、接口与路由对照、数据库关系检查、错误定位和检查清单整理；DeepSeek既作为平台接入的对话模型，也用于比较提示词表达和生成效果。工具只能处理成员提供的上下文，不能替代本地构建、数据库校验、接口测试和现场验收。",
        ),
        clone_paragraph(
            body_template,
            "AI的优势是能快速汇总分散信息并提出验证路径，风险是可能引用旧接口、混淆同步与SSE链路、虚构字段或把目标值写成实测结果。因此采用建议前必须回到Controller、Vue Router、建库脚本、测试源码和真实运行日志复核，涉及密钥、Token和个人数据的内容不得输入公共模型或放入报告截图。",
        ),
        clone_paragraph(level2_template, "8.2  AI工具主要用途", True),
        clone_paragraph(
            body_template,
            "表8-1中的百分比表示全部AI辅助活动在不同阶段的投入分布，合计100%，并不表示AI完成了对应比例的代码、文档或项目工作。每项辅助结果均经过成员复核，表中同时说明人工确认方式。",
        ),
        clone_paragraph(table_caption_template, "表8-1 AI辅助活动分布及人工复核方式", True),
        ai_table,
        clone_paragraph(level3_template, "8.2.1  需求范围核对", True),
        clone_paragraph(
            body_template,
            "向AI提供三类角色、当前路由和Controller清单，要求按角色整理功能边界。初稿用于发现行政班、视频处理、学习路线、AI备课等遗漏；成员逐项核对前后端实现，删除不存在或已废弃的能力后再写入需求分析。",
        ),
        clone_paragraph(level3_template, "8.2.2  数据库关系检查", True),
        clone_paragraph(
            body_template,
            "使用AI检查课程、班级、学生、课时、评测和Agent数据之间的重复关系，并生成主外键核对清单。最终以ai_academy_schema.sql中的45条CREATE TABLE、外键和唯一约束为准；AI提出但脚本中不存在的字段和级联规则不采纳。",
        ),
        clone_paragraph(level3_template, "8.2.3  前后端接口对齐", True),
        clone_paragraph(
            body_template,
            "页面调用失败时，将请求方法、路径、参数和Controller签名并列交给AI比较。成员通过Vue Router、浏览器网络面板和后端日志确认差异，只采纳能够重现并通过接口验证的修改建议；旧兼容接口不会被当作当前主线功能重复统计。",
        ),
        clone_paragraph(level3_template, "8.2.4  作业考试流程调试", True),
        clone_paragraph(
            body_template,
            "围绕题卷预检、发布、开始答题、保存答案、交卷、自动判分和人工批改，让AI列出状态越界和权限风险。团队据此检查未发布评测、超时提交、重复交卷、主观题超分和跨班访问；未实际执行的并发场景仅列为后续测试，不写成通过结论。",
        ),
        clone_paragraph(level3_template, "8.2.5  Agent与知识库排错", True),
        clone_paragraph(
            body_template,
            "Agent或WeKnora请求异常时，AI用于区分普通同步会话、待确认写动作和知识库SSE链路，并检查参数、超时和错误结束逻辑。实际结论以服务响应和本地日志为准；把Agent原生会话统一描述为流式返回等不符合源码的建议不采纳。",
        ),
        clone_paragraph(level3_template, "8.2.6  测试用例补充", True),
        clone_paragraph(
            body_template,
            "AI根据接口约束补充空值、非法输入、权限不足、外键冲突和外部服务不可用场景。只有由JUnit、Apifox或真实操作执行并保留结果的用例才计入已验证范围；当前报告如实记录2个Java测试文件、18处@Test以及接口文档中的4组Apifox场景。",
        ),
        clone_paragraph(level3_template, "8.2.7  报告内容校对", True),
        clone_paragraph(
            body_template,
            "AI用于检查章节结构、术语、接口名称和统计数字是否一致，成员再通过自动扫描核对21个模块、279个已注册接口、45张物理表、45个Vue视图和46条命名路由。旧统计、缺少证据的性能结论和把接口数等同独立功能数的表述均不采用。",
        ),
        clone_paragraph(caption_template, "图8-1 AI辅助开发的提示词、人工复核与采用结果", True),
    ]
    replace_body_block(document, start, end, elements)

    table = next(
        table
        for table in document.tables
        if table.cell(0, 0).text.strip() == "序号" and table.cell(0, 1).text.strip() == "项目阶段"
    )
    table.cell(0, 2).text = "AI辅助活动分布"
    for row_index, row in enumerate(table.rows):
        prevent_row_split(row, repeat_header=(row_index == 0))


def build_chapter_9(document: Document):
    start = find_body_paragraph(document, lambda t: t.startswith("9  "))
    appendix_break = None
    for element in document._body._element:
        if element.tag.endswith("}p") and element.xpath('.//*[local-name()="sectPr"]'):
            if list(document._body._element).index(element) > list(document._body._element).index(start):
                appendix_break = element
                break
    if appendix_break is None:
        raise ValueError("Appendix section break not found")
    chapter_template = find_paragraph(document, lambda t: t.startswith("9  "))
    level2_template = find_paragraph(document, lambda t: t.startswith("9.1"))
    body_template = find_paragraph(document, lambda t: t.startswith("项目规模扩大后"))

    elements = [
        clone_paragraph(chapter_template, "9  经验总结", True),
        clone_paragraph(level2_template, "9.1  项目管理经验", True),
        clone_paragraph(
            body_template,
            "项目规模扩大后，最容易出现的问题不是单个页面无法完成，而是同一概念在路由、接口、实体和数据表中命名不一致，以及新旧兼容链路被重复统计。团队应在迭代开始时确定角色、资源归属、状态枚举、接口版本和统计口径，并让接口文档、建库脚本和报告随代码同步更新。",
        ),
        clone_paragraph(
            body_template,
            "任务拆分应围绕可演示的业务闭环，而不是只按前端、后端和数据库分工。课程学习、作业考试、Agent动作确认等模块都应在一次迭代中完成页面操作、接口响应、数据库结果和错误处理，合并前再通过交叉检查确认成员能够解释所负责的实现。",
        ),
        clone_paragraph(level2_template, "9.2  技术经验与教训", True),
        clone_paragraph(
            body_template,
            "鉴权、异常封装、文件上传、事务和操作日志属于公共能力，应尽早统一。评测提交、批量导入和Agent写操作必须由后端校验身份、状态与资源范围；外部模型、论文、知识库和视频服务需要独立超时及降级路径，不能因第三方异常阻塞本地教学主流程。",
        ),
        clone_paragraph(
            body_template,
            "保留兼容接口有助于短期迁移，但会造成模型、状态和统计重复。后续应通过集成测试保护数据迁移，明确旧Controller、旧作业表和兼容路由的删除条件。当前功能测试规模有限，文件、批量数据、并发评测和SSE长连接还需要容量、压力和恢复测试。",
        ),
        clone_paragraph(level2_template, "9.3  AI工具使用体会", True),
        clone_paragraph(
            body_template,
            "AI适合阅读大量代码、整理检查项和提出验证顺序，但它不了解未写入仓库的约定，也可能把示例、旧接口或目标指标当成当前事实。有效的使用方式是让AI给出可验证假设，再由成员通过源码、日志、测试和真实操作判断采用、修改或拒绝，而不是追求看起来完整但无法证明的答案。",
        ),
        clone_paragraph(level2_template, "9.4  后续改进建议", True),
        clone_paragraph(
            body_template,
            "后续可建立持续集成，统一执行后端测试、前端构建和接口回归；部署侧补充对象存储、配置密钥管理、监控告警、自动备份和恢复演练；业务侧完成作业考试兼容链路迁移，完善知识库权限、AI回答引用与生成内容版本，并在真实教学数据规模下记录P95响应时间和成功率。",
        ),
    ]
    replace_body_block(document, start, appendix_break, elements)


def transplant_appendices(document: Document, reference: Document):
    source_a = find_body_paragraph(document, lambda t: t.startswith("附录A"))
    source_b_break = None
    source_b = find_body_paragraph(document, lambda t: t.startswith("附录B"))
    for element in document._body._element:
        if element.tag.endswith("}p") and element.xpath('.//*[local-name()="sectPr"]'):
            if list(document._body._element).index(source_a) < list(document._body._element).index(element) < list(document._body._element).index(source_b):
                source_b_break = element
                break
    if source_b_break is None:
        raise ValueError("Appendix B section break not found")

    ref_a = find_body_paragraph(reference, lambda t: t.startswith("附录A"))
    ref_b = find_body_paragraph(reference, lambda t: t.startswith("附录B"))
    ref_elements = list(reference._body._element)
    ref_a_index = ref_elements.index(ref_a)
    ref_b_index = ref_elements.index(ref_b)
    ref_b_break_index = next(
        i for i in range(ref_a_index, ref_b_index)
        if ref_elements[i].tag.endswith("}p") and ref_elements[i].xpath('.//*[local-name()="sectPr"]')
    )
    appendix_a_elements = [deepcopy(e) for e in ref_elements[ref_a_index:ref_b_break_index]]
    replace_body_block(document, source_a, source_b_break, appendix_a_elements)

    source_b = find_body_paragraph(document, lambda t: t.startswith("附录B"))
    body_elements = list(document._body._element)
    final_sect = next(e for e in body_elements if e.tag.endswith("}sectPr"))
    ref_final_sect = next(e for e in ref_elements if e.tag.endswith("}sectPr"))
    appendix_b_elements = [deepcopy(e) for e in ref_elements[ref_b_index:ref_elements.index(ref_final_sect)]]
    replace_body_block(document, source_b, final_sect, appendix_b_elements)

    for paragraph in document.paragraphs:
        text = paragraph.text.strip()
        if text.startswith("说明：接口以当前42个"):
            set_text_keep_format(
                paragraph,
                "说明：接口以当前42个带@RestController的控制器为准，已排除6个仅保留源码但未注册的兼容Controller；数据表以database/ai_academy_schema.sql为准；页面以Vue Router当前46条命名路由为准。",
            )
        elif text.startswith("（1）系统功能：按当前源码统计"):
            set_text_keep_format(
                paragraph,
                "（1）系统功能：按当前源码统计，功能模块21个，已注册方法级HTTP接口279个，数据库物理表45张，Vue视图文件45个，前端命名路由46条（43条MainLayout子路由，另含登录、注册和404）。",
            )

    appendix_table = next(table for table in document.tables if len(table.rows) == 280)
    appendix_table.cell(0, 3).text = "接口功能说明"
    for row_index, row in enumerate(appendix_table.rows):
        prevent_row_split(row, repeat_header=(row_index == 0))
        for cell in row.cells:
            for paragraph in cell.paragraphs:
                for run in paragraph.runs:
                    configure_run_font(run, 7.5, bold=(row_index == 0))
    for row in appendix_table.rows[1:]:
        if row.cells[1].text.strip() == "系统审计":
            row.cells[6].text = "操作日志"
            row.cells[7].text = "OperationLogList.vue"


def update_chapter_3_and_4_facts(document: Document):
    replacements = {
        "四、系统总体设计": "4  系统总体设计",
        "本系统由 7 大功能模块组成：公共门户与认证模块、角色与权限体系模块、教学业务中心模块、考试测评中心模块、AI 智能能力中心模块、论文服务中心模块、勤工俭学中心模块以及系统管理模块。":
            "本系统按职责归纳为8个功能域，并在附录A中进一步细分为21个可核验模块：公共门户与认证、角色与权限、教学业务、作业考试评测、AI智能能力、论文与推荐、勤工俭学以及系统管理。各功能域共享统一登录、异常返回、文件管理和操作日志等公共能力。",
        "教学业务中心模块包含课程管理、教学班管理、作业管理和学习过程四个子模块。课程管理子模块负责课程、章节、课时及课程分类的维护；教学班管理子模块负责教学班的创建与学生组织；作业管理子模块支持教师布置与批改作业、学生提交作业；学习过程子模块记录学生的课程学习、课时播放、资料下载及学习进度。":
            "教学业务域包含课程与分类、课程资源、行政班、教学班和学习过程。课程按照分类—课程—章节—课时组织，课时支持Markdown、互动内容、文件与视频；行政班由管理员维护基础成员，教学班围绕课程组织教学活动；学习过程记录进度、时长、播放位置、笔记和评论。",
        "考试测评中心模块包含题库管理、试卷管理、考试管理和学生考试四个子模块。题库管理子模块负责题目的增删改查、PDF 抽取及批量导入；试卷管理子模块负责试卷的创建、题目配置与复制；考试管理子模块支持考试的创建、发布、关闭与批改；学生考试子模块支持学生参加考试、保存答案、提交试卷及查看成绩分析。":
            "作业考试评测域按照题库、题卷、评测场次、学生答卷、答案明细和评阅结果组织，同时支持批量导入导出。考试和作业复用评测页面及主要状态流转，客观题自动判分、主观题人工批改，完成后生成成绩、统计和导出结果。",
        "AI 智能能力中心模块包含智能学伴/知识库问答、教师知识库管理、Agent 助手、智能资源生成和个性化智能推荐五个子模块。智能学伴子模块为学生提供基于知识库的流式问答和文件预览服务；教师知识库管理子模块支持教师创建知识库、上传和删除课件文档；Agent 助手子模块提供 Agent 会话、定义管理、知识文档上传和单轮调试功能；智能资源生成子模块辅助生成教学大纲、课堂讲义、练习题库和项目案例；个性化智能推荐子模块为用户提供课程、练习和拓展资料的推荐服务。":
            "AI智能能力域包含Agent会话与定义、平台读写工具、动作确认、智能备课、WeKnora知识库问答和个性化推荐。普通对话、平台数据查询与写操作规划采用不同处理路径，写操作只允许教师或管理员确认；备课生成内容以草稿形式保存，人工修改后才发布。",
        "系统管理模块提供用户管理、分类管理和管理员全局权限配置功能，由管理员专属使用。":
            "系统管理域提供用户与角色、教师申请、课程分类、行政班、文件和操作日志等管理入口。管理员拥有全局数据范围，教师仅管理本人课程及教学班，学生只能访问本人或所在班级的数据。",
        "系统管理模块独立于各业务模块之上，对用户管理、分类管理等基础数据进行统一管理，为全系统提供基础数据支撑":
            "系统管理域位于各业务模块之上，对用户、教师申请、课程分类、行政班和审计数据进行统一管理，为全系统提供基础数据与运行追踪支撑。",
        "项目使用Git进行版本管理和团队协作，后端采用Maven构建，前端采用npm与Vite构建，Python视频辅助服务使用独立虚拟环境。后端测试以JUnit 5、MockMvc和H2兼容数据库为主，前端使用Playwright进行端到端检查；这些功能测试不能替代并发与P95响应时间测试。":
            "项目使用Git进行版本管理和团队协作，后端采用Maven构建，前端采用npm与Vite构建，Python视频辅助服务使用独立虚拟环境。当前主后端保留2个JUnit测试文件和18处@Test；前端依赖中包含Playwright，但当前工作区未保留独立spec用例，需在提交前重新整理端到端测试。上述功能检查不能替代并发与P95响应时间测试。",
    }
    for paragraph in document.paragraphs:
        text = paragraph.text.strip()
        if text in replacements:
            set_text_keep_format(paragraph, replacements[text])

    tech_table = next(
        table for table in document.tables
        if table.cell(0, 0).text.strip() == "层次" and table.cell(0, 1).text.strip() == "技术选型"
    )
    versions = {
        "Vue": "3.5.38",
        "Vite": "8.1.0",
        "Element Plus": "2.14.2",
        "Pinia": "3.0.4",
        "Spring Boot": "3.5.16",
        "MyBatis Plus": "3.5.12",
        "MySQL": "8.0",
        "JWT": "0.12.6",
        "Spring AI、 DeepSeek": "依赖管理 / 外部API",
        "WeKnora": "外部服务",
        "arXiv/OpenAlex/SemanticScholar": "外部API",
    }
    for row in tech_table.rows[1:]:
        selection = row.cells[1].text.strip()
        if selection in versions:
            row.cells[2].text = versions[selection]


def normalize_document_format(document: Document):
    chapter3_index = next(i for i, p in enumerate(document.paragraphs) if p.text.strip().startswith("3  "))
    for index, paragraph in enumerate(document.paragraphs):
        if index < chapter3_index:
            continue
        text = paragraph.text.strip()
        if not text:
            continue
        normalized = text
        if re.match(r"^\d+(?:\.\d+){1,2}\S", normalized):
            match = re.match(r"^(\d+(?:\.\d+){1,2})(.*)$", normalized)
            normalized = f"{match.group(1)}  {match.group(2).lstrip()}"
        caption_match = re.match(r"^([图表]\d+-\d+)(.*)$", normalized)
        if caption_match:
            normalized = f"{caption_match.group(1)} {caption_match.group(2).lstrip()}"
        if normalized != text:
            set_text_keep_format(paragraph, normalized)
            text = normalized

        if re.match(r"^\d+  ", text):
            for run in paragraph.runs:
                configure_run_font(run, 14, True)
            paragraph.paragraph_format.keep_with_next = True
        elif re.match(r"^\d+\.\d+  ", text):
            for run in paragraph.runs:
                configure_run_font(run, 12, True)
            paragraph.paragraph_format.keep_with_next = True
        elif re.match(r"^\d+\.\d+\.\d+  ", text):
            for run in paragraph.runs:
                configure_run_font(run, 10.5, True)
            paragraph.paragraph_format.keep_with_next = True
        elif re.match(r"^[图表]\d+-\d+ ", text):
            paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
            paragraph.paragraph_format.keep_with_next = text.startswith("表")
            for run in paragraph.runs:
                configure_run_font(run, 9, False)


def media_hashes(path: Path):
    with ZipFile(path) as archive:
        return {
            name: hashlib.sha256(archive.read(name)).hexdigest()
            for name in archive.namelist()
            if name.startswith("word/media/")
        }


def canonical_prefix_hash(path: Path):
    document = Document(path)
    body = document._body._element
    first_chapter = find_body_paragraph(document, lambda t: t.startswith("1  "))
    parts = []
    for element in list(body)[:list(body).index(first_chapter)]:
        parts.append(element)
    payload = b"".join(
        __import__("lxml.etree", fromlist=["tostring"]).tostring(
            element, method="c14n", exclusive=True, with_comments=False
        )
        for element in parts
    )
    return hashlib.sha256(payload).hexdigest()


def validate(document_path: Path, source_path: Path):
    with ZipFile(document_path) as archive:
        if archive.testzip() is not None:
            raise AssertionError("DOCX ZIP integrity failed")
    if media_hashes(document_path) != media_hashes(source_path):
        raise AssertionError("Existing media hashes changed")
    if canonical_prefix_hash(document_path) != canonical_prefix_hash(source_path):
        raise AssertionError("Cover or evaluation-page content changed")

    document = Document(document_path)
    all_text = "\n".join(p.text for p in document.paragraphs)
    stale = [
        "功能模块17个",
        "功能点193个",
        "核心数据表27个",
        "43个Vue视图",
        "45个Spring Controller",
        "53个测试类",
        "347处@Test",
        "图8-1（待补）",
    ]
    found = [phrase for phrase in stale if phrase in all_text]
    if found:
        raise AssertionError(f"Stale phrases remain: {found}")
    if len(document.inline_shapes) != 15:
        raise AssertionError(f"Inline image count changed: {len(document.inline_shapes)}")
    if not any(len(table.rows) == 280 for table in document.tables):
        raise AssertionError("Current 279-interface appendix table missing")
    if "功能模块21个" not in all_text or "46条命名路由" not in all_text:
        raise AssertionError("Current project statistics missing")
    return {
        "paragraphs": len(document.paragraphs),
        "tables": len(document.tables),
        "images": len(document.inline_shapes),
        "appendix_rows": max(len(table.rows) for table in document.tables),
    }


def main():
    for path in [SOURCE, CHAPTER34_REFERENCE, APPENDIX_REFERENCE, SCHEMA]:
        if not path.exists():
            raise FileNotFoundError(path)

    document = Document(SOURCE)
    chapter_reference = Document(CHAPTER34_REFERENCE)
    appendix_reference = Document(APPENDIX_REFERENCE)
    schema_sql, schema_tables = parse_schema(SCHEMA)

    old_business_goal = "保证过程可追溯。对重要操作、成绩导入、通知发布和异常结果保留日志或状态记录，方便管理员和教师查询问题来源。"
    new_business_goal = "引入智能协作。通过Agent助手连接课程、教学班、评测和成绩等业务数据，为学生提供学习答疑，为教师提供数据查询与备课支持；涉及业务写入的动作先生成待确认方案，经用户确认后执行，使智能辅助能够安全参与教学流程。"
    business_goal_paragraph = find_paragraph(document, lambda t: t == old_business_goal)
    set_text_keep_format(business_goal_paragraph, new_business_goal)

    compatibility_paragraph = find_paragraph(
        document,
        lambda t: t == "兼容性：前端页面应在Chrome、Edge、Firefox和Safari等主流现代浏览器中正常使用。",
    )
    cross_device_paragraph = clone_paragraph(
        compatibility_paragraph,
        "跨终端适配：系统应同时支持电脑端和手机端访问，在常见桌面分辨率及宽度不低于375px的移动端视口下，页面布局能够自适应，导航、表单、列表和主要操作正常显示且无明显卡顿。",
    )
    compatibility_paragraph._p.addnext(cross_device_paragraph)

    transplant_chapters_3_and_4(document, chapter_reference)
    update_chapter_3_and_4_facts(document)

    chapter5_texts = [
        "5  系统详细设计",
        "5.1  用户、权限与首页统计模块",
        "5.1.1  模块描述",
        "用户与权限模块提供公共首页、注册登录、个人资料、教师申请、用户管理和角色授权，是平台其他业务的统一入口。t_user保存账号与状态，t_role和t_user_role维护admin、teacher、student三类角色，t_teacher_application记录教师申请。前端由Vue Router和Pinia控制登录状态、菜单及页面入口，后端通过JWT、用户上下文和资源归属再次鉴权。",
        "首页统计根据角色返回不同数据：管理员查看用户、教师申请、课程分类和平台资源，教师查看本人课程、教学班、评测及待批改任务，学生进入个人学习入口。管理员可以启停账号、调整角色并审核教师申请；普通用户只能维护本人资料。密码、Token和密钥不以明文返回，校验失败统一返回业务码和可读提示。",
        "5.1.2  流程描述",
        "用户通过/auth/register注册或通过/auth/login登录，后端依次完成参数、账号状态和密码校验，成功后签发JWT并返回角色。Axios拦截器附加令牌，路由守卫加载对应菜单；受保护请求到达后端后再次校验身份和数据范围。首页按角色并行查询统计数据，单个统计接口失败时显示降级提示而不影响其他卡片。教师申请提交后由管理员审核，角色变更后重新获取用户信息。",
        "图5-1 用户登录、角色校验与首页统计流程图",
        "5.1.3  界面设计",
        "相关页面包括LoginLanding.vue、PublicHome.vue、Login.vue、Register.vue、Home.vue、UserManage.vue、TeacherApplications.vue和ProfileSettings.vue，对应/、/home、/login、/register、/console、/users、/teacher-applications和/profile。表单统一进行必填、格式与状态校验，管理操作完成后刷新列表，越权访问由页面和接口共同阻止。",
        "图5-2 登录、用户管理、教师审核与首页统计界面",
        "5.2  课程资源、行政班、教学班与学习过程模块",
        "5.2.1  模块描述",
        "教学资源按照课程分类—课程—章节—课时组织，课时支持Markdown课本、互动内容、附件、本地视频和公开视频URL。t_course_category、t_course、t_chapter、t_lesson和video_resource保存资源结构；行政班由管理员维护，教学班由教师围绕课程创建，两类班级分别通过成员表关联学生。学习过程使用t_learning_progress、t_learning_record、study_note和lesson_comment保存进度、时长、播放位置、笔记与评论。",
        "教师只能维护本人课程及教学班，学生只能访问已加入班级的课程内容。普通文件由统一上传接口保存，单个请求限制100MB；公开视频URL交给独立yt-dlp服务异步处理，失败时记录原因并允许重试。资源服务异常不会覆盖课时正文和学生已有进度。",
        "5.2.2  流程描述",
        "管理员先维护课程分类和行政班，教师创建课程、章节、课时及教学班并加入学生。学生从我的班级或学习路线进入课程，系统根据章节、课时和已有进度生成学习地图，加载正文、互动内容、文件或视频并恢复上次位置；前端定时上报进度和时长，学生可保存笔记与评论。课时完成后更新总体完成度并解锁后续节点，视频处理失败时页面显示状态和重试入口。",
        "图5-3 课程资源、班级组织与学习过程流程图",
        "5.2.3  界面设计",
        "管理页面包括CategoryManage.vue、AdministrativeClassManage.vue、CourseList.vue、CourseForm.vue、CourseDetail.vue和TeachingClassList.vue；学生页面包括MyClassList.vue、LearningRoutes.vue、LearningMap.vue和CourseLearn.vue。课程详情集中维护章节、课时及资源状态，学习页统一渲染Markdown、互动实验、附件和视频，同时展示进度、笔记及评论。",
        "图5-4 课程详情、班级管理、学习路线与课时学习界面",
        "5.3  作业、考试、成绩与批量导入模块",
        "5.3.1  模块描述",
        "评测业务按照题库—题卷—评测场次—学生答卷—答案明细—评阅结果组织。题库支持单选、多选、判断、填空和简答题，并提供Excel/CSV导入导出、PDF题目抽取和知识库生成题目。考试使用t_exam_paper、t_exam_session、t_exam_record和t_exam_answer，作业逐步使用对应的homework_paper、homework_session、homework_record和homework_answer结构，旧接口仅保留兼容读取。",
        "教师设置教学班、起止时间、题目顺序和分值后发布评测；学生提交时系统校验身份、状态、时间和重复交卷，客观题自动判分，主观题进入人工批改。完成批改后生成成绩和统计，GradeAnalysis.vue展示趋势、分布、平均分及合格率。批量导入记录总数、成功数、失败数和逐行错误，相关多表写入使用事务。",
        "5.3.2  流程描述",
        "教师维护题库并创建考试试卷或作业题卷，保存前执行题目、总分和必答配置预检；随后创建评测场次、选择教学班并发布。学生开始作答后建立答卷，过程中持续保存答案，交卷后系统自动评分客观题并锁定答卷；教师完成主观题评分后汇总成绩并可导出。导入文件先校验模板、字段和学生身份，失败行返回原因且不会覆盖已成功记录。",
        "图5-5 题库、作业考试、批改与成绩生成流程图",
        "5.3.3  界面设计",
        "教师页面包括QuestionList.vue、PaperList.vue、ExamList.vue、ExamGrading.vue、ExamResults.vue、GradeAnalysis.vue及复用的作业评测入口；学生通过MyExamList.vue、MyHomeworkList.vue、ExamTaking.vue、ExamResult.vue和MyGrades.vue完成考核与查询。页面使用状态标签区分草稿、已发布、进行中、待批改和已完成，并在导入对话框中显示逐行错误。",
        "图5-6 题库、在线作业考试、批改与成绩分析界面",
        "5.4  Agent、AI备课与动作确认模块",
        "5.4.1  模块描述",
        "Agent模块由定义、会话、消息和待确认动作组成，保存于t_agent、t_agent_session、t_agent_message和t_agent_pending_action。登录用户可进行普通对话，教师或管理员还能使用平台数据读工具；统一规划器区分普通聊天、查询和写操作，学生不能发起平台数据写入。t_ai_preparation保存AI备课阶段、输入、草稿和发布状态。",
        "写操作不会由模型直接执行，而是先生成包含动作类型、目标对象和参数的待确认记录；用户确认时后端重新校验角色、参数和业务状态，再调用白名单执行器并记录操作日志。AI备课按照大纲、课时和题目分阶段生成，教师人工修改后才发布。模型、知识库或网络异常时保留会话及草稿并返回可恢复提示。",
        "5.4.2  流程描述",
        "用户输入问题后，后端加载Agent配置和会话上下文，根据意图进入普通回答、只读工具或写计划。参数不足时返回补充问题，对象不唯一时返回候选；写计划校验后保存为待确认动作，只有教师或管理员确认才执行。备课模块读取主题、教学目标、参考文件或知识库，分阶段生成草稿，教师审核编辑后写入课程、章节、课时或题库。Agent原生会话为同步返回，知识库问答使用SSE，两条链路分别处理超时。",
        "图5-7 Agent会话、AI备课与动作确认流程图",
        "5.4.3  界面设计",
        "主要页面和组件为AiChat.vue、AgentFloatingAssistant.vue、AiChatWidget.vue及IntelligentResourceGen.vue。会话界面显示Agent、历史消息和输入区；待确认卡片突出动作、目标、参数、取消和确认入口；备课页展示阶段进度、参考资料、草稿编辑及发布结果，避免将模型输出误认为正式教学内容。",
        "图5-8 Agent会话、动作确认与AI备课界面",
        "5.5  论文、智能推荐、知识库与图谱模块",
        "5.5.1  模块描述",
        "论文服务统一接入本地库、arXiv、OpenAlex和Semantic Scholar，支持关键词、作者、来源、年份、引用量、开放获取和相关度筛选。t_paper、t_paper_author和paper_user_mark保存本地论文、作者、收藏、已读及笔记，外部论文导入按来源标识去重。智能推荐根据课程分类、学习进度、成绩薄弱项和论文数据计算课程、课时、练习及论文分数，前端只展示后端给出的理由和影响因素。",
        "知识库模块通过WeKnora管理知识库、文档、会话、检索和知识图谱。教师创建知识库、上传课件并查看解析状态，学生选择知识库进行检索增强问答；SSE转发回答片段并支持停止会话。知识库正文由WeKnora保存，本系统不把外部内容伪装成本地数据，服务不可用时明确提示。",
        "5.5.2  流程描述",
        "论文检索根据source选择本地或外部服务并转换为统一结果，用户可查看详情、导入、收藏或记录笔记；推荐接口汇总本人课程、进度、成绩和论文标记，输出分数与解释。知识库问答先选择知识库并创建会话，再发送问题和知识库编号，后端转发WeKnora的SSE片段；网络中断时结束连接并保留会话状态。",
        "图5-9 论文检索、智能推荐与知识库问答流程图",
        "5.5.3  界面设计",
        "相关页面包括PaperSearch.vue、IntelligentRecommendation.vue、TeacherKnowledgeBase.vue、KnowledgeBase.vue和KnowledgeGraph.vue。论文页面标识数据来源、筛选条件和外部链接；推荐页显示推荐分数与理由；知识库页面显示文档解析、会话和流式状态，知识图谱页展示节点、关系及来源。",
        "图5-10 论文、推荐、知识库与知识图谱界面",
        "5.6  通知、文件、日志与勤工俭学模块",
        "5.6.1  模块描述",
        "通知支持系统、课程和教学班等范围，后端按角色及成员关系筛选可见数据。统一文件模块提供上传、访问和删除能力，为课程、作业、知识库及视频资源服务。操作日志记录用户、请求方法、地址、状态、耗时、摘要和IP，用于故障定位及Agent写操作审计；OperationLogList.vue为管理员和教师提供独立查询页面。",
        "勤工俭学模块维护岗位、学生申请、审核和劳务费名单，t_work_study保存岗位与招聘状态，t_work_study_student保存学生、月份、工时、时薪、金额和审核结果。名单支持手动维护及Excel/CSV导入导出，学生只能查看招聘岗位和本人申请，教师或管理员负责审核和数据维护。",
        "5.6.2  流程描述",
        "教师或管理员发布通知后，用户按课程、班级和个人范围查看；文件上传先校验大小与类型，再保存路径和业务关系；管理请求完成后日志拦截器记录状态与耗时。勤工俭学岗位发布后学生提交申请，系统检查身份、招聘状态和重复申请，审核通过后维护月度工时并计算金额；导入失败返回行号、学号和原因，导出补充学生与岗位名称。",
        "图5-11 通知、文件、日志与勤工俭学流程图",
        "5.6.3  界面设计",
        "相关页面包括NoticeList.vue、OperationLogList.vue、WorkStudyList.vue、MyWorkStudy.vue、WorkStudyManage.vue和WorkStudyStudentManage.vue，文件上传作为课程、评测与知识库页面的公共组件。通知与日志强调筛选和详情，岗位与劳务费页面突出申请状态、导入错误、计算结果和导出条件。",
        "图5-12 通知、文件、日志与勤工俭学界面",
    ]
    replace_chapter_texts(document, "5  ", "6  ", chapter5_texts)

    build_chapter_6(document, schema_sql, schema_tables)
    build_chapter_7(document)
    build_chapter_8(document)
    build_chapter_9(document)
    transplant_appendices(document, appendix_reference)
    normalize_document_format(document)

    TARGET.parent.mkdir(parents=True, exist_ok=True)
    document.save(TARGET)
    result = validate(TARGET, SOURCE)
    print(f"TARGET={TARGET}")
    for key, value in result.items():
        print(f"{key}={value}")


if __name__ == "__main__":
    main()
