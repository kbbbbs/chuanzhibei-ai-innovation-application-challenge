# Agent 自然语言操作进度与剩余计划

## 当前结论

当前 Agent 已完成模块化重构，并具备“用户自然语言 -> LLM Planner -> action schema 后端校验 -> 错误反馈修复 -> pending action -> 用户确认 -> 权限和业务校验 -> 写数据库及审计日志”的基础闭环。

当前执行逻辑已经采用受控的单工具 ReAct 形态：写操作由模型选择 action schema 并经过最多 3 轮校验修复；读操作由模型选择一个读工具，后端执行后把 observation 交给模型生成最终答复。权限、业务状态和数据库写入仍只由本地执行器负责。

## 已完成基础能力

| 能力 | 当前状态 | 说明 |
| --- | --- | --- |
| Agent 模块结构 | 已完成 | 已拆为 `core`、`definition`、`knowledge`、`tool`、`action`、`docs` 等目录。 |
| 工具协议 | 已完成 | 已有 `AgentTool`、`AgentToolContext`、`AgentToolResult` 和工具别名注册。 |
| 写操作计划 | 已完成 | 已有 `AgentActionPlan`，写工具只生成计划，不直接落库。 |
| 通用动作规格 | 已完成第一版 | 新增 `AgentActionSpec`、`AgentActionParameterSpec`、`AgentActionPlanFactory`，统一动作编码、风险、参数 schema、待确认计划和 pending action 创建。 |
| Schema-first 参数抽取 | 已完成第一版 | `AgentActionParameterSpec` 已支持 type、enum、aliases、examples 和 JSON Schema；`AgentActionSchemaValidator` 统一处理模型/本地抽取结果的类型转换、枚举校验、缺字段和未知字段诊断。 |
| 动作能力目录 | 已完成第一版 | `AgentActionCatalog` 可从已迁移的 PlanTool 自动收集 `AgentActionSpec`，为后续“先选择动作，再抽取参数”的通用路由做准备。 |
| 待确认动作 | 已完成 | 已有 `AgentPendingActionStore`，支持 `actionId` 和过期时间。 |
| 确认执行接口 | 已完成 | 已有 `POST /api/agent/actions/confirm`。 |
| 高风险确认文本 | 已完成 | `HIGH`/`CRITICAL` 操作必须匹配确认文本。 |
| 执行审计 | 已完成 | 成功确认后写入 `t_operation_log`。 |
| 权限再校验 | 已完成 | 执行器内重新校验角色、归属、参数范围和数据存在性。 |
| 旧关键词意图路由 | 已隔离 | `AgentActionIntentRouter`、`AgentActionSelectionService`、`NaturalLanguageQueryService` 及其查询规则辅助类均不注册为 Spring Bean。 |

## 已完成只读工具

| 模块 | 已实现工具 | 覆盖范围 |
| --- | --- | --- |
| 课程 | `course_search` | 查询当前用户可见课程。 |
| 课程分类 | `course_category_search` | 查询全部分类或按分类名称查询。 |
| 分类课程 | `course_by_category_search` | 按分类名称查询当前用户可见课程。 |
| 章节 | `chapter_search` | 查询当前用户可见课程章节。 |
| 课时 | `lesson_search` | 查询课时内容。 |
| 课程结构 | `course_structure_search` | 按课程名称跨表查询章节和课时。 |
| 课程资源 | `course_resource_search` | 查询课时/课程资源。 |
| 教学班 | `teaching_class_search` | 查询教学班信息。 |
| 作业 | `homework_search` | 查询作业信息。 |
| 作业提交统计 | `homework_submission_stats` | 按作业或教学班统计应交、已交、未交和提交率。 |
| 学习进度 | `learning_progress` | 查询学习进度。 |
| 学习建议 | `learning_plan` | 基于进度生成学习建议。 |
| 考试安排 | `exam_session_search` | 查询考试安排。 |
| 考试记录 | `exam_record_search` | 查询考试记录。 |
| 考试统计 | `exam_stats` | 统计考试记录数、平均分、最高分和最低分。 |
| 试卷 | `exam_paper_search` | 查询试卷信息。 |
| 成绩 | `grade_record_search` | 查询成绩记录。 |
| 论文 | `paper_local_search` | 查询本地论文资料。 |
| 通知 | `notice_search` | 查询通知。 |
| 教师申请 | `teacher_application_search` | 管理员按关键词和审核状态查询申请。 |
| 导入日志 | `import_log_search` | 管理员或教师按关键词和 HTTP 状态查询导入日志。 |
| 课程统计 | `course_stats` | 按课程统计章节、课时、教学班和作业数量。 |
| 题库 | `question_search` | 教师或管理员按关键词、课程、题型和状态查询题目。 |

## 已完成写操作闭环

| 业务域 | 计划工具 | 执行动作 | 风险 | 当前状态 |
| --- | --- | --- | --- | --- |
| 课程 | `course_create_plan` | `course.create` | 中 | 已完成创建课程；支持课程分类名称到 ID 解析（categoryName），名称不唯一时要求补充 categoryId。 |
| 课程 | `course_update_plan` | `course.update` | 中 | 已完成修改课程基础信息，不允许修改教师归属、人数、浏览量和状态；支持按分类名称修改 categoryId。 |
| 课程 | `course_status_plan` | `course.status` | 高 | 已完成课程状态变更，用于上下架、停用、恢复等场景；不物理删除课程记录；返回 impactPreview（受影响章节/课时/教学班/学生数）。 |
| 课程 | `course_delete_plan` | `course.delete` | 高 | 已完成课程删除；删除类表达走 DELETE 语义，不再映射为下架状态。 |
| 章节 | `chapter_create_plan` | `chapter.create` | 中 | 已完成创建章节。 |
| 章节 | `chapter_update_plan` | `chapter.update` | 中 | 已完成修改章节，支持修改标题、描述、排序值和所属课程。 |
| 章节 | `chapter_sort_plan` | `chapter.sort` | 中 | 已完成同一课程下章节排序调整。 |
| 章节 | `chapter_delete_plan` | `chapter.delete` | 高 | 已完成章节软删除，标记章节为已删除；不物理删除章节记录，也不删除章节下课时。 |
| 课时 | `lesson_create_plan` | `lesson.create` | 中 | 已完成创建课时。 |
| 课时 | `lesson_update_plan` | `lesson.update` | 中 | 已完成修改课时，支持修改标题、内容、资源字段、排序、状态和所属章节。 |
| 课时 | `lesson_sort_plan` | `lesson.sort` | 中 | 已完成同一章节下课时排序调整。 |
| 课时 | `lesson_status_plan` | `lesson.status` | 高 | 已完成课时状态变更，用于上下架、停用、恢复等场景；不物理删除课时记录。 |
| 课程资源 | `course_resource_attach_plan` | `course.resource.attach` | 中 | 已完成给课时挂载或替换视频、文档、资料链接、正文等资源字段。 |
| 课程资源 | `course_resource_remove_plan` | `course.resource.remove` | 高 | 已完成清空课时资源字段；不删除课时记录，也不删除文件存储中的原始文件。 |
| 教学班 | `teaching_class_create_plan` | `teaching.class.create` | 中 | 已完成创建教学班，教师只能创建自己负责课程下的教学班，管理员可指定教师。 |
| 教学班 | `teaching_class_update_plan` | `teaching.class.update` | 中 | 已完成修改教学班基础信息和归属课程；状态变更不走普通修改动作。 |
| 教学班 | `teaching_class_status_plan` | `teaching.class.status` | 高 | 已完成教学班结束/恢复等状态变更；不物理删除教学班记录。 |
| 班级学生 | `class_student_add_plan` | `class.student.add` | 中 | 已完成把学生加入教学班，执行时校验教学班归属、学生存在和重复加入。 |
| 班级学生 | `class_student_remove_plan` | `class.student.remove` | 高 | 已完成从教学班学生名单中移除学生；不删除学生账号、学习进度或成绩。 |
| 班级学生 | `class_student_status_plan` | `class.student.status` | 高 | 已完成班级学生正常/休学状态变更。 |
| 通知 | `notice_create_plan` | `notice.create` | 中 | 已完成发布通知。 |
| 通知 | `notice_update_plan` | `notice.update` | 中 | 已完成修改通知，支持按 noticeId 或原通知标题唯一定位，修改标题、正文、通知类型和目标教学班；教师只能修改自己发布的通知。 |
| 通知 | `notice_status_plan` | `notice.status` | 高 | 已完成通知状态变更，支持撤回（0）/发布（1），删除类表达映射为撤回，不物理删除通知记录。 |
| 作业 | `homework_create_plan` | `homework.create` | 中 | 已完成发布作业。 |
| 作业 | `homework_update_plan` | `homework.update` | 中 | 已完成修改作业。 |
| 作业 | `homework_delete_plan` | `homework.delete` | 高 | 已完成删除作业。 |
| 作业提交 | `homework_submission_grade_plan` | `homework.submission.grade` | 高 | 已完成批改作业提交；当前只更新 `t_homework_submit`，不自动同步 `t_grade_record`。 |
| 考试安排 | `exam_session_create_plan` | `exam.session.create` | 高 | 已完成创建考试安排。 |
| 考试安排 | `exam_session_close_plan` | `exam.session.close` | 高 | 已完成关闭考试安排。 |
| 考试答题 | `exam_answer_grade_plan` | `exam.answer.grade` | 高 | 已完成主观题/答题人工批改。 |
| 考试试卷 | `exam_paper_create_plan` | `exam.paper.create` | 中 | 已完成创建试卷基础信息；不会自动添加题目或创建考试安排。 |
| 考试试卷 | `exam_paper_update_plan` | `exam.paper.update` | 中 | 已完成修改试卷基础信息；不会自动调整试卷题目关系或考试安排。 |
| 考试试卷 | `exam_paper_delete_plan` | `exam.paper.delete` | 高 | 已完成试卷软删除，把试卷标记为归档状态；不会物理删除试卷、试卷题目关系或考试安排。 |
| 试卷题目关系 | `exam_paper_question_add_plan` | `exam.paper.question.add` | 中 | 已完成把已有题库题目加入草稿试卷，并重算试卷总分；不会发布试卷或创建考试安排。 |
| 试卷题目关系 | `exam_paper_question_remove_plan` | `exam.paper.question.remove` | 高 | 已完成从草稿试卷移除题目关系，并重算试卷总分；不会删除题库题目，已被答卷使用的试卷题目不能移除。 |
| 试卷题目关系 | `exam_paper_question_update_plan` | `exam.paper.question.update` | 中 | 已完成调整草稿试卷题目的分值、顺序和必答设置，并重算试卷总分；已被答卷使用的试卷题目不能调整。 |
| 试题 | `question_create_plan` | `question.create` | 中 | 已完成创建试题，复用题库服务校验题型、难度、选项和答案；教师只能给自己负责课程创建试题。 |
| 试题 | `question_update_plan` | `question.update` | 中 | 已完成修改试题，支持部分字段更新并合并原题数据，复用题库服务重新校验题型、选项和答案。 |
| 试题 | `question_delete_plan` | `question.delete` | 高 | 已完成删除试题；物理删除题库记录，并在审计结果中记录受影响的试卷题目关联数和考试答题记录数。 |
| 成绩 | `grade_record_create_plan` | `grade.record.create` | 高 | 已完成录入成绩记录。 |
| 成绩 | `grade_record_update_plan` | `grade.record.update` | 高 | 已完成修改成绩记录。 |
| 成绩 | `grade_record_delete_plan` | `grade.record.delete` | 高 | 已完成删除成绩记录。 |

## 通用动作模型迁移状态

当前已经确定后续写操作的标准实现路线：

1. 计划工具只声明 `AgentActionSpec`，不再手写 `AgentActionPlan` 和 `pendingActionStore.create(...)`。
2. 参数字段必须声明 type、必要 aliases、必要 enum 和 examples，让模型看到的是动作 schema，而不是整套数据库或 Java 代码。
3. `LlmActionRepairLoop` 负责模型调用、JSON 解析和最多 3 轮结构化修复。
4. `LlmActionDecisionValidator` 与 `AgentActionSchemaValidator` 统一校验 actionCode、字段白名单、类型、枚举、必填和二选一约束。
5. 只有校验通过后，`LlmValidatedPlanFactory` 才生成 `pendingAction`；修复后仍失败则返回需要用户补充的信息。
6. `LlmIntentSelector` 先从摘要目录选择单个读工具，`SchemaDrivenReadToolPlanner` 再根据该工具的独立 arguments schema 提取和校验参数；CHAT 不再额外调用模型复核。
7. 前端和历史消息读取结构化 `pendingAction`，执行前通过数据库接口重新校验 actionId。

这条路线等价于在 Java/Spring 内实现 TypeChat 的核心模式：用强 schema 约束模型输出，再用本地校验和业务执行器兜底；不需要把 TypeChat 的 TypeScript runtime 直接引入到后端。

已迁移样板：

| 工具 | 状态 | 说明 |
| --- | --- | --- |
| `notice_create_plan` | 已迁移 | 使用 `AgentActionSpec` + `AgentActionPlanFactory`，返回参数 schema、缺失字段和 pending action。 |
| `notice_update_plan` | 已迁移 | 通知修改计划已统一走 `AgentActionSpec` + `AgentActionPlanFactory`，支持按 ID 或原标题定位。 |
| `notice_status_plan` | 已迁移 | 通知状态变更计划已统一走 `AgentActionSpec` + `AgentActionPlanFactory`，保留高风险确认文本。 |
| `course_create_plan` | 已迁移 | 课程创建计划已统一走 `AgentActionSpec` + `AgentActionPlanFactory`。 |
| `course_update_plan` | 已迁移 | 课程修改计划已统一走 `AgentActionSpec` + `AgentActionPlanFactory`。 |
| `course_status_plan` | 已迁移 | 课程状态变更计划已统一走 `AgentActionSpec` + `AgentActionPlanFactory`，保留高风险确认文本。 |

下一批建议迁移顺序：

1. 课时/章节域：`chapter_create_plan`、`chapter_update_plan`、`lesson_create_plan`、`lesson_update_plan`。
2. 作业域：`homework_create_plan`、`homework_update_plan`、`homework_delete_plan`。
3. 考试和成绩高风险域：先迁移计划工具，再补确认文本、影响预览和更严格参数 schema。

## 待实现内容模块总览

| 优先级 | 模块 | 待实现内容 | 原因 |
| --- | --- | --- | --- |
| P1 | 课程资源增强 | 资源变更历史、文件引用联动、上传引用和存储文件删除策略。 | 资源挂载和移除已完成；后续补文件侧一致性和可追踪性。 |
| P1 | 班级学生增强 | 批量添加、批量移除、批量调整状态。 | 单条添加、移除和状态变更已完成；批量类操作需要更强预览和确认。 |
| P1 | 课时域增强 | 课时删除前依赖影响预览、恢复策略、资源变更历史。 | 创建、修改、排序和状态变更已完成，后续补安全体验和跨模块联动。 |
| P1 | 课程域增强 | 课程状态枚举统一、删除前依赖影响预览、恢复策略。 | 创建、修改和状态变更已完成，后续补安全体验和跨模块联动。 |
| P1 | 作业提交 | 查询提交详情、退回/重置批改状态、批量批改。 | 当前只完成单条提交批改。 |
| P1 | 考试试卷 | 组卷增强。 | 试卷创建、修改、软删除、添加题目、移除题目和调整题目已完成；后续补批量组卷体验。 |
| P1 | 试题 | 题目导入。 | 题目创建、修改和删除已完成；题库管理还需要补导入能力。 |
| P1 | 试卷题目关系 | 批量调整、影响预览和更强的组卷体验。 | 添加、移除、调整分值和顺序已完成；后续补批量能力和确认预览。 |
| P1 | 考试记录 | 强制交卷、重置答卷、重新计算成绩。 | 属于高风险考试流程操作。 |
| P1 | 成绩导入 | 批量导入、导入日志查询、导入回滚策略。 | 计划中已有导入成绩方向，但尚未落地。 |
| P2 | 学习进度 | 手动修正进度、重置学习记录。 | 需要谨慎，防止影响学习统计。 |
| P2 | 通知 | 影响预览。 | 创建、修改、撤回/状态变更已完成；前端列表/详情跳转入口已完成（NoticeList 视图+路由+agent 深链）。 |
| P2 | 论文 | 外部检索、推荐、收藏/本地论文入库。 | 当前只有本地论文查询工具。 |
| P2 | 用户与角色 | 用户查询、禁用/启用、角色调整。 | 高风险管理操作，需要更严格确认。 |
| P2 | 文件 | 上传引用、资源绑定、文件删除。 | 需要和课程资源、作业附件结合设计。 |
| P2 | 审计 | Agent 执行轨迹查询、按用户/动作筛选。 | 便于追踪自然语言操作结果。 |

## 下一步推荐执行顺序

1. 试卷和题库：实现题目导入、批量组卷和试卷题目关系批量调整的写操作闭环。
2. 批量类操作：在单条操作稳定后，再做批量成绩导入、批量批改、批量加学生。
3. 资源增强：补文件引用联动、资源变更历史和存储文件删除策略。
4. 学习进度和通知增强：补进度重置、通知修改/撤回等高风险动作。

## 每个新动作的标准落地清单

1. 新增计划工具：放到对应 `agent/tool/{domain}` 包，优先声明 `AgentActionSpec` 并调用 `AgentActionPlanFactory.createWritePlan(...)`。
2. 新增执行器：放到对应 `agent/action/{domain}` 包，实现 `AgentActionExecutor`。
3. 补 schema：确保动作被 `AgentActionCatalog` 收集，并在 Planner 的 enabled specs 中可见；不要在 `AgentChatService` 添加关键词判断。
4. 补权限：执行器内校验角色、数据归属、状态和参数范围。
5. 补审计：确认执行成功后依赖 `AgentActionExecutionService` 统一写日志。
6. 补测试：覆盖 Planner 选择、JSON 修复、schema 校验、确认执行、拒绝越权和非法业务状态。
7. 更新文档：同步本进度文档和 `natural-language-operations-plan.md`。

## 当前不建议优先做的内容

- 暂不拆真实 MCP server。当前内部工具协议还在扩展，先保持 Spring Boot 单体内模块化。
- 暂不扩展为无限轮自主执行；当前保持最多 3 轮规划修复、单个读工具 observation 和所有写操作人工确认。
- 暂不让模型直接生成数据库写操作参数并执行。所有写操作仍必须走计划、确认和执行器校验。

## 结构化读工具链路

当前读链路是 `用户输入 -> LLM 选择 TOOL/CHAT -> 工具 arguments schema 校验 -> 错误反馈修复（最多 3 轮） -> 执行一个读工具 -> observation -> 最终模型回答`。

`AgentPromptAssembler` 不再调用 `NaturalLanguageQueryService`，也不会执行全部 enabled 工具。跨表查询由专用工具负责，例如课程结构、课程统计、作业提交统计和考试统计。每个专用工具只接收后端校验后的结构化字段，不再用关键词判断查询意图。

旧 `NaturalLanguageQueryService`、`NaturalLanguageEntityResolver`、`NaturalLanguageTimeParser` 和 `NaturalLanguageQueryTool` 仅保留为迁移参考与旧测试对象，均没有 Spring 组件注解；默认 Agent 也不再包含 `natural_language_query`。

测试覆盖 `SchemaDrivenReadToolPlannerTest`、`AgentReadToolCatalogTest`、`LegacyReadRouterIsolationTest` 和各读工具测试；真实 HTTP 回归已验证分类、分类课程、课程结构、课程统计、作业提交统计、教师申请、导入日志、考试统计和题库的模型选工具日志。

### 写操作实体定位回流

写操作执行器已从硬编码按标题 `eq` 精确匹配升级为复用统一实体定位能力：

| 执行器 | 改造前 | 改造后 |
| --- | --- | --- |
| CourseUpdateActionExecutor | QueryWrapper eq title + LIMIT 2，多匹配只报「请补充 courseId」 | AgentWriteEntityLocator.locateCourse，title 用 like 模糊匹配，多匹配列出候选「11=Java 入门、12=Java 进阶」 |
| CourseStatusActionExecutor | 同上硬编码 | 同上 |
| NoticeUpdateActionExecutor | QueryWrapper eq title + LIMIT 2 | AgentWriteEntityLocator.locateNotice，title 用 like，多匹配列出候选 |
| NoticeStatusActionExecutor | 同上硬编码 | 同上 |

AgentWriteEntityLocator（agent/query）是当前写操作执行阶段的专用定位层：它强制唯一锁定，多候选时抛出带候选 ID+名称的 BusinessException 让用户补充 ID 消歧。旧查询层 `NaturalLanguageEntityResolver` 已退出生产路径。

测试：AgentWriteEntityLocatorTest 覆盖按 ID/模糊标题定位、唯一命中、多候选消歧、无定位条件、未找到；CourseActionExecutorTest/NoticeActionExecutorTest 改用真实 locator + mock mapper 回归。
### 统计查询扩展

统计意图不再由 `isExamStatsQuery` 等关键词分支识别。模型根据工具 schema 选择 `exam_stats`、`homework_submission_stats` 或 `course_stats`，后端使用结构化字段查询并聚合；测试由 Planner、schema catalog、专用工具和真实 HTTP 回归共同覆盖。
