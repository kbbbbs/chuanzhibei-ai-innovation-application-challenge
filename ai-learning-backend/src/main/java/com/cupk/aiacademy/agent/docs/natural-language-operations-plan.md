# Agent 全平台自然语言操作计划

## 目标

让用户通过自然语言完成智能教育平台内的查询和操作。当前阶段先保持 Spring Boot 单体内模块化，不拆真实 MCP server
或独立服务；每个业务域按 server 模块组织工具，后续如需开放给外部 Agent，再把这些模块适配成 MCP server。

## 模块边界

```text
agent/
  core/          Agent 会话、Prompt 编排、LLM 调用、异常处理
  definition/    Agent 定义、模型参数、知识库和工具配置管理
  knowledge/     知识库上下文提供器
  tool/
    api/         工具接口、上下文、后续参数/确认/审计协议
    support/     权限、可见范围、关键词、格式化等共享逻辑
    course/      课程、章节、课时、资源、教学班、班级学生
    homework/    作业、提交、批改反馈
    learning/    学习进度、学习计划、成绩
    exam/        考试、试卷、试题、答题、考试记录、考试流程
    paper/       论文检索、本地论文、推荐
    notice/      通知
    user/        用户、角色、权限
    file/        文件上传和资源引用
    audit/       操作日志、执行轨迹
```

## 工具分级

只读工具：允许直接执行，例如查询课程、作业、考试安排、学习进度、论文资料。

写操作工具：必须先生成可确认的操作计划，用户确认后再执行，例如创建课程、发布作业、批改成绩、发布通知、修改班级成员。

高风险工具：需要更严格的角色校验和二次确认，例如删除数据、导入成绩、修改用户角色、结束考试。

## 执行链路

1. 用户发起自然语言请求。
2. `AgentChatService` 构造对话上下文。
3. 轻量意图路由先判断是否命中明确写操作计划；未命中再进入普通 Prompt + LLM。
4. `AgentToolExecutor` 调用对应业务域工具。
5. 写操作先返回结构化预览和 `actionId`，待用户确认。
6. 用户确认后执行真实业务 service/mapper。
7. 记录操作日志、工具输入输出、执行人、角色、影响数据 ID。
8. LLM 基于工具结果生成面向用户的回复。

## 分阶段实施

### 第一阶段：只读覆盖

- 完成工具接口稳定化：`AgentTool`、`AgentToolContext`、后续补 `AgentToolResult`。
- 按业务域补齐查询工具。
- 只返回当前用户有权限看到的数据。
- 输出统一为可拼入 Prompt 的文本，同时保留后续结构化结果扩展点。

优先顺序：

1. 课程域：课程、章节、课时、资源、教学班。
2. 作业域：作业、提交记录、批改反馈。
3. 学习域：学习进度、成绩、学习建议。
4. 考试域：考试、试卷、试题、考试记录。
5. 论文域：论文搜索、本地论文、推荐。
6. 通知和用户域：通知列表、用户角色查询。

### 第二阶段：写操作计划

- 新增 `AgentActionPlan`：描述目标动作、参数、影响范围、风险等级。
- 新增 `AgentPendingActionStore`：保存待确认操作、过期时间、创建人、确认 ID。
- 写操作工具只生成计划，不直接落库。
- 前端展示确认卡片，用户点击确认后调用执行接口。

典型动作：

- 创建/更新课程、章节、课时和资源。
- 发布、修改和批改作业。
- 创建考试、组卷、发布考试、结束考试。
- 发布通知。
- 导入成绩。

### 第三阶段：确认执行和审计

- 新增确认执行接口：`POST /api/agent/actions/confirm`。
- 请求体包含 `actionId`、`actionCode`、`confirmed`、`parameters`；优先通过 `actionId` 找回待确认计划，也保留直接 `actionCode`
  执行能力。
- 执行时重新校验用户、角色、数据可见范围和参数合法性。
- 所有写操作进入 `OperationLog` 或新的 Agent 审计表。
- 对失败操作返回可恢复错误，不让 LLM 自行猜测结果。

当前已落地的写操作闭环：

- `course_create_plan`：生成 `course.create` 的创建课程计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `course.create`：教师或管理员通过 `actionId` 或 `actionCode` 确认后创建课程，执行时校验课程分类、教师、课程名称、评分和状态；教师默认只能以当前登录教师身份创建，管理员可指定教师。
- `course_update_plan`：生成 `course.update` 的修改课程计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `course.update`：教师或管理员通过 `actionId` 或 `actionCode` 确认后修改已有课程，执行时校验课程ID、教师归属和课程分类，只允许修改标题、描述、封面、展示教师名、难度、分类和评分。
- `course_status_plan`：生成 `course.status` 的课程状态变更计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认变更课程状态`。
- `course.status`：教师或管理员通过 `actionId` 或 `actionCode` 确认后变更课程状态，执行时校验课程ID、教师归属和状态范围，当前用于上下架、停用、恢复等场景，不物理删除课程记录。
- `course_delete_plan`：生成 `course.delete` 的课程删除计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认删除课程`。
- `course.delete`：教师或管理员通过 `actionId` 或 `actionCode` 确认后删除课程，执行时校验课程ID或课程名称定位、教师归属和二次确认文本；删除类表达不再映射为下架。
- `chapter_create_plan`：生成 `chapter.create` 的创建章节计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `chapter.create`：教师或管理员通过 `actionId` 或 `actionCode` 确认后创建章节，执行时校验课程ID、课程归属、章节标题和排序值。
- `chapter_update_plan`：生成 `chapter.update` 的修改章节计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `chapter.update`：教师或管理员通过 `actionId` 或 `actionCode` 确认后修改已有章节，执行时校验章节ID、原课程归属、目标课程归属和排序值，只允许修改所属课程、标题、描述和排序值。
- `chapter_sort_plan`：生成 `chapter.sort` 的章节排序计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `chapter.sort`：教师或管理员通过 `actionId` 或 `actionCode` 确认后批量更新同一课程下章节排序，执行时校验课程ID、课程归属、章节归属、重复章节ID和重复排序值。
- `chapter_delete_plan`：生成 `chapter.delete` 的章节删除计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认删除章节`。
- `chapter.delete`：教师或管理员通过 `actionId` 或 `actionCode` 确认后软删除章节，执行时校验章节ID、课程归属和二次确认文本；当前只把章节标记为已删除，不物理删除章节记录，也不删除章节下课时。
- `lesson_create_plan`：生成 `lesson.create` 的创建课时计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `lesson.create`：教师或管理员通过 `actionId` 或 `actionCode` 确认后创建课时，执行时校验章节ID、章节所属课程归属、课时标题、资源字段、时长、排序值和状态。
- `lesson_update_plan`：生成 `lesson.update` 的修改课时计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `lesson.update`：教师或管理员通过 `actionId` 或 `actionCode` 确认后修改已有课时，执行时校验课时ID、原章节课程归属、目标章节课程归属、资源字段、时长、排序值和状态，只允许修改所属章节、标题、资源类型、资源地址、正文、视频地址、时长、排序和状态。
- `lesson_sort_plan`：生成 `lesson.sort` 的课时排序计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `lesson.sort`：教师或管理员通过 `actionId` 或 `actionCode` 确认后批量更新同一章节下课时排序，执行时校验章节ID、章节所属课程归属、课时归属、重复课时ID和重复排序值。
- `lesson_status_plan`：生成 `lesson.status` 的课时状态变更计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认变更课时状态`。
- `lesson.status`：教师或管理员通过 `actionId` 或 `actionCode` 确认后变更课时状态，执行时校验课时ID、章节所属课程归属和状态范围，当前用于上下架、停用、恢复等场景，不物理删除课时记录。
- `course_resource_attach_plan`：生成 `course.resource.attach` 的课程资源挂载计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `course.resource.attach`：教师或管理员通过 `actionId` 或 `actionCode` 确认后给课时挂载或替换视频、文档、资料链接、正文等资源字段，执行时校验课时ID、章节所属课程归属和资源字段合法性。
- `course_resource_remove_plan`：生成 `course.resource.remove` 的课程资源移除计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认移除课程资源`。
- `course.resource.remove`：教师或管理员通过 `actionId` 或 `actionCode` 确认后清空课时上的一个或多个资源字段，执行时校验课时ID、章节所属课程归属和移除字段合法性；不删除课时记录，也不删除文件存储中的原始文件。
- `teaching_class_create_plan`：生成 `teaching.class.create` 的创建教学班计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `teaching.class.create`：教师或管理员通过 `actionId` 或 `actionCode` 确认后创建教学班，执行时校验课程ID、课程归属、教师用户、教学班名称和状态；教师只能创建自己负责课程下的教学班。
- `teaching_class_update_plan`：生成 `teaching.class.update` 的修改教学班计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `teaching.class.update`：教师或管理员通过 `actionId` 或 `actionCode` 确认后修改教学班基础信息，执行时校验教学班ID、教师归属、目标课程归属和教师用户；不通过该动作变更状态。
- `teaching_class_status_plan`：生成 `teaching.class.status` 的教学班状态变更计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认变更教学班状态`。
- `teaching.class.status`：教师或管理员通过 `actionId` 或 `actionCode` 确认后变更教学班状态，执行时校验教学班ID、教师归属和状态范围；当前用于结束、停用、恢复等场景，不物理删除教学班记录。
- `class_student_add_plan`：生成 `class.student.add` 的添加班级学生计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `class.student.add`：教师或管理员通过 `actionId` 或 `actionCode` 确认后把学生加入教学班，执行时校验教学班归属、学生用户存在、重复加入和状态范围。
- `class_student_remove_plan`：生成 `class.student.remove` 的移除班级学生计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认移除班级学生`。
- `class.student.remove`：教师或管理员通过 `actionId` 或 `actionCode` 确认后删除教学班学生名单记录，执行时校验名单记录、教学班归属；不删除学生账号、学习进度或成绩记录。
- `class_student_status_plan`：生成 `class.student.status` 的班级学生状态变更计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认变更班级学生状态`。
- `class.student.status`：教师或管理员通过 `actionId` 或 `actionCode` 确认后调整教学班学生正常/休学状态，执行时校验名单记录、教学班归属和状态范围。
- `notice_create_plan`：生成 `notice.create` 的发布通知计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `notice.create`：教师或管理员通过 `actionId` 或 `actionCode` 确认后发布通知，执行时校验发布人、教学班、标题、正文和状态。
- `homework_create_plan`：生成 `homework.create` 的发布作业计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `homework.create`：教师或管理员通过 `actionId` 或 `actionCode` 确认后发布作业，执行时校验教学班、教师、标题、满分、时间和状态。
- `homework_update_plan`：生成 `homework.update` 的修改作业计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `homework.update`：教师或管理员通过 `actionId` 或 `actionCode`
  确认后修改已有作业，执行时校验作业ID、教师归属、时间范围和状态，只允许修改标题、内容、附件、满分、开始/截止时间和状态。
- `homework_delete_plan`：生成 `homework.delete` 的删除作业计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认删除作业`。
- `homework.delete`：教师或管理员通过 `actionId` 或 `actionCode` 确认后删除已有作业，执行时校验作业ID和教师归属，并在审计中保留被删除作业的关键字段。
- `homework_submission_grade_plan`：生成 `homework.submission.grade` 的批改作业提交计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认批改作业`。
- `homework.submission.grade`：教师或管理员通过 `actionId` 或 `actionCode` 确认后批改作业提交，执行时校验提交ID、作业归属、教师归属和分数范围，更新
  `t_homework_submit` 的得分、反馈、批改状态、批改人和批改时间；当前不自动同步写入 `t_grade_record`。
- `exam_session_create_plan`：生成 `exam.session.create` 的创建考试安排计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认创建考试`。
- `exam.session.create`：教师或管理员通过 `actionId` 或 `actionCode` 确认后创建考试安排，执行时校验教学班、教师、试卷、考试名称、开始/结束时间、时长和状态，并复用现有
  `ExamService`。
- `exam_session_close_plan`：生成 `exam.session.close` 的关闭考试安排计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认关闭考试`。
- `exam.session.close`：教师或管理员通过 `actionId` 或 `actionCode` 确认后关闭考试安排，执行时校验考试ID、操作者角色、教师归属，并复用现有
  `ExamService.close`。
- `exam_answer_grade_plan`：生成 `exam.answer.grade` 的考试答题批改计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认批改考试`。
- `exam.answer.grade`：教师或管理员通过 `actionId` 或 `actionCode` 确认后批改考试答题，执行时校验答卷、考试归属、批改教师权限，并复用现有
  `SubmissionService.manualGrade` 更新答题分、主观题分、总分和答卷状态。
- `exam_paper_create_plan`：生成 `exam.paper.create` 的创建试卷计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `exam.paper.create`：教师或管理员通过 `actionId` 或 `actionCode` 确认后创建试卷基础信息，执行时校验课程ID、课程归属、教师用户、试卷名称、满分、时长和状态；不会自动添加题目或创建考试安排。
- `exam_paper_update_plan`：生成 `exam.paper.update` 的修改试卷计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `exam.paper.update`：教师或管理员通过 `actionId` 或 `actionCode` 确认后修改试卷基础信息，执行时校验试卷ID、课程归属、教师用户、满分、时长和状态；不会自动调整试卷题目关系或考试安排。
- `exam_paper_delete_plan`：生成 `exam.paper.delete` 的删除试卷计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认删除试卷`。
- `exam.paper.delete`：教师或管理员通过 `actionId` 或 `actionCode` 确认后软删除试卷，执行时校验试卷ID、课程归属和教师权限；当前只把试卷标记为归档状态，不物理删除试卷、试卷题目关系或考试安排。
- `exam_paper_question_add_plan`：生成 `exam.paper.question.add` 的试卷添加题目计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `exam.paper.question.add`：教师或管理员通过 `actionId` 或 `actionCode` 确认后把已有题库题目加入草稿试卷，执行时校验试卷ID、题目ID、课程归属、教师权限、重复题目和重复排序，并重算试卷总分。
- `exam_paper_question_remove_plan`：生成 `exam.paper.question.remove` 的试卷移除题目计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认移除试卷题目`。
- `exam.paper.question.remove`：教师或管理员通过 `actionId` 或 `actionCode` 确认后从草稿试卷移除题目关系，执行时校验试卷ID、试卷题目关系ID或题目ID、课程归属、教师权限和答题引用，并重算试卷总分；不会删除题库题目本身。
- `exam_paper_question_update_plan`：生成 `exam.paper.question.update` 的试卷题目调整计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `exam.paper.question.update`：教师或管理员通过 `actionId` 或 `actionCode` 确认后调整草稿试卷题目的分值、顺序和必答设置，执行时校验试卷ID、试卷题目关系ID或题目ID、课程归属、教师权限、重复排序和答题引用，并重算试卷总分。
- `question_create_plan`：生成 `question.create` 的创建试题计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `question.create`：教师或管理员通过 `actionId` 或 `actionCode` 确认后创建题库试题，执行时校验课程归属、教师身份、题型、难度、状态、选项和答案，并复用现有
  `QuestionService.create`。
- `question_update_plan`：生成 `question.update` 的修改试题计划和 10 分钟有效的 `actionId`，不写业务数据库。
- `question.update`：教师或管理员通过 `actionId` 或 `actionCode` 确认后修改题库试题，执行时校验试题ID、原试题归属、目标课程归属、教师身份、题型、难度、状态、选项和答案，并复用现有
  `QuestionService.update`。
- `question_delete_plan`：生成 `question.delete` 的删除试题计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认删除试题`。
- `question.delete`：教师或管理员通过 `actionId` 或 `actionCode` 确认后删除题库试题，执行时校验试题ID、课程归属和教师权限，并复用现有
  `QuestionService.delete`；数据库外键会级联删除该试题在试卷中的题目关联和考试答题记录，审计结果会记录关联影响数量。
- `grade_record_create_plan`：生成 `grade.record.create` 的录入成绩记录计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认录入成绩`。
- `grade.record.create`：教师或管理员通过 `actionId` 或 `actionCode` 确认后录入一条成绩记录，执行时校验学生、教学班、课程、教师、成绩名称、来源和分数范围，默认来源为
  `manual`、满分为 100。
- `grade_record_update_plan`：生成 `grade.record.update` 的修改成绩记录计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认修改成绩`。
- `grade.record.update`：教师或管理员通过 `actionId` 或 `actionCode` 确认后修改已有成绩记录，执行时校验成绩ID、教师归属、分数范围，只允许修改分数、满分、评语和成绩名称。
- `grade_record_delete_plan`：生成 `grade.record.delete` 的删除成绩记录计划和 10 分钟有效的 `actionId`，风险等级为 `HIGH`
  ，要求二次确认文本 `确认删除成绩`。
- `grade.record.delete`：教师或管理员通过 `actionId` 或 `actionCode` 确认后删除已有成绩记录，执行时校验成绩ID、教师归属，并在审计中保留被删除记录的关键字段。
- 审计：确认执行成功后写入 `t_operation_log`，记录执行人、动作、接口、参数和影响数据 ID。
- 聊天路由：Agent
  启用对应计划工具后，创建/新增课程、修改/调整课程、上下架/停用/恢复/删除课程、创建/新增章节、修改/调整章节、章节排序/调整顺序、删除/移除章节、创建/新增课时、修改/调整课时、课时排序/调整顺序、上下架/停用/恢复/删除课时、挂载/绑定/替换课程资源、移除/清空/解绑课程资源、创建/修改/结束教学班、添加/移除班级学生、变更班级学生正常或休学状态、发布/创建/发送通知或公告、发布/布置作业、修改/调整作业、删除/撤销作业、批改/评分作业提交、创建/安排/发布考试、关闭/结束考试、批改考试答题、创建/新增试卷、修改/调整试卷、删除/撤销/归档试卷、给试卷添加/新增题目或组卷、从试卷移除/删除题目、调整试卷题目分值或顺序、创建/新增试题、修改/调整试题、删除/撤销试题、录入/登记成绩、修改/调整成绩、删除/撤销成绩类自然语言请求会直接返回待确认计划，不先交给模型自由判断。

### 第四阶段：自然语言体验优化

- 支持更多明确意图路由：导入成绩、批量成绩复核等先进入写计划，不让模型猜测执行结果。
- 支持多轮补参：缺少课程名、截止时间、班级等参数时先追问。
- 支持歧义消解：多个课程/班级匹配时让用户选择。
- 支持撤销策略：对可逆操作提供撤销入口，对不可逆操作只允许二次确认。
- 支持按角色定制工具集：学生、教师、管理员看到不同工具。

## 实现原则

- 工具必须复用现有业务 service 或明确封装业务规则，不能在 Agent 层绕过权限和校验。
- 只读工具可以直接执行，写操作必须确认。
- 工具输出要同时考虑 LLM 可读和系统可审计。
- Agent 不直接信任模型生成的参数，执行前必须做类型、范围、权限和业务状态校验。
- 不急着拆真实 MCP server；等内部工具协议稳定后，再按业务域适配 MCP。
