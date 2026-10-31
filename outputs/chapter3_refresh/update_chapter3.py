from __future__ import annotations

import argparse
import io
import re
from copy import deepcopy
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn


BLUE = "#4E7DFF"
BLUE_DARK = "#2F5FD3"
BLUE_LIGHT = "#EEF4FF"
INK = "#2D3442"
MUTED = "#566174"
GRID = "#CAD6EA"
GREEN = "#19A66A"
WHITE = "#FFFFFF"


def find_font(*candidates: str) -> str:
    for candidate in candidates:
        path = Path(candidate)
        if path.exists():
            return str(path)
    raise FileNotFoundError(f"No usable font found: {candidates}")


REGULAR_FONT_PATH = find_font(
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\simsun.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
)
BOLD_FONT_PATH = find_font(
    r"C:\Windows\Fonts\msyhbd.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
    REGULAR_FONT_PATH,
)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(BOLD_FONT_PATH if bold else REGULAR_FONT_PATH, size=size)


def wrap_text(draw: ImageDraw.ImageDraw, text: str, text_font: ImageFont.FreeTypeFont, max_width: int) -> list[str]:
    lines: list[str] = []
    current = ""
    tokens = re.findall(r"[A-Za-z0-9_.:/+-]+|\s+|.", text, flags=re.S)
    for token in tokens:
        if token == "\n":
            lines.append(current.rstrip())
            current = ""
            continue
        candidate = current + token
        if current and draw.textlength(candidate, font=text_font) > max_width:
            lines.append(current.rstrip())
            current = token.lstrip()
        else:
            if not current and draw.textlength(token, font=text_font) > max_width:
                for char in token:
                    candidate = current + char
                    if current and draw.textlength(candidate, font=text_font) > max_width:
                        lines.append(current.rstrip())
                        current = char
                    else:
                        current = candidate
            else:
                current = candidate
    if current:
        lines.append(current)
    return lines or [""]


def draw_centered_lines(
    draw: ImageDraw.ImageDraw,
    box: tuple[float, float, float, float],
    text: str,
    text_font: ImageFont.FreeTypeFont,
    fill: str = INK,
    line_gap: int = 5,
) -> None:
    x1, y1, x2, y2 = box
    max_width = int(x2 - x1 - 24)
    max_height = int(y2 - y1 - 14)
    font_path = getattr(text_font, "path", REGULAR_FONT_PATH)
    font_size = int(getattr(text_font, "size", 20))
    while True:
        lines = wrap_text(draw, text, text_font, max_width)
        bbox = draw.textbbox((0, 0), "中Ag", font=text_font)
        line_height = bbox[3] - bbox[1] + line_gap
        total_height = line_height * len(lines) - line_gap
        if total_height <= max_height or font_size <= 14:
            break
        font_size -= 2
        text_font = ImageFont.truetype(font_path, size=font_size)
    y = y1 + (y2 - y1 - total_height) / 2
    for line in lines:
        width = draw.textlength(line, font=text_font)
        draw.text((x1 + (x2 - x1 - width) / 2, y), line, font=text_font, fill=fill)
        y += line_height


def arrow_head(draw: ImageDraw.ImageDraw, x: float, y: float, direction: str = "down", color: str = MUTED) -> None:
    size = 12
    if direction == "down":
        points = [(x, y), (x - size, y - size * 1.4), (x + size, y - size * 1.4)]
    elif direction == "up":
        points = [(x, y), (x - size, y + size * 1.4), (x + size, y + size * 1.4)]
    elif direction == "right":
        points = [(x, y), (x - size * 1.4, y - size), (x - size * 1.4, y + size)]
    else:
        points = [(x, y), (x + size * 1.4, y - size), (x + size * 1.4, y + size)]
    draw.polygon(points, fill=color)


def draw_connector(
    draw: ImageDraw.ImageDraw,
    start: tuple[float, float],
    end: tuple[float, float],
    color: str = MUTED,
    width: int = 4,
) -> None:
    x1, y1 = start
    x2, y2 = end
    if abs(x1 - x2) < 2:
        draw.line((x1, y1, x2, y2), fill=color, width=width)
    else:
        mid_y = (y1 + y2) / 2
        draw.line((x1, y1, x1, mid_y, x2, mid_y, x2, y2), fill=color, width=width, joint="curve")
    arrow_head(draw, x2, y2, "down", color)


def draw_lane_flow(
    output: Path,
    size: tuple[int, int],
    lanes: list[str],
    steps: list[tuple[int, str]],
) -> None:
    width, height = size
    image = Image.new("RGB", size, WHITE)
    draw = ImageDraw.Draw(image)
    margin = 28
    header_top = 24
    header_height = 62
    content_top = header_top + header_height
    content_bottom = height - 35
    lane_width = (width - margin * 2) / len(lanes)

    for index, lane in enumerate(lanes):
        x1 = margin + lane_width * index
        x2 = margin + lane_width * (index + 1)
        draw.rectangle((x1, header_top, x2, header_top + header_height), fill=BLUE_LIGHT, outline=GRID, width=2)
        draw_centered_lines(draw, (x1, header_top, x2, header_top + header_height), lane, font(28, bold=True), INK)
        draw.line((x1, content_top, x1, content_bottom), fill=GRID, width=2)
    draw.line((width - margin, content_top, width - margin, content_bottom), fill=GRID, width=2)

    step_gap = (content_bottom - content_top - 50) / max(1, len(steps))
    box_height = min(112, max(76, int(step_gap * 0.62)))
    text_size = 24 if lane_width >= 300 else 22
    centers: list[tuple[float, float]] = []
    boxes: list[tuple[float, float, float, float]] = []
    for index, (lane_index, text) in enumerate(steps):
        cx = margin + lane_width * (lane_index + 0.5)
        cy = content_top + 38 + step_gap * (index + 0.5)
        box_width = lane_width * 0.80
        box = (cx - box_width / 2, cy - box_height / 2, cx + box_width / 2, cy + box_height / 2)
        draw.rounded_rectangle(box, radius=24, fill=WHITE, outline=BLUE, width=3)
        draw_centered_lines(draw, box, text, font(text_size), INK)
        centers.append((cx, cy))
        boxes.append(box)

    for index in range(len(steps) - 1):
        start_box = boxes[index]
        end_box = boxes[index + 1]
        draw_connector(
            draw,
            ((start_box[0] + start_box[2]) / 2, start_box[3] + 2),
            ((end_box[0] + end_box[2]) / 2, end_box[1] - 2),
        )

    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output, format="PNG", optimize=True)


def draw_parallel_flows(output: Path, size: tuple[int, int], flows: list[tuple[str, list[str]]]) -> None:
    width, height = size
    image = Image.new("RGB", size, WHITE)
    draw = ImageDraw.Draw(image)
    margin = 24
    header_top = 22
    header_height = 66
    column_width = (width - margin * 2) / len(flows)
    for column, (title, steps) in enumerate(flows):
        x1 = margin + column_width * column
        x2 = margin + column_width * (column + 1)
        draw.rectangle((x1, header_top, x2, header_top + header_height), fill=BLUE_LIGHT, outline=GRID, width=2)
        draw_centered_lines(draw, (x1, header_top, x2, header_top + header_height), title, font(25, bold=True), INK)
        draw.line((x1, header_top + header_height, x1, height - 28), fill=GRID, width=2)

        gap = (height - header_top - header_height - 90) / len(steps)
        box_height = min(112, max(74, int(gap * 0.58)))
        boxes: list[tuple[float, float, float, float]] = []
        for index, text in enumerate(steps):
            cx = (x1 + x2) / 2
            cy = header_top + header_height + 42 + gap * (index + 0.5)
            box = (x1 + 24, cy - box_height / 2, x2 - 24, cy + box_height / 2)
            draw.rounded_rectangle(box, radius=22, fill=WHITE, outline=BLUE, width=3)
            draw_centered_lines(draw, box, text, font(21), INK)
            boxes.append(box)
        for index in range(len(boxes) - 1):
            b1, b2 = boxes[index], boxes[index + 1]
            draw_connector(draw, ((b1[0] + b1[2]) / 2, b1[3]), ((b2[0] + b2[2]) / 2, b2[1]))
    draw.line((width - margin, header_top + header_height, width - margin, height - 28), fill=GRID, width=2)
    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output, format="PNG", optimize=True)


def draw_role_cards(output: Path) -> None:
    width, height = 1800, 570
    image = Image.new("RGB", (width, height), WHITE)
    draw = ImageDraw.Draw(image)
    title_font = font(32, bold=True)
    title = "AI智学平台用例图"
    title_width = draw.textlength(title, font=title_font)
    draw.text(((width - title_width) / 2, 16), title, font=title_font, fill=INK)

    cards = [
        (
            "学生角色",
            [
                "浏览首页并注册登录",
                "查看班级、学习路线和学习地图",
                "学习课时、视频和互动内容",
                "记录进度、笔记并参与评论",
                "完成作业和考试",
                "查看成绩与分析",
                "使用智能学伴和Agent助手",
                "检索论文并查看智能推荐",
                "申请勤工俭学岗位",
                "查看通知和维护个人信息",
            ],
        ),
        (
            "教师角色",
            [
                "提交入驻申请并登录",
                "维护课程、章节、课时和视频资源",
                "管理教学班及学生成员",
                "维护题库、题卷、作业和考试",
                "批改主观题并查看结果",
                "发布通知并查询操作日志",
                "管理知识库和生成题目",
                "使用智能备课发布课程",
                "使用Agent助手和论文检索",
                "维护勤工俭学岗位及名单",
            ],
        ),
        (
            "管理员角色",
            [
                "管理用户、角色和教师审核",
                "维护课程分类和全局数据",
                "管理行政班及学生成员",
                "访问全局课程与评测数据",
                "管理通知和操作日志",
                "维护Agent定义和知识文档",
                "管理知识库与论文数据",
                "维护勤工俭学岗位及劳务名单",
                "处理异常数据和系统配置",
            ],
        ),
    ]

    margin_x = 28
    gap = 28
    card_top = 72
    card_bottom = height - 24
    card_width = (width - margin_x * 2 - gap * 2) / 3
    for index, (role, items) in enumerate(cards):
        x1 = margin_x + index * (card_width + gap)
        x2 = x1 + card_width
        draw.rounded_rectangle((x1, card_top, x2, card_bottom), radius=18, fill=WHITE, outline=BLUE, width=3)
        draw.rounded_rectangle((x1, card_top, x2, card_top + 58), radius=18, fill=BLUE_LIGHT, outline=BLUE, width=3)
        draw.rectangle((x1 + 2, card_top + 38, x2 - 2, card_top + 60), fill=BLUE_LIGHT)
        draw_centered_lines(draw, (x1, card_top, x2, card_top + 58), role, font(27, bold=True), INK)

        y = card_top + 72
        item_font = font(19)
        for item in items:
            draw.ellipse((x1 + 23, y + 7, x1 + 35, y + 19), outline=GREEN, width=3)
            lines = wrap_text(draw, item, item_font, int(card_width - 78))
            line_height = 27
            for line_index, line in enumerate(lines):
                draw.text((x1 + 47, y + line_index * line_height), line, font=item_font, fill=INK)
            y += max(34, line_height * len(lines))

    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output, format="PNG", optimize=True)


def generate_diagrams(output_dir: Path) -> dict[int, Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    diagrams: dict[int, Path] = {}

    diagrams[71] = output_dir / "figure-3-1-use-cases.png"
    draw_role_cards(diagrams[71])

    diagrams[79] = output_dir / "figure-3-2-auth.png"
    draw_lane_flow(
        diagrams[79],
        (900, 1700),
        ["用户", "系统", "管理员"],
        [
            (0, "访问公共首页，选择学生注册、教师申请或直接登录"),
            (1, "学生注册创建账号；教师申请保存为待审核记录"),
            (2, "审核教师入驻申请并建立教师账号"),
            (0, "输入用户名和密码登录"),
            (1, "校验账号状态和密码并签发JWT令牌"),
            (1, "前端路由与后端拦截器同时校验角色和资源范围"),
            (0, "进入对应工作区并访问授权功能"),
        ],
    )

    diagrams[86] = output_dir / "figure-3-3-course-video.png"
    draw_lane_flow(
        diagrams[86],
        (900, 1640),
        ["教师/管理员", "后端与视频服务", "学生"],
        [
            (0, "维护课程分类并创建课程"),
            (0, "添加章节、课时和教学内容"),
            (0, "上传文件或填写公开视频URL"),
            (1, "保存课时与资源元数据，视频URL进入处理队列"),
            (1, "yt-dlp服务异步下载并更新PENDING、READY或FAILED状态"),
            (0, "查看处理状态，失败时发起重试"),
            (2, "从班级和学习路线进入课程"),
            (1, "返回可访问的课时内容、文件或视频播放地址"),
            (2, "学习资源并上报学习进度"),
        ],
    )

    diagrams[95] = output_dir / "figure-3-4-class-management.png"
    draw_lane_flow(
        diagrams[95],
        (1100, 1390),
        ["管理员", "教师", "学生"],
        [
            (0, "维护行政班编号、名称、年级、专业和关联课程"),
            (0, "新增、调整或移除行政班学生成员"),
            (1, "围绕本人课程创建教学班并设置学期"),
            (1, "按用户名手动添加或移除教学班学生"),
            (2, "在“我的班级”查看已加入的教学班"),
            (2, "进入课程学习，接收班级通知、作业和考试"),
            (1, "按教学班管理教学活动和成员数据"),
        ],
    )

    diagrams[102] = output_dir / "figure-3-5-learning.png"
    draw_lane_flow(
        diagrams[102],
        (900, 1445),
        ["学生", "平台", "教师/Agent"],
        [
            (0, "选择我的班级或学习路线"),
            (1, "生成课程学习地图并判断课时解锁状态"),
            (0, "进入课时，阅读Markdown、互动内容或播放视频"),
            (1, "恢复上次播放位置并定时保存进度与学习记录"),
            (0, "保存Markdown笔记、参与课时评论"),
            (1, "完成课时后更新课程进度并解锁后续节点"),
            (2, "在授权范围内查询学习进度与未完成课时"),
            (0, "继续下一课时或查看学习结果"),
        ],
    )

    diagrams[109] = output_dir / "figure-3-6-assessment.png"
    draw_lane_flow(
        diagrams[109],
        (900, 1640),
        ["教师", "系统", "学生"],
        [
            (0, "维护题库，支持表格导入、PDF抽取和知识库出题"),
            (0, "创建考试试卷或作业题卷并配置题目分值"),
            (1, "执行组卷预检并保存评测配置"),
            (0, "设置教学班、时间和状态后发布"),
            (2, "在我的考试或我的作业中开始作答"),
            (1, "校验时间与状态，建立答卷并持续保存答案"),
            (2, "主动提交或到时结束"),
            (1, "客观题自动判分，主观题进入待批改状态"),
            (0, "完成主观题批改并提交评测结果"),
            (1, "汇总成绩、统计结果并支持导出"),
        ],
    )

    diagrams[116] = output_dir / "figure-3-7-grades.png"
    draw_lane_flow(
        diagrams[116],
        (1000, 1320),
        ["系统", "教师/管理员", "学生"],
        [
            (0, "汇总考试、作业和独立成绩记录"),
            (1, "完成主观题批改或维护授权范围内的成绩"),
            (0, "计算总分、平均分、合格率和分数分布"),
            (1, "按考试查看答卷详情并导出成绩表"),
            (2, "在“我的成绩”查看本人考试与作业结果"),
            (0, "生成成绩趋势、分布和薄弱课程信号"),
            (2, "查看答题明细并获得后续学习建议"),
        ],
    )

    diagrams[143] = output_dir / "figure-3-9-ai.png"
    draw_parallel_flows(
        diagrams[143],
        (1600, 1185),
        [
            (
                "Agent助手",
                [
                    "打开全局浮动助手并创建会话",
                    "统一规划器判断普通对话、数据查询或写操作",
                    "读工具执行查询；写操作生成待确认动作",
                    "用户确认后再次校验权限并执行",
                    "保存消息与审计结果；失败时返回补充提示",
                ],
            ),
            (
                "知识库问答",
                [
                    "教师创建知识库并上传教学文档",
                    "平台等待文档解析并提供检索与图谱",
                    "学生选择知识库和会话后提问",
                    "WeKnora检索相关片段并通过SSE返回回答",
                    "前端展示引用来源、文件预览和讲解结果",
                ],
            ),
            (
                "论文与推荐",
                [
                    "检索本地、arXiv、OpenAlex和Semantic Scholar",
                    "查看详情，导入论文并维护收藏、已读和笔记",
                    "后端结合课程、进度和成绩计算规则评分",
                    "生成课程、课时、练习和论文推荐理由",
                    "前端按分数展示推荐结果，不重新排序",
                ],
            ),
            (
                "智能备课",
                [
                    "创建备课项目并上传资料或关联知识库",
                    "生成并人工审核课程大纲与课时结构",
                    "生成和编辑课时Markdown内容",
                    "生成、修改并审核章节题目",
                    "发布为正式课程、章节、课时和题库数据",
                ],
            ),
        ],
    )

    diagrams[151] = output_dir / "figure-3-10-work-study.png"
    draw_lane_flow(
        diagrams[151],
        (900, 1344),
        ["教师/管理员", "系统", "学生"],
        [
            (0, "新增或维护勤工俭学岗位和招聘状态"),
            (2, "浏览招聘中岗位并提交申请"),
            (1, "校验岗位状态、学生身份和重复申请"),
            (0, "通过接口审核申请并维护岗位学生记录"),
            (2, "在“我的岗位”查看申请及审核状态"),
            (0, "按月录入或导入工作时长和适用时薪"),
            (1, "计算劳务费并保存名单记录"),
            (0, "按岗位、月份和学生筛选并导出Excel或CSV"),
        ],
    )
    return diagrams


def apply_bilingual_fonts(run) -> None:
    run.font.name = "Times New Roman"
    r_pr = run._element.get_or_add_rPr()
    r_fonts = r_pr.rFonts
    if r_fonts is None:
        r_fonts = OxmlElement("w:rFonts")
        r_pr.insert(0, r_fonts)
    r_fonts.set(qn("w:ascii"), "Times New Roman")
    r_fonts.set(qn("w:hAnsi"), "Times New Roman")
    r_fonts.set(qn("w:eastAsia"), "宋体")


def first_text_run(paragraph):
    return next((run for run in paragraph.runs if run.text), paragraph.runs[0] if paragraph.runs else None)


def replace_paragraph_text(paragraph, text: str, run_template=None) -> None:
    template_run = run_template or first_text_run(paragraph)
    template_r_pr = deepcopy(template_run._r.rPr) if template_run is not None and template_run._r.rPr is not None else None
    for child in list(paragraph._p):
        if child.tag != qn("w:pPr"):
            paragraph._p.remove(child)
    run = paragraph.add_run(text)
    if template_r_pr is not None:
        if run._r.rPr is not None:
            run._r.remove(run._r.rPr)
        run._r.insert(0, template_r_pr)
    apply_bilingual_fonts(run)


def copy_paragraph_properties(target, source) -> None:
    if target._p.pPr is not None:
        target._p.remove(target._p.pPr)
    if source._p.pPr is not None:
        target._p.insert(0, deepcopy(source._p.pPr))
    target.style = source.style


def insert_paragraph_before(reference, text: str, template):
    paragraph = reference.insert_paragraph_before()
    copy_paragraph_properties(paragraph, template)
    replace_paragraph_text(paragraph, text, first_text_run(template))
    return paragraph


def resize_table(table, desired_rows: int) -> None:
    if desired_rows < 1:
        raise ValueError("A table must keep at least one row")
    while len(table.rows) > desired_rows:
        table._tbl.remove(table.rows[-1]._tr)
    prototype = deepcopy(table.rows[1]._tr if len(table.rows) > 1 else table.rows[0]._tr)
    while len(table.rows) < desired_rows:
        table._tbl.append(deepcopy(prototype))


def replace_cell_text(cell, text: str) -> None:
    paragraph = cell.paragraphs[0]
    template_run = first_text_run(paragraph)
    replace_paragraph_text(paragraph, text, template_run)
    for extra in list(cell.paragraphs[1:]):
        cell._tc.remove(extra._p)


def set_row_pagination(row, *, repeat_header: bool = False) -> None:
    tr_pr = row._tr.get_or_add_trPr()
    if not any(child.tag == qn("w:cantSplit") for child in tr_pr):
        tr_pr.append(OxmlElement("w:cantSplit"))
    for child in list(tr_pr):
        if child.tag == qn("w:tblHeader"):
            tr_pr.remove(child)
    if repeat_header:
        tr_pr.append(OxmlElement("w:tblHeader"))


def set_table_data(table, rows: list[list[str]]) -> None:
    resize_table(table, len(rows))
    for row_index, row_values in enumerate(rows):
        if len(row_values) != len(table.columns):
            raise ValueError(f"Table expects {len(table.columns)} columns, received {len(row_values)}")
        set_row_pagination(table.rows[row_index], repeat_header=row_index == 0)
        for column_index, value in enumerate(row_values):
            replace_cell_text(table.rows[row_index].cells[column_index], value)


def replace_image_in_paragraph(document, paragraph, image_path: Path, image_index: int = 0) -> None:
    blips = paragraph._p.xpath(".//a:blip")
    if len(blips) <= image_index:
        raise ValueError(f"No image {image_index} found in paragraph")
    relationship_id = blips[image_index].get(qn("r:embed"))
    image_part = document.part.rels[relationship_id].target_part
    image_part._blob = image_path.read_bytes()


def update_document(source: Path, output: Path, diagram_dir: Path) -> None:
    diagrams = generate_diagrams(diagram_dir)
    document = Document(source)
    paragraphs = document.paragraphs

    if len(paragraphs) < 168 or len(document.tables) < 8:
        raise ValueError("The source document does not match the expected report structure")
    if not paragraphs[66].text.strip().startswith("3") or "需求分析" not in paragraphs[66].text:
        raise ValueError("Chapter 3 was not found at the expected location")

    replacements = {
        66: "3  需求分析",
        67: "3.1  目标用户分析",
        68: "AI智学平台面向高校教学与学习场景设计。公共首页、登录和注册页面允许未登录用户访问；进入工作区后，系统按照管理员、教师和学生三种角色控制菜单、接口和数据范围。三类角色共享通知、个人设置、Agent助手、论文查询等基础能力，但在可见数据和可执行操作上存在明显区别。结合当前前后端路由、权限校验和业务模块，我们将角色职责与预期使用频度归纳如表3-1所示。",
        69: "表3-1  目标用户角色与职责",
        70: "系统采用统一登录入口。登录成功后，前端保存JWT令牌并在后续请求中携带，路由守卫按照角色显示工作入口；后端通过登录拦截器验证身份，课程、教学班、评测和成绩等核心接口再由PermissionService校验角色、课程负责人、教学班归属及学生入班关系。管理员拥有全局维护能力，教师只能维护本人课程和教学班范围内的数据，学生只能查看和提交与本人相关的学习记录、作业、考试、成绩和申请。",
        72: "图3-1  AI智学平台用例图",
        73: "3.2  功能需求",
        74: "3.2.1  用户与权限管理功能需求",
        75: "（1）功能描述",
        76: "这个模块负责公共入口、学生注册、教师入驻申请、用户登录、角色分配和资源权限校验，是进入系统业务功能的基础。学生可以直接注册，教师需要提交入驻申请并由管理员审核；登录成功后由后端签发JWT令牌。当前密码采用BCrypt保存，同时兼容旧MD5密码并在登录后自动升级。管理员可维护用户和角色，前端按照角色生成菜单，核心业务接口按照角色与资源归属限制数据访问，避免仅依赖页面隐藏造成越权。",
        77: "（2）业务流程",
        78: "未登录用户从公共首页进入注册或登录页面；学生注册时填写账号信息，系统创建学生账号并建立角色关系；选择教师身份时，系统保存教师申请，管理员在教师审核页面通过后再建立教师账号。用户登录时，后端校验账号状态和密码，签发JWT令牌并返回用户角色；前端保存令牌并进入对应工作区。之后每次请求都会携带令牌，后端先验证登录状态，课程、教学班、评测等核心接口再检查角色、负责人或学生本人范围，只有通过校验的请求才能继续执行。",
        80: "图3-2  用户与权限管理业务流程图",
        81: "3.2.2  课程资源与视频处理功能需求",
        82: "（1）功能描述",
        83: "课程资源模块按照“课程分类—课程—章节—课时”组织教学内容。教师和管理员可以维护课程及其章节、课时，课时支持Markdown课本、互动内容、文件、PPT、本地视频和公开视频URL等资源形式。普通文件通过统一上传接口保存，单次请求限制为100MB；公开视频URL会生成视频资源记录，由独立的yt-dlp服务异步下载并返回标题、时长、文件大小和播放地址，前端显示处理状态并允许对失败任务发起重试。",
        84: "（2）业务流程",
        85: "教师先维护课程分类并创建课程，再在课程详情页添加章节和课时；编辑课时时选择内容类型，填写Markdown或互动数据、上传本地文件，或者提交公开视频URL。后端保存课时与资源关系；遇到视频URL时，将资源置为待处理状态并异步调用内部视频服务，处理完成后更新为可播放状态，失败则记录原因。教师可在课程详情页轮询状态并重试；学生从班级、学习路线或学习地图进入课程，系统根据权限返回可访问的课时内容、文件和视频地址。",
        87: "图3-3  课程资源与视频处理业务流程图",
        89: "3.2.3  行政班与教学班管理功能需求",
        90: "（1）功能描述",
        91: "当前版本同时保留行政班和教学班两类组织。行政班由管理员维护，包含班级编号、名称、年级、专业、状态和关联课程，并通过成员表管理学生名单；教学班由教师或管理员围绕课程创建，记录任课教师、学期和班级说明，用于课程学习、通知、作业和考试的数据范围。一个学生可以属于多个教学班，教师只能维护本人课程对应的教学班和成员。",
        92: "（2）业务流程",
        93: "管理员在行政班管理页面创建班级，选择关联课程并维护学生成员；教师在本人课程下创建教学班，填写班级名称和学期，系统校验课程负责人和任课教师是否一致。教师在学生管理对话框中按用户名查找学生并手动添加或移除，系统在教学班学生关系表中保存记录并防止重复加入。学生登录后只在“我的班级”中查看已经加入的教学班，并由此进入课程学习、接收通知以及参加对应的作业和考试。",
        96: "图3-4  行政班与教学班管理业务流程图",
        97: "3.2.4  学习路线与学习过程跟踪功能需求",
        98: "（1）功能描述",
        99: "学习过程模块不仅记录课时进度，还提供学习路线、学习地图和互动课时体验。学生可以查看课程节点、解锁状态和总体完成度，进入课时后阅读Markdown课本、完成互动实验或播放视频。系统记录学习进度、学习时长和视频播放位置，并支持断点续学；课时侧栏还提供Markdown学习笔记、笔记下载和课时评论，使学习内容、过程记录与交流反馈保持关联。",
        100: "（2）业务流程",
        101: "学生从“我的班级”进入学习路线，系统根据课程章节、课时和已有进度生成学习地图；学生选择已解锁课时后，平台加载正文、互动内容、文件或视频，并恢复上次播放位置。学习过程中前端定时上报进度和时长，后端新增或更新学习记录；学生可以同步保存个人笔记、发表评论。课时完成后系统更新课程完成度并解锁后续节点，教师或具备权限的Agent读工具可以在相应数据范围内查询学习进度和未完成课时。",
        103: "图3-5  学习路线与学习过程业务流程图",
        104: "3.2.5  作业与考试一体化评测功能需求",
        105: "（1）功能描述",
        106: "当前版本将考试和作业逐步统一为“题库—题卷—评测活动—学生答卷—答案明细—评阅结果”的处理链路。题库支持单选、多选、判断、填空和简答等题型，并提供Excel/CSV导入导出、PDF题目抽取和知识库生成题目。教师可以分别创建考试试卷或作业题卷，执行组卷预检，设置教学班、起止时间和状态后发布；前端的试卷、评测列表、答题和批改页面通过评测类型复用同一套交互。",
        107: "（2）业务流程",
        108: "教师维护题库后创建题卷，可以从题库选题，也可以在组卷时录入新题，并设置题目分值和顺序；系统在保存前检查题目、总分和必答配置。教师基于题卷创建考试或作业，选择教学班并发布。学生在“我的考试”或“我的作业”中开始作答，系统校验时间、状态和班级身份，建立答卷并持续保存答案；提交后客观题自动判分，主观题进入待批改状态。教师完成批改并提交结果，系统汇总成绩、平均分和合格率，支持查看答题详情和导出成绩表。旧作业写入接口仅用于兼容读取，正式写入使用新的统一评测接口。",
        110: "图3-6  作业与考试评测业务流程图",
        111: "3.2.6  成绩与学习分析功能需求",
        112: "（1）功能描述",
        113: "成绩与学习分析模块汇总考试、作业和独立成绩记录。学生在“我的成绩”页面查看本人考试与作业结果、总分、答题详情、成绩趋势和分数分布；教师在考试结果页查看平均分、合格率、学生答卷和主观题得分，并导出成绩表。教师或管理员还可以在授权的课程和教学班范围内维护独立成绩记录，学生只能访问自己的数据。",
        114: "（2）业务流程",
        115: "学生提交答卷后，系统先处理客观题得分；教师完成主观题批改后，系统计算总分并生成评测结果。教师按考试或作业查看学生列表、平均分、合格率和答题明细，需要时导出成绩文件；学生进入个人成绩页面，系统合并本人可见的考试、作业和成绩记录，生成趋势图和分布图。成绩与课程、教学班和学生关联，薄弱课程和近期完成情况还会作为规则评分推荐的输入，但前端不重新计算推荐顺序。",
        117: "图3-7  成绩与学习分析业务流程图",
        119: "3.2.7  通知与操作日志管理功能需求",
        120: "（1）功能描述",
        121: "系统提供通知和操作日志两类辅助管理能力。教师或管理员可以创建、修改和删除通知，并按照系统、课程或教学班范围发布；学生登录后只能查看与本人或所在教学班相关的通知。操作日志记录请求用户、接口地址、请求方法、HTTP状态、响应时长和请求摘要，管理员与教师可以按时间、用户和操作类型分页查询，用于问题定位、Agent写操作审计和后续性能验收。",
        122: "（2）业务流程",
        123: "教师或管理员在通知页面填写标题、内容、类型和目标范围后发布，后端校验其课程或教学班权限并保存通知；用户进入通知中心时，系统依据角色和班级成员关系筛选可见内容。对于管理类请求和Agent确认执行的写操作，系统在处理完成后记录操作人、请求地址、状态和耗时；管理员或教师在操作日志页面按条件查询详情。日志只用于审计和排查，不替代具体业务表中的状态与结果。",
        125: "图3-8  通知与操作日志业务流程图",
        138: "3.2.8  AI学习与教学辅助功能需求",
        139: "（1）功能描述",
        140: "AI辅助能力已经扩展为Agent助手、知识库问答、智能备课、论文检索和个性化推荐。Agent以全局浮动助手嵌入登录后的工作区，保存会话和历史消息；统一规划器先判断请求属于普通对话、平台数据查询还是平台写操作。教师和管理员可以通过结构化读工具查询课程、班级、评测、成绩等数据，写操作必须先生成待确认动作，用户确认后再校验权限并执行；学生主要使用普通对话和知识问答，不能直接发起平台数据写操作。",
        141: "（2）业务流程",
        142: "用户打开浮动助手并输入问题后，后端根据意图进入普通对话、读工具或写操作规划；参数缺失或对象不唯一时返回候选和补充提示，写计划校验通过后保存为待确认动作，确认执行时再次检查权限并记录日志。教师可以创建知识库、上传教学文档、查看解析状态和知识图谱，学生在智能学伴中选择知识库和会话，通过WeKnora检索片段并接收SSE流式回答。WeKnora不可用时，知识库相关功能应明确提示，但课程、作业、考试及不依赖知识上下文的基础能力不受影响。",
        144: "图3-9  AI学习与教学辅助业务流程图",
        146: "3.2.9  勤工俭学管理功能需求",
        147: "（1）功能描述",
        148: "勤工俭学模块覆盖岗位维护、学生申请、申请状态查询、岗位学生记录和劳务费名单管理。教师或管理员可以新增、修改和删除岗位，维护招聘状态，并通过接口审核学生申请；学生只能查看招聘中的岗位、提交本人申请并在“我的岗位”中查看进度。劳务费名单支持手动维护、Excel/CSV导入导出，并按岗位、学生、月份和关键字分页筛选。",
        149: "（2）业务流程",
        150: "教师或管理员发布勤工俭学岗位，设置岗位名称、地点、工作内容、时薪、人数和招聘状态；学生浏览招聘中的岗位并提交申请，系统校验学生身份、岗位状态和重复申请。审核通过后，教师或管理员维护岗位学生记录，按月录入或批量导入工作时长和适用时薪，系统计算劳务费并保存名单；需要报表时，可按岗位、月份和学生筛选并导出Excel或CSV。学生在个人页面查看申请及审核状态，不具备修改他人记录的权限。",
        152: "图3-10  勤工俭学业务流程图",
        154: "表3-2  主要功能模块清单",
        155: "3.3  性能需求",
        156: "结合高校日常教学、集中评测和智能辅助场景，系统性能需求需要同时覆盖普通业务、文件处理和外部智能服务。当前项目已经具备分页查询、数据库索引、异步任务、超时控制和请求耗时日志，但尚未形成正式压力测试报告，因此表3-3中的并发数、P95响应时间和成功率均作为验收目标，不写成已经实测达到的结论。",
        157: "表3-3  系统性能需求",
        158: "3.3.1  软件环境",
        159: "表3-4  软件环境",
        160: "",
        161: "3.3.2  硬件环境",
        162: "表3-5  硬件环境",
        163: "3.3.3  开发环境",
        164: "表3-6  开发环境",
        165: "项目使用Git进行版本管理和团队协作，后端采用Maven构建，前端采用npm与Vite构建，Python视频辅助服务使用独立虚拟环境。后端测试以JUnit 5、MockMvc和H2兼容数据库为主，前端使用Playwright进行端到端检查；这些功能测试不能替代并发与P95响应时间测试。",
        166: "开发和演示环境可以将前端、Spring Boot和MySQL部署在同一台计算机上；正式环境建议将数据库、WeKnora知识库和yt-dlp视频服务按资源需求独立部署，并为上传文件和视频准备足够的SSD空间。Spring Boot默认端口为8080，Vite开发端口为24678，MySQL端口为3306；WeKnora与视频服务需要使用互不冲突的独立端口。核心业务数据应定期备份，并验证恢复流程。",
        167: "在数据一致性和安全性方面，关键写操作使用数据库事务，登录接口签发JWT并由前后端共同校验角色与资源范围；普通文件上传限制为100MB，视频下载采用异步任务并记录状态。Agent普通请求、智能备课和外部论文检索均设置超时，知识库或视频服务异常时返回明确状态，不能阻塞课程、班级、作业、考试和成绩等基础业务。生产部署时还应关闭详细SQL输出、限制分页上限，并针对普通接口、Agent和SSE分别进行压力测试。",
    }

    for index, text in replacements.items():
        replace_paragraph_text(paragraphs[index], text)

    insert_paragraph_before(
        paragraphs[71],
        "未登录用户只作为访问状态，不单独进入业务角色表。学生注册后直接获得学生角色；教师需要提交入驻申请，管理员审核通过后才能以教师身份登录。管理员除维护用户、课程分类和行政班外，还负责教师申请审核与全局数据处理；教师承担课程建设、教学班、评测、知识库和智能备课工作；学生围绕学习路线、课时、作业考试、成绩、推荐和勤工俭学开展日常操作。",
        paragraphs[70],
    )
    insert_paragraph_before(
        paragraphs[74],
        "当前版本以公共认证和角色权限为基础，业务范围已经扩展到课程资源、班级组织、互动学习、统一评测、成绩分析、通知日志、AI辅助、论文推荐和勤工俭学等方面。各模块保留独立页面和接口，同时通过用户、课程、教学班和学习数据形成关联，具体需求如下。",
        paragraphs[76],
    )
    insert_paragraph_before(
        paragraphs[141],
        "论文服务支持本地数据库、arXiv、OpenAlex和Semantic Scholar检索，以及详情、导入、收藏、已读和Markdown笔记。个性化推荐由后端依据课程分类、学习进度、成绩薄弱项和论文数据计算课程、课时、练习与论文分数，前端只展示分数、理由和影响因素。智能备课则允许教师或管理员上传参考资料或关联知识库，按“大纲—课时—题目”阶段生成和人工修改内容，最后发布为正式课程、章节、课时和题库数据。上述外部能力依赖模型、网络或WeKnora服务状态，失败时应保留草稿并给出可恢复提示。",
        paragraphs[140],
    )
    insert_paragraph_before(
        paragraphs[143],
        "Agent原生会话接口当前以同步结果返回，知识库问答使用SSE流式响应，两者不能混写为同一种流式链路。推荐仍属于基于业务数据的规则评分，不使用WeKnora向量召回；论文外部检索和视频下载也会受到网络与第三方服务影响，因此系统需要在页面上展示加载、超时、失败和重试状态。",
        paragraphs[142],
    )
    insert_paragraph_before(
        paragraphs[158],
        "性能验收时，应在不少于50个并发请求的基线环境中记录P95响应时间和成功率，并逐步验证100名同时在线用户的课程学习与集中评测场景。视频下载属于异步任务，其处理时长不计入同步接口响应时间；知识库SSE、Agent和智能备课分别按照各自超时配置验收。",
        paragraphs[156],
    )

    role_table = [
        ["角色", "主要职责", "技术要求", "预期使用频度"],
        [
            "学生",
            "查看班级和学习路线，学习课时与互动内容，完成作业考试，查看成绩，使用智能学伴、Agent、论文推荐和勤工俭学功能",
            "掌握浏览器、文件上传、在线答题和基础Markdown编辑操作",
            "教学期间高频使用，作业截止、考试和复习阶段较集中",
        ],
        [
            "教师",
            "维护课程、章节课时、教学班、题库题卷、作业考试与通知，批改成绩，管理知识库，使用智能备课和Agent助手",
            "能够使用课程编辑、题库组卷、Excel/CSV导入导出、Markdown和知识库管理功能",
            "教学周期内持续使用，备课、发布评测和集中批改时频率较高",
        ],
        [
            "管理员",
            "维护用户角色、教师申请、课程分类、行政班、全局业务数据、操作日志、Agent定义和系统运行配置",
            "熟悉角色权限、数据维护、日志排查、文件服务和基础部署配置",
            "日常巡检与维护，开学、期末和数据调整阶段集中使用",
        ],
    ]
    set_table_data(document.tables[2], role_table)

    module_table = [
        ["模块名称", "核心功能点", "涉及角色"],
        ["用户与权限管理", "公共入口、学生注册、教师申请、JWT登录、用户角色和资源权限", "管理员、教师、学生"],
        ["课程资源与视频处理", "分类、课程、章节、课时、互动内容、文件上传、视频异步处理", "管理员、教师、学生"],
        ["行政班与教学班管理", "行政班、教学班、课程关联、教师归属和学生成员", "管理员、教师、学生"],
        ["学习路线与学习过程", "路线、地图、课时解锁、进度、视频断点、笔记和评论", "教师、学生"],
        ["作业与考试评测", "题库、题卷、导入预览、发布、作答、自动判分和人工批改", "管理员、教师、学生"],
        ["成绩与学习分析", "个人成绩、考试结果、趋势、分布、答题详情和导出", "管理员、教师、学生"],
        ["通知与操作日志", "通知发布与范围过滤、请求状态、耗时记录和操作审计", "管理员、教师、学生"],
        ["Agent与知识库", "浮动助手、会话、读工具、待确认写操作、文档检索和流式问答", "管理员、教师、学生"],
        ["智能备课", "资料上传、大纲、课时和题目生成、人工审核及课程发布", "管理员、教师"],
        ["论文与智能推荐", "多源检索、导入收藏笔记、规则评分及推荐理由", "管理员、教师、学生"],
        ["勤工俭学", "岗位、学生申请、审核状态、劳务名单和Excel/CSV导入导出", "管理员、教师、学生"],
    ]
    set_table_data(document.tables[3], module_table)

    performance_table = [
        ["性能指标", "目标值或配置", "说明"],
        ["普通查询P95响应时间", "目标不超过2秒", "课程、班级、题库、成绩等分页查询；需要通过压测验收"],
        ["普通事务P95响应时间", "目标不超过3秒", "新增、修改、保存答案和提交等不含外部模型的同步操作"],
        ["复杂查询P95响应时间", "目标不超过5秒", "成绩汇总、学习路线和本地统计；外部论文接口单独提示等待状态"],
        ["并发与成功率", "基线50并发，目标100人在线；成功率不低于99%", "当前为验收目标，尚无正式P95与并发报告"],
        ["Agent请求", "模型15秒、完整请求25秒", "意图选择5秒、参数规划10秒；超时后返回明确提示"],
        ["智能备课", "单次模型调用90秒，最大并发2", "任务队列容量100，生成过程异步记录进度与失败原因"],
        ["文件与视频", "普通上传100MB；视频异步处理", "课程资料一次最多10个文件；视频下载耗时不计入同步接口"],
        ["知识库问答", "SSE连接超时300秒", "WeKnora不可用时提示失败，基础教学业务继续可用"],
        ["数据保存与可用性", "核心数据不少于5年，总体可用率目标99%", "配合定期备份、恢复验证和请求耗时日志"],
        ["浏览器兼容性", "Chrome、Edge、Firefox、Safari", "支持主流现代浏览器，移动端保持基本可访问性"],
    ]
    set_table_data(document.tables[4], performance_table)

    software_table = [
        ["项目", "选型", "说明"],
        ["操作系统", "Windows 10/11或Linux", "开发环境以Windows为主，正式部署可使用主流Linux发行版"],
        ["后端主服务", "Java 21、Spring Boot 3.5.16、内嵌Tomcat", "默认监听8080，提供REST接口、定时任务和静态资源访问"],
        ["前端应用", "Vue 3.5.38、Vite 8.1.0、Element Plus 2.14.2", "开发端口24678，生产环境构建为静态文件"],
        ["数据库", "MySQL 8.0、InnoDB、utf8mb4", "当前正式结构包含45张表和外键约束，不依赖启动时自动建表"],
        ["AI模型接口", "Spring AI、OpenAI兼容接口、DeepSeek", "密钥和模型地址通过环境变量配置"],
        ["知识库服务", "WeKnora（可选外部服务）", "后端默认代理8090，需独立部署；主应用不使用Redis业务缓存"],
        ["视频辅助服务", "Python、FastAPI、yt-dlp、FFmpeg", "默认监听127.0.0.1:8091，负责视频下载和元数据返回"],
        ["浏览器", "Chrome、Edge、Firefox、Safari", "用于访问公共首页、角色工作区和互动学习页面"],
    ]
    set_table_data(document.tables[5], software_table)

    hardware_table = [
        ["项目", "推荐配置", "说明"],
        ["开发与演示主机", "四核及以上CPU、16GB及以上内存、SSD", "可同时运行前端、Spring Boot、MySQL和必要的辅助服务"],
        ["应用服务器", "四核及以上CPU、8GB及以上内存", "承载Spring Boot接口、文件访问、定时任务和异步队列"],
        ["数据库服务器", "四核及以上CPU、16GB及以上内存、SSD", "正式环境建议独立部署，并配置备份与恢复方案"],
        ["知识库与视频节点", "四核及以上CPU、8GB以上内存、充足磁盘", "按文档数量和视频体量扩容，并避免WeKnora与视频服务端口冲突"],
        ["客户端", "普通PC或移动设备、现代浏览器", "无需安装专用客户端，稳定网络可提升视频和SSE体验"],
    ]
    set_table_data(document.tables[6], hardware_table)

    development_table = [
        ["项目", "选型", "说明"],
        ["后端开发语言", "Java 21", "作为Maven编译目标，开发运行时应保持兼容"],
        ["后端开发工具", "IntelliJ IDEA、Maven", "用于依赖管理、编码、调试、测试和打包"],
        ["前端开发语言", "JavaScript、Vue 3", "采用Composition API进行组件化开发"],
        ["前端开发工具", "VS Code、npm、Vite", "提供依赖管理、热更新和生产构建"],
        ["视频服务开发", "Python 3、FastAPI", "在独立虚拟环境中安装yt-dlp和相关依赖"],
        ["数据库工具", "MySQL 8.0客户端或图形化管理工具", "执行正式结构脚本、检查数据和外键关系"],
        ["测试工具", "JUnit 5、MockMvc、H2、Playwright", "验证业务与页面流程；性能仍需JMeter或k6单独验收"],
        ["版本控制", "Git", "用于分支管理、变更审查和团队协作"],
    ]
    set_table_data(document.tables[7], development_table)

    for paragraph_index, image_path in diagrams.items():
        replace_image_in_paragraph(document, paragraphs[paragraph_index], image_path)

    output.parent.mkdir(parents=True, exist_ok=True)
    document.save(output)

    check = Document(output)
    chapter_text = "\n".join(p.text for p in check.paragraphs[66:180])
    required_phrases = [
        "行政班与教学班管理功能需求",
        "作业与考试一体化评测功能需求",
        "AI学习与教学辅助功能需求",
        "普通查询P95响应时间",
        "Vite 8.1.0",
    ]
    missing = [phrase for phrase in required_phrases if phrase not in chapter_text and not any(phrase in c.text for t in check.tables for r in t.rows for c in r.cells)]
    if missing:
        raise ValueError(f"Structural verification failed, missing: {missing}")
    expected_rows = {2: 4, 3: 12, 4: 11, 5: 9, 6: 6, 7: 9}
    for table_index, row_count in expected_rows.items():
        if len(check.tables[table_index].rows) != row_count:
            raise ValueError(f"Table {table_index} has {len(check.tables[table_index].rows)} rows, expected {row_count}")

    print(f"Saved: {output}")
    print(f"Paragraphs: {len(check.paragraphs)}; Tables: {len(check.tables)}; Inline shapes: {len(check.inline_shapes)}")
    print("Updated table rows:", {index: len(check.tables[index].rows) for index in expected_rows})


def main() -> None:
    parser = argparse.ArgumentParser(description="Refresh chapter 3 in the AI learning project report.")
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--diagram-dir", type=Path, required=True)
    args = parser.parse_args()
    update_document(args.source.resolve(), args.output.resolve(), args.diagram_dir.resolve())


if __name__ == "__main__":
    main()
