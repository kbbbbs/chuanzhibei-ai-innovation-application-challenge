# Agent 自然语言操作进度与剩余计划

## 当前结论

当前 Agent 已完成模块化重构，并已经具备“自然语言识别明确写操作意图 -> 生成待确认计划 -> 用户确认 -> 执行业务动作 -> 写审计日志”的基础闭环。

当前执行逻辑不是完整 ReAct 或 plan-replan。现阶段采用的是受控直达路由：`AgentChatService` 先判断是否命中明确写操作计划工具，命中后直接返回待确认计划；未命中时再走普通 Prompt、知识库和只读工具上下文。这个策略适合当前目标：通过自然语言操作平台功能，同时避免模型绕过权限或直接猜测执行结果。

## 已完成基础能力

| 能力 | 当前状态 | 说明 |
| --- | --- | --- |
| Agent 模块结构 | 已完成 | 已拆为 `core`、`definition`、`knowledge`、`tool`、`action`、`docs` 等目录。 |
| 工具协议 | 已完成 | 已有 `AgentTool`、`AgentToolContext`、`AgentToolResult` 和工具别名注册。 |
| 写操作计划 | 已完成 | 已有 `AgentActionPlan`，写工具只生成计划，不直接落库。 |
| 待确认动作 | 已完成 | 已有 `AgentPendingActionStore`，支持 `actionId` 和过期时间。 |
| 确认执行接口 | 已完成 | 已有 `POST /api/agent/actions/confirm`。 |
| 高风险确认文本 | 已完成 | `HIGH`/`CRITICAL` 操作必须匹配确认文本。 |
| 执行审计 | 已完成 | 成功确认后写入 `t_operation_log`。 |
| 权限再校验 | 已完成 | 执行器内重新校验角色、归属、参数范围和数据存在性。 |
| 轻量意图路由 | 已完成 | 发布通知、作业、考试、成绩等明确写操作可直接返回计划。 |

## 已完成只读工具

| 模块 | 已实现工具 | 覆盖范围 |
| --- | --- | --- |
| 课程 | `course_search` | 查询当前用户可见课程。 |
| 章节 | `chapter_search` | 查询当前用户可见课程章节。 |
| 课时 | `lesson_search` | 查询课时内容。 |
| 课程资源 | `course_resource_search` | 查询课时/课程资源。 |
| 教学班 | `teaching_class_search` | 查询教学班信息。 |
| 作业 | `homework_search` | 查询作业信息。 |
| 学习进度 | `learning_progress` | 查询学习进度。 |
| 学习建议 | `learning_plan` | 基于进度生成学习建议。 |
| 考试安排 | `exam_session_search` | 查询考试安排。 |
| 考试记录 | `exam_record_search` | 查询考试记录。 |
| 试卷 | `exam_paper_search` | 查询试卷信息。 |
| 成绩 | `grade_record_search` | 查询成绩记录。 |
| 论文 | `paper_local_search` | 查询本地论文资料。 |
| 通知 | `notice_search` | 查询通知。 |

## 已完成写操作闭环

| 业务域 | 计划工具 | 执行动作 | 风险 | 当前状态 |
| --- | --- | --- | --- | --- |
| 课程 | `course_create_plan` | `course.create` | 中 | 已完成创建课程。 |
| 课程 | `course_update_plan` | `course.update` | 中 | 已完成修改课程基础信息，不允许修改教师归属、人数、浏览量和状态。 |
| 课程 | `course_status_plan` | `course.status` | 高 | 已完成课程状态变更，用于上下架、停用、恢复等场景；不物理删除课程记录。 |
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
| P2 | 通知 | 通知修改、撤回/删除。 | 当前只完成发布通知。 |
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

1. 新增计划工具：放到对应 `agent/tool/{domain}` 包，返回 `AgentActionPlan`。
2. 新增执行器：放到对应 `agent/action/{domain}` 包，实现 `AgentActionExecutor`。
3. 补路由：在 `AgentChatService` 增加明确意图识别和工具别名。
4. 补权限：执行器内校验角色、数据归属、状态和参数范围。
5. 补审计：确认执行成功后依赖 `AgentActionExecutionService` 统一写日志。
6. 补测试：覆盖计划工具、确认执行、拒绝越权、拒绝非法参数、聊天直达路由。
7. 更新文档：同步本进度文档和 `natural-language-operations-plan.md`。

## 当前不建议优先做的内容

- 暂不拆真实 MCP server。当前内部工具协议还在扩展，先保持 Spring Boot 单体内模块化。
- 暂不优先实现完整 ReAct/plan-replan。先把工具协议、权限、确认、审计和业务动作覆盖做扎实。
- 暂不让模型直接生成数据库写操作参数并执行。所有写操作仍必须走计划、确认和执行器校验。
