from copy import deepcopy
from pathlib import Path
import sys

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn


def set_text_keep_format(paragraph, text):
    if paragraph.runs:
        first = paragraph.runs[0]
        first.text = text
        for run in paragraph.runs[1:]:
            run.text = ""
    else:
        paragraph.add_run(text)


def shade(cell, fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def insert_table_after(document, paragraph, rows, widths=None):
    table = document.add_table(rows=1, cols=len(rows[0]))
    table.style = "Table Grid"
    for j, value in enumerate(rows[0]):
        table.cell(0, j).text = value
        shade(table.cell(0, j), "D9EAF7")
        for run in table.cell(0, j).paragraphs[0].runs:
            run.bold = True
    for values in rows[1:]:
        cells = table.add_row().cells
        for j, value in enumerate(values):
            cells[j].text = value
    paragraph._p.addnext(table._tbl)
    return table


base = Path(sys.argv[1])
source = next(p for p in base.glob("*.docx") if not p.name.startswith("~$") and p.stat().st_size == 1334187)
target = base / (source.stem + "_第567章更新.docx")
doc = Document(source)

replacements = {
    "用户与权限模块负责注册、登录、个人资料维护、用户管理和角色授权，是平台其他业务的入口。系统以t_user保存账号信息，以t_role和t_user_role维护角色关系，前端根据admin、teacher、student三类角色生成菜单，后端再由鉴权拦截器校验JWT令牌，避免只靠前端隐藏菜单造成越权。":
    "用户与权限模块负责注册、登录、个人资料维护、教师申请审核、用户管理和角色授权，是平台其他业务的统一入口。系统以t_user保存账号与状态，以t_role、t_user_role维护admin、teacher、student三类角色关系。前端依据角色动态生成菜单并通过Vue Router守卫控制页面入口，后端鉴权拦截器再次解析JWT并校验接口权限，形成“页面可见性控制+服务端权限校验”的双重保护。",
    "管理员可以查询、创建、停用和修改用户，教师申请由独立审核流程处理。普通用户可在个人设置中更新昵称、邮箱、手机号和头像。密码、令牌密钥等敏感数据不在前端保存明文，接口异常统一返回业务码和可读提示。":
    "管理员可以分页查询、新增、修改、启停用户并维护角色，教师身份通过TeacherApplications.vue对应的独立申请审核流程取得。普通用户可在ProfileSettings.vue中更新昵称、邮箱、手机号和头像。注册、登录和资料修改均进行必填项、格式、唯一性和账号状态校验；密码及令牌密钥不以明文返回前端，接口异常统一封装为业务码、提示信息和必要的数据字段。",
    "用户在登录页提交用户名和密码后，后端先校验账号状态与密码，再生成JWT令牌并返回用户角色。前端把令牌加入后续请求头，路由守卫根据登录状态和角色决定能否进入目标页面。管理员维护用户或角色时，后端仍会做关联关系校验，存在课程、班级等引用的数据不会被直接删除。":
    "用户通过POST /auth/login提交用户名和密码，后端依次完成参数校验、账号查询、状态与密码核验，成功后签发JWT并返回用户及角色信息。Axios请求拦截器把令牌加入后续请求头，路由守卫根据登录状态和角色决定能否进入目标页面；令牌失效时清理本地状态并返回登录页。管理员维护用户和角色时，服务层同步检查角色关系及课程、教学班等业务引用，避免产生孤立数据。",
    "课程业务采用“分类-课程-章节-课时”四级结构。教师维护课程基本信息、封面和章节顺序，课时可以保存视频链接、文章、文件或视频文件。教学班把课程、教师和学生组织到同一教学周期中，学生与教学班的多对多关系由t_class_student记录。":
    "课程业务采用“课程分类—课程—章节—课时”四级结构。教师维护课程基本信息、封面、发布状态和章节顺序，课时支持视频链接、文章、附件及本地视频等资源形式。教学班把课程、任课教师和学生组织到同一教学周期中，t_class_student维护学生与教学班的多对多关系；学习笔记、课时评论和视频资源作为辅助数据与课时关联。",
    "学习进度以教学班、学生和课时为定位条件，保存进度百分比、已学习时长、上次播放位置和最近学习时间。课程资源本身与学习记录分开存放，教师调整课时内容时不会覆盖学生已有的进度数据。":
    "学习进度以教学班、学生和课时作为联合定位条件，保存进度百分比、累计学习时长、上次播放位置和最近学习时间。资源数据与学习记录分离，教师调整课时内容不会覆盖学生历史进度；前端定时上报时采用新增或更新策略，后端同时校验学生是否属于目标教学班，防止跨班写入。",
    "考核模块把作业、题库、试卷、考试安排、答卷和成绩分开建模。作业支持文字内容、附件、满分、起止时间与提交记录；考试支持单选、多选、判断、填空和简答题，试卷通过中间表保存题目顺序与分值，考试安排再关联教学班和时间窗口。":
    "考核模块将作业、题库、试卷、考试安排、考试记录、答题明细和成绩分开建模。作业支持文字内容、附件、满分、起止时间及学生提交记录；题库覆盖单选、多选、判断、填空和简答题。t_exam_paper_question保存试卷题目顺序与分值，考试安排关联教学班、试卷、开放时间和结束时间，从而支持同一试卷在不同班级和时间段复用。",
    "客观题交卷后由系统自动判分，简答题进入人工批改状态。作业和考试的最终得分统一写入成绩记录，教师也可以使用Excel批量导入成绩，导入总数、成功数、失败数和错误原因会记录在日志中。":
    "学生开始考试后生成考试记录，答题过程中分次保存答案；交卷时系统校验场次状态、截止时间和重复提交，客观题自动判分，简答题进入待人工批改状态。教师完成主观题评分后汇总总分，作业和考试结果统一写入t_grade_record。Excel批量导入成绩时记录文件名、导入总数、成功数、失败数和逐行错误原因，便于复核和追溯。",
    "Agent模块包含Agent定义、会话、消息和待确认动作。Agent定义保存名称、系统提示词、模型和温度等配置；会话与消息表保留上下文，普通对话会把用户消息和模型回复写入数据库。智能资源生成用于辅助教师整理教学大纲、讲义、练习题和项目案例。":
    "Agent模块由Agent定义、会话、消息和待确认动作构成。Agent定义保存名称、系统提示词、模型、温度、启停状态及知识文档；会话与消息表按用户隔离上下文，支持普通响应和SSE流式响应。智能资源生成面向教师提供教学大纲、讲义、练习题和项目案例四类输出，生成结果作为可编辑草稿，不直接替代教师审核。",
    "涉及课程、教学班、通知等写操作时，系统先生成待确认动作，不直接执行。用户确认后，后端再次校验当前身份、参数和目标数据，再调用对应执行器完成操作，并记录来源IP和执行结果。这一设计把自然语言建议与实际业务写入隔开。":
    "当模型规划课程、教学班、作业、考试、成绩或通知等写操作时，系统只生成包含动作类型、目标对象和关键参数的待确认记录，不直接修改业务数据。用户通过/api/agent/actions/confirm确认后，后端重新校验当前身份、参数完整性和目标数据状态，再调用白名单执行器完成操作并记录来源IP及结果，使自然语言建议与实际业务写入保持明确边界。",
    "论文模块同时支持本地库、arXiv、OpenAlex和Semantic Scholar。用户可以按关键词、作者、年份、来源和引用数筛选；本地搜索优先使用全文索引，无结果时回退到模糊查询。论文标记、课程研究方向和个性化推荐在本地保存，外部论文导入时按arXiv编号去重。":
    "论文模块统一接入本地库、arXiv、OpenAlex和Semantic Scholar四类来源。用户可按关键词、作者、年份、来源、引用数和相关度筛选排序；后端依据source参数选择检索服务，并将标题、作者、摘要、年份、链接等字段转换为统一结果结构。本地检索优先使用全文索引，无结果时回退到模糊查询；外部论文导入按arXiv编号等来源标识去重，标记、课程研究方向和推荐结果保存在本地。",
    "知识库模块通过WeKnora接口管理知识库、文档、会话和知识图谱。教师可上传课件并维护知识库，学生可以检索文档片段或发起知识问答。流式问答采用SSE返回片段，长连接与普通业务接口分开处理。":
    "知识库模块通过WeKnora接口管理知识库、文档、会话和知识图谱。教师可创建知识库、上传课件并查看文档处理状态，学生可检索相关片段或发起基于指定知识库的问答。系统先创建会话，再以SSE转发上游回答片段，同时提供停止会话、删除会话和错误结束处理；知识库正文由WeKnora保存，本系统仅维护调用所需的编号和会话关系。",
    "通知模块按系统、课程、作业、考试和成绩划分类型，教师或管理员可以面向班级或用户发布。操作日志由拦截器记录用户、请求方法、地址、参数、IP和时间，用于故障定位和审计。勤工俭学模块维护岗位、学生申请和劳务费记录，并支持Excel导入导出。":
    "通知模块按系统、课程、作业、考试和成绩划分类型，教师或管理员可面向教学班或指定用户发布。操作日志由统一拦截器记录操作人、请求方法、地址、参数、来源IP、执行状态和时间，用于故障定位与审计。勤工俭学模块维护岗位、学生申请、工作时长、时薪和劳务费记录，并提供Excel批量导入与导出。",
    "数据库按业务边界划分为用户权限、教学组织、学习考核、AI与学术服务、系统运营五组实体。用户权限组以用户、角色和用户角色关系为核心；教学组织组包含课程分类、课程、章节、课时、教学班和班级学生；学习考核组包含进度、作业、题库、试卷、考试、答题和成绩；AI与学术组包含Agent、会话、消息、论文和作者；运营组包含通知、日志和勤工俭学记录。":
    "数据库按照业务边界划分为用户权限、教学组织、学习考核、AI与学术服务、系统运营五个实体域。用户权限域以用户、角色和用户角色关系为核心；教学组织域覆盖课程分类、课程、章节、课时、教学班和班级学生；学习考核域覆盖学习进度、作业提交、题库、试卷、考试场次、考试记录、答题明细和成绩；AI与学术域保存Agent定义、会话、消息、待确认动作、论文和作者；系统运营域保存通知、操作日志与勤工俭学数据。五个实体域通过用户、课程和教学班三个关键编号衔接。",
    "逻辑结构采用MySQL 8.0和utf8mb4字符集。表名统一使用t_前缀，主键一般为id，业务表保留create_time和update_time。查询频繁的外键、状态、时间和排序字段建立普通索引，用户角色、班级学生、试卷题目等关系使用联合唯一约束避免重复数据。":
    "逻辑结构采用MySQL 8.0与utf8mb4字符集，表名统一使用t_前缀，主键一般为自增id，核心业务表保留create_time和update_time。查询频繁的外键、状态、时间、来源及排序字段建立普通索引；用户名等自然业务键建立唯一索引；t_user_role、t_class_student、t_exam_paper_question和学习进度定位字段使用联合唯一约束。删除和修改操作由服务层检查引用关系，考试提交、成绩生成和批量导入等多表写入使用事务保证一致性。",
    "项目已经形成可运行的前后端分离系统。按附录A的课程报告统计口径，系统包含17个模块、193个功能点、27张核心数据表和27个核心业务页面；源码中现有43个Vue视图文件和45个Spring Controller，辅助页面与兼容接口未重复计入核心页面数量。":
    "项目已经形成可运行的前后端分离系统。按附录A的统计口径，当前版本包含17个模块、193个功能点、27张核心数据表和27个核心业务页面；工程中共有43个Vue视图文件和45个Spring Controller，辅助页面、通用组件及兼容接口未重复计入核心页面数量。系统已覆盖“账号进入—课程组织—在线学习—作业考试—成绩反馈”的教学主链路，并扩展AI Agent、智能资源生成、论文检索、知识库、通知审计和勤工俭学业务。",
    "后端工程按controller、service、mapper、pojo、dto、config和agent等目录组织，数据库脚本集中在database目录；前端按views、components、api、router、store、composables和utils组织。课程、教学班、作业、考试、成绩、Agent、论文、知识库和勤工俭学均有对应的接口封装与页面入口。":
    "后端工程以启动类为入口，按controller、service、mapper、pojo、dto、config、interceptor和agent等目录分层，database目录集中保存建表及初始化脚本；前端以src为主目录，按views、components、api、router、store、composables、utils和assets组织。views负责角色页面，api统一封装Axios请求，router和store共同处理菜单、登录状态及权限，公共上传、异常提示和流式会话逻辑由组件或组合式函数复用。",
    "当前测试源码包含53个测试类和347处@Test标记，覆盖考试流程、文件上传、导入、权限、Agent规划与动作执行等场景。接口文档记录的最近一次Apifox全量测试包含4个场景、176个自定义HTTP步骤，正常、空值、非法输入和完整性约束场景均通过。":
    "当前测试源码包含53个测试类和347处@Test标记，覆盖登录鉴权、考试状态流转、文件上传、Excel导入、数据完整性、Agent规划及动作确认等场景。接口文档记录的最近一次Apifox全量回归包含4个场景、176个自定义HTTP步骤，分别验证正常流程、空值、非法输入和完整性约束。上述数字反映当前工程与测试记录，性能目标仍需在接近真实教学班规模的数据和并发条件下单独验证。",
    "本地运行时，后端以Spring Boot启动在8080端口，前端由Vite提供开发服务，MySQL保存业务数据。DeepSeek、WeKnora、arXiv和OpenAlex属于外部依赖，需要在环境变量或配置文件中提供地址和密钥。":
    "本地运行时，Java 21环境下的Spring Boot后端启动在8080端口，Vue 3前端由Vite提供开发服务，MySQL 8.0保存业务数据，上传文件写入本地资源目录。DeepSeek、WeKnora、arXiv、OpenAlex和Semantic Scholar属于外部依赖，需要在环境变量或配置文件中提供服务地址、密钥和超时参数；外部服务不可用时，课程、作业、考试和成绩等本地核心业务仍可独立运行。",
    "以下按实际页面入口说明主要功能。截图暂留位置，补图时应使用真实页面和白底后端日志，并保证界面操作、请求地址和SQL记录相互对应。":
    "以下按当前版本的实际页面入口说明主要功能，并列出页面操作、核心接口和数据表之间的对应关系。文中的图片位置需在提交前替换为真实运行截图：每组至少包含操作前后页面、浏览器请求结果或白底后端日志，并保证页面动作、请求方法、接口地址和SQL记录属于同一次操作。",
    "访问/login或/register完成登录和注册。登录成功后进入/console，管理员可从/users维护账号和状态，普通用户从/profile修改个人信息。角色由路由守卫控制页面入口，后端接口仍会校验JWT。":
    "访问/login或/register完成登录和注册，成功后进入/console。管理员从/users分页维护账号、状态和角色，普通用户从/profile修改个人信息，教师申请由独立审核页面处理。前端路由守卫根据JWT和角色控制菜单及页面入口，后端对每次受保护请求重新校验令牌，验证结果以接口响应为准。",
    "教师从/courses创建课程，在课程详情中维护章节课时，再从/teaching-classes建立教学班；学生从/my-classes进入/course-learn/:courseId学习，页面定时上报进度并支持断点续学。":
    "教师从/courses创建课程并维护封面与状态，在课程详情中按顺序配置章节、课时和学习资源，再从/teaching-classes建立教学班并加入学生；学生从/my-classes进入/course-learn/:courseId学习。页面定时上报播放位置、学习时长和进度百分比，再次进入相同课时时读取上次位置，实现断点续学。",
    "教师在/homeworks发布作业，在/questions和/papers维护题库与试卷，在/exams发布考试并从批改页处理主观题；学生通过/my-homeworks、/my-exams参加考核，完成后在/my-grades查看成绩。":
    "教师在/homeworks发布作业，在/questions和/papers维护题库、试卷题目顺序与分值，在/exams设置考试时间并发布场次；学生通过/my-homeworks提交作业，通过/my-exams完成开始考试、保存答案和交卷。系统自动评判客观题，教师在批改页处理主观题，最终成绩统一在/my-grades展示。",
    "用户从/agent-chat创建会话并发送问题，教师从/resource-gen生成大纲、讲义、练习题或项目案例。会话消息保存在数据库；涉及业务写入的自然语言动作先显示待确认信息，确认后才执行。":
    "用户从/agent-chat选择Agent、创建会话并发送问题，可查看历史消息或使用流式输出；教师从/resource-gen生成大纲、讲义、练习题和项目案例。会话消息持久化到数据库，生成内容先作为草稿展示；涉及课程、考试或通知等写入的自然语言动作必须显示目标与参数摘要，用户确认后才由后端白名单执行器处理。",
    "学生从/paper-search选择本地库、arXiv、OpenAlex或Semantic Scholar检索论文，也可保存标记；从/knowledge-base上传资料并发起知识问答，/knowledge-graph用于查看知识图谱。":
    "用户从/paper-search选择本地库、arXiv、OpenAlex或Semantic Scholar检索论文，可按条件筛选、查看外部链接、导入本地库或保存标记；教师在知识库页面创建知识库并上传资料，学生从/knowledge-base选择知识库发起检索和流式问答，/knowledge-graph用于查看知识关联。界面明确标识本地数据、外部来源和文档处理状态。",
    "管理员从/work-studies维护岗位，从/work-study-students导入工时和劳务费记录；学生从/work-study-jobs查看岗位，并在/my-work-study查看自己的申请和记录。":
    "管理员从/work-studies维护岗位、时薪和有效状态，从/work-study-students批量导入学生工时并导出劳务费记录；学生从/work-study-jobs查看岗位与申请条件，在/my-work-study查看个人申请、工作时长和金额。后端按工作时长与时薪计算金额，导入失败时返回对应学号、行号或格式错误原因。",
}

for paragraph in doc.paragraphs:
    if paragraph.text in replacements:
        set_text_keep_format(paragraph, replacements[paragraph.text])

# Remove obsolete “待补” wording while retaining caption locations for real screenshots.
for paragraph in doc.paragraphs:
    if paragraph.text.startswith("图5-") or paragraph.text.startswith("图6-") or paragraph.text.startswith("图7-"):
        set_text_keep_format(paragraph, paragraph.text.replace("（待补）", ""))

# Add a current-version core table inventory after the existing user-table caption/table.
caption = next(p for p in doc.paragraphs if p.text == "表6-1  用户表（t_user）")
detail = doc.add_paragraph("表6-2  当前版本核心数据表及关系说明")
detail.style = caption.style
caption._p.addnext(detail._p)

rows = [
    ["业务域", "核心数据表", "主要关联与约束"],
    ["用户权限", "t_user、t_role、t_user_role", "用户—角色多对多；用户名唯一；关系表联合唯一"],
    ["课程资源", "t_course_category、t_course、t_chapter、t_lesson", "分类—课程—章节—课时逐级一对多，章节与课时保存排序号"],
    ["教学班与进度", "t_teaching_class、t_class_student、t_learning_progress", "课程—教学班一对多；学生—班级多对多；班级+学生+课时定位进度"],
    ["作业", "t_homework、t_homework_submit", "作业属于教学班；提交记录关联作业与学生并保存批改结果"],
    ["题库与试卷", "t_question、t_exam_paper、t_exam_paper_question", "试卷—题目多对多；中间表保存题目顺序与分值"],
    ["考试与答题", "t_exam_session、t_exam_record、t_exam_answer", "场次关联班级和试卷；考试记录关联学生；答案关联考试记录和题目"],
    ["成绩", "t_grade_record、t_grade_import_log", "成绩关联学生、课程、教学班和来源；导入日志保存成功数与错误原因"],
    ["AI Agent", "t_agent、t_agent_session、t_agent_message、t_agent_pending_action", "Agent—会话—消息逐级关联；待确认动作关联用户、会话与执行状态"],
    ["论文", "t_paper、t_paper_author", "论文与作者通过关联数据维护多作者关系；来源标识用于导入去重"],
    ["系统运营", "t_notice、t_operation_log", "通知按用户或班级发布；日志记录操作人、接口、IP和执行时间"],
    ["勤工俭学", "t_work_study、t_work_study_student", "岗位—学生记录一对多；学生记录保存工时、时薪和金额"],
]
insert_table_after(doc, detail, rows)

# Strengthen the completion statement without claiming unperformed performance tests.
anchor = next(p for p in doc.paragraphs if p.text == "图7-3MySQL核心数据表截图" or p.text == "图7-3 MySQL核心数据表截图")
extra = doc.add_paragraph("当前版本的验收重点为三类闭环：一是页面操作、REST接口和数据库写入能够对应；二是学生、教师、管理员在前后端均受到角色约束；三是DeepSeek、WeKnora及学术数据源异常时给出明确提示且不影响本地教学主流程。功能与边界场景已有自动化测试和Apifox回归记录，100人并发、长期可用性等指标仍作为部署阶段的压力测试目标，不在本章中表述为已完成结果。")
extra.style = doc.paragraphs[284].style
anchor._p.addnext(extra._p)

doc.save(target)
print(target)
