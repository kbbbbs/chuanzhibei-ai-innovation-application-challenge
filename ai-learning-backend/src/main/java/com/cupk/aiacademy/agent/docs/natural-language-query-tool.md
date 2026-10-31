# 旧自然语言查询工具迁移说明

更新日期：2026-07-10

## 当前状态

`natural_language_query` 已退出生产链路，不能再通过 `enabledTools` 启用：

- `NaturalLanguageQueryTool`、`NaturalLanguageQueryService`、`NaturalLanguageEntityResolver` 和 `NaturalLanguageTimeParser` 均不再注册为 Spring Bean。
- 默认 Agent 工具清单不包含 `natural_language_query`。
- `AgentPromptAssembler` 不再自动调用规则查询，也不再一次执行全部读工具。
- 旧源码和旧单测暂时保留，只用于迁移对照，不能作为当前架构说明。

## 当前替代链路

读请求统一走：

```text
用户自然语言
-> LlmIntentSelector（摘要级选择 CHAT/READ/WRITE）
-> SchemaDrivenReadToolPlanner（仅接收选中的一个读工具 Schema）
-> LLM 从启用的专用读工具中选择 TOOL/CHAT，并生成 arguments JSON
-> AgentActionSchemaValidator 校验字段、类型、必填、二选一和范围
-> 失败后把结构化错误反馈给 LLM，默认最多修复 1 轮
-> 执行一个专用读工具
-> observation 交给最终模型回答
```

首次 `CHAT` 会再次让模型核对工具目录；连续两次都判断为普通聊天才放行。限定轮次后仍无法得到合法工具 JSON 时，系统明确标记“未执行平台数据库查询”，要求最终回答请用户补充查询对象或条件。

## 专用替代工具

| 旧规则能力 | 当前工具 |
| --- | --- |
| 列出课程分类 | `course_category_search` |
| 按分类查课程 | `course_by_category_search` |
| 按课程查章节和课时 | `course_structure_search` |
| 课程数量统计 | `course_stats` |
| 作业提交率和未交名单 | `homework_submission_stats` |
| 考试分数统计 | `exam_stats` |
| 教师申请查询 | `teacher_application_search` |
| 导入日志查询 | `import_log_search` |
| 题库查询 | `question_search` |

其他课程、章节、课时、教学班、通知、作业、考试、试卷、成绩、学习进度和论文查询继续使用各自的专用读工具。

## 后续清理

确认不再需要旧实现做迁移对照后，可以物理删除 `agent/query/NaturalLanguageQueryService.java`、相关规则解析类、`NaturalLanguageQueryTool.java` 及对应旧单测。删除前应保持 `LegacyReadRouterIsolationTest` 和真实 HTTP 回归通过。
