# Agent 自然语言查询工具说明

## 目标

本次新增 `natural_language_query` 读工具，用于补齐 Agent 在真实用户说法下的查询能力。用户可以尽量使用课程名、班级名、学生姓名、作业标题和自然时间表达，而不是必须说 `id=1`。

该工具只新增文件，不改现有聊天链路。Spring 会自动扫描工具 Bean，但只有当 Agent 的 `enabledTools` 包含 `natural_language_query` 或它的别名时，聊天 Prompt 才会带上该工具结果。

## 启用方式

在 `t_agent.enabled_tools` 中加入：

```json
["natural_language_query"]
```

也可以使用别名：

```json
["smart_query"]
```

建议和已有工具一起启用：

```json
[
  "natural_language_query",
  "course_search",
  "homework_search",
  "grade_record_search",
  "learning_progress"
]
```

## 新增文件

- `agent/query/NaturalLanguageTimeRange.java`：自然语言时间解析结果。
- `agent/query/NaturalLanguageTimeParser.java`：解析今天、昨天、本周、下周五、最近 N 天、显式日期等时间范围。
- `agent/query/NaturalLanguageEntityResolver.java`：按名称、昵称、标题、班级名等召回课程、教学班、学生、作业候选。
- `agent/query/NaturalLanguageQueryService.java`：根据自然语言问题执行课程、作业、提交、成绩、学习进度、考试、课程结构和统计查询。
- `agent/tool/query/NaturalLanguageQueryTool.java`：注册为 Agent 读工具，工具名 `natural_language_query`。
- `agent/docs/natural-language-query-tool.md`：当前说明文档。

## 当前支持的说法

### 课程和课程结构

```text
查一下张老师负责的 Python 数据分析课程
查 Python 数据分析基础 这门课下面有哪些章节和课时
统计 Python 数据分析基础 的章节数、课时数、教学班数和作业数
```

### 教学班、作业、提交情况

```text
查 Python 2026 春季班 最近一个月的作业
查 第一次 NumPy 练习 的提交情况
查 Python 数据分析基础 这门课里 第一次 NumPy 练习 哪些学生还没交
统计 Python 2026 春季班 的作业提交率
```

### 学生、成绩、学习进度

```text
查张三在 Python 数据分析基础 这门课里的成绩
查张三最近30天的学习进度
统计 Python 2026 春季班 的平均学习进度
查我的学习进度
```

### 考试

```text
查 Python 第一次测验 的考试安排
查张三最近一次考试记录
查本周的考试
```

## 重名消歧

当查询需要明确学生、课程，但召回多个候选时，工具不会直接猜测，会返回候选列表，例如：

```text
匹配到多个学生，请补充学号、手机号或更完整姓名后重试：
用户ID=8；用户名=zhangsan；昵称=张三；手机号=...
用户ID=19；用户名=zhangsan2；昵称=张三；手机号=...
```

后续用户可以继续说：

```text
查手机号 13800000000 的张三在 Python 数据分析基础 里的成绩
```

## 时间解析范围

当前内置解析：

- `今天`、`昨天`、`明天`
- `本周`、`上周`、`下周`、`下周五`
- `本月`、`上月`、`下月`
- `今年`、`去年`
- `最近7天`、`近30天`、`过去90天`
- `2026-07-01`、`2026/07/01`、`2026年7月1日`
- 两个显式日期组成的范围，例如 `2026-07-01 到 2026-07-31`

暂未接入 Duckling，因此“下周五晚上十二点”会先按“下周五”整天处理，不会精确到 24:00。

## 当前边界

该工具是读工具，不执行写操作。创建、修改、删除仍走现有 `*PlanTool -> pendingAction -> confirm -> ActionExecutor` 链路。

当前模糊查询是数据库 `LIKE` 召回加关系过滤，没有接入全文索引、拼音、同义词或向量检索。后续如果要更强的“全向模糊查”，建议继续补：

1. 实体别名表，例如课程别名、学生别名、班级简称。
2. MySQL FULLTEXT 或 Elasticsearch。
3. 拼音/首字母检索。
4. 更细的结构化 QuerySpec，支持数值范围、排序、分页和聚合字段。
5. 多轮消歧状态，把用户选择的候选绑定到当前 Agent 会话。

## 回归测试指令

```text
查一下张老师负责的 Python 课程
查 Python 数据分析基础 这门课下面有哪些章节和课时
统计 Python 数据分析基础 的章节数、课时数、教学班数和作业数
查 Python 2026 春季班 最近30天的作业
查 第一次 NumPy 练习 的提交情况
查 Python 数据分析基础 这门课里 第一次 NumPy 练习 哪些学生还没交
查张三在 Python 数据分析基础 这门课里的成绩
查张三最近30天的学习进度
查我的学习进度
查本周的考试
```
