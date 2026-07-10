# Agent 使用说明

## 1. 整体调用链

Agent 聊天不是只把用户问题直接发给大模型。当前实现会先读取 Agent 定义，再按配置补充知识库资料和平台工具数据，最后一起进入模型 Prompt。

```text
用户请求 /api/agent/sessions/{sessionId}/chat
  -> AgentChatService.ensureSession()
  -> AgentChatService.ensureAgentDefinition()
  -> AgentActionIntentRouter.buildDirectActionReply()
      -> 清晰写操作先生成待确认计划
  -> AgentPromptAssembler.buildPrompt()
      -> KnowledgeContextProvider.retrieve()
      -> AgentToolExecutor.buildToolContext()
  -> ChatModelClient.call()
      -> AgentModelDiagnostics 记录失败类别
```

作用：说明普通非流式聊天会走完整的上下文构造流程。

代码含义：`knowledgeBaseIds` 控制是否检索知识库，`enabledTools` 控制是否读取课程、作业、学习进度和学习计划数据。

## 2. 模型配置和健康检查

模型配置不要硬编码 Key，使用环境变量注入：

```powershell
$env:AI_OPENAI_BASE_URL="https://api.deepseek.com"
$env:AI_OPENAI_API_KEY="[REDACTED]"
$env:AI_OPENAI_MODEL="deepseek-v4-flash"
```

应用启动后先调用健康检查接口：

```http
GET /api/agent/model/health
Authorization: Bearer <token>
```

成功时 `data.healthy=true`，失败时 `data.diagnostics` 会返回：

- `provider`：当前模型供应商，例如 `deepseek`。
- `model`：当前配置模型。
- `category`：失败类别，例如 `AUTHENTICATION`、`INVALID_REQUEST`、`RATE_LIMIT`、`TIMEOUT`、`NETWORK`、`PROVIDER_ERROR`。
- `rootMessage`：脱敏后的底层错误信息。
- `retryable`：是否适合重试。

聊天接口仍然会给用户返回安全文案 `AI 回复失败，请稍后重试。`，但接口 `data.diagnostics` 和后端日志会保留可排查信息。

## 3. Agent 配置字段

`knowledgeBaseIds` 和 `enabledTools` 都必须传 JSON 数组字符串，后端会在创建和修改 Agent 时校验并规范化保存。

```json
{
  "knowledgeBaseIds": "[\"kb-java\",\"kb-homework\"]",
  "enabledTools": "[\"course_search\",\"homework_search\",\"learning_progress\",\"learning_plan\"]"
}
```

作用：创建或修改 Agent 时启用知识库检索和工具调用。

代码含义：

- `knowledgeBaseIds` 是字符串字段，但字符串内容必须是 JSON 数组。
- `enabledTools` 也是字符串字段，数组里的每一项是工具名。
- 保存后聊天时再由 `AgentConfigurationParser` 解析成 `List<String>`。

非法示例：

```json
{
  "knowledgeBaseIds": "kb-java",
  "enabledTools": "{\"tool\":\"course_search\"}"
}
```

非法值会返回：

- `knowledgeBaseIds 必须是 JSON 数组`
- `enabledTools 必须是 JSON 数组`

## 4. Agent 定义接口

### 3.1 创建 Agent

```http
POST /api/agent/definitions
Authorization: Bearer <admin token>
Content-Type: application/json
```

```json
{
  "code": "student_tool_agent",
  "name": "学生工具助手",
  "description": "测试课程、作业、学习进度和学习计划工具",
  "systemPrompt": "你是一个智能教学管理平台助手，回答要结合平台数据，简洁准确。",
  "model": "deepseek-v4-flash",
  "temperature": 0.7,
  "knowledgeBaseIds": "[]",
  "enabledTools": "[\"course_search\",\"homework_search\",\"learning_progress\",\"learning_plan\"]",
  "status": 1
}
```

作用：新增一个可用于聊天的 Agent。

代码含义：

- `Authorization` 必须是管理员 token，普通学生或教师会返回 `code=403`。
- `knowledgeBaseIds` 为空数组时不检索知识库。
- `enabledTools` 配置了四个工具，聊天时会把工具结果追加到 Prompt。
- `status=1` 表示启用，停用后不能创建新会话或继续聊天。

### 3.2 修改 Agent

```http
PUT /api/agent/definitions/{agentId}
Authorization: Bearer <admin token>
Content-Type: application/json
```

```json
{
  "code": "student_tool_agent",
  "name": "学生工具助手",
  "systemPrompt": "你是一个智能教学管理平台助手，回答要优先结合知识库和平台数据。",
  "model": "deepseek-v4-flash",
  "temperature": 0.6,
  "knowledgeBaseIds": "[\"kb-java\"]",
  "enabledTools": "[\"course_search\",\"learning_plan\"]",
  "status": 1
}
```

作用：更新 Agent 的提示词、模型参数、知识库和工具配置。

代码含义：这次配置只启用 `kb-java` 知识库、课程查询工具和学习计划工具。

### 3.3 启用或停用 Agent

```http
PUT /api/agent/definitions/{agentId}/status?status=0
Authorization: Bearer <admin token>
```

作用：停用 Agent。

代码含义：`status=0` 停用，`status=1` 启用。

## 5. 知识库检索

### 4.1 Agent 聊天里的知识库入口

Agent 聊天内部调用的是 `AgentChatService.retrieveKnowledgeContext()`。

```java
Result result = weKnoraService.search(
        new WeKnoraSearchRequest(knowledgeBaseIds, currentUserContent.trim())
);
```

作用：用当前用户问题作为检索词，在 Agent 绑定的知识库里查资料。

代码含义：

- `knowledgeBaseIds` 来自 Agent 定义里的 `knowledgeBaseIds`。
- `currentUserContent.trim()` 是用户本轮问题。
- 检索失败不会中断聊天，只会跳过知识库上下文。

### 4.2 项目内调试接口

```http
POST /api/weknora/search
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "knowledgeBaseIds": ["kb-java"],
  "query": "Java 多态是什么意思"
}
```

作用：不经过 Agent，直接测试 WeKnora 知识库检索是否可用。

代码含义：

- `knowledgeBaseIds` 是真正的 JSON 数组，不是字符串。
- `query` 是检索词。
- 该接口由 `WeKnoraController.search()` 转发到 `WeKnoraService.search()`。

### 4.3 WeKnora 实际外部接口

```java
Map<String, Object> body = new LinkedHashMap<>();
body.put("query", request.getQuery());
body.put("knowledge_base_ids", request.getKnowledgeBaseIds());

String resp = post("/api/v1/knowledge-search", body);
```

作用：后端服务代理调用 WeKnora 的真实检索接口。

代码含义：

- 平台对外暴露的是 `/api/weknora/search`。
- 服务内部实际请求 WeKnora：`{WEKNORA_BASE_URL}/api/v1/knowledge-search`。
- 请求头会带 `X-API-Key`，配置来自 `application.yml` 的 `weknora.api-key`。

### 4.4 知识库管理接口

列出知识库：

```http
GET /api/weknora/knowledge-bases
Authorization: Bearer <token>
```

作用：查看可用知识库 ID，拿到后填入 Agent 的 `knowledgeBaseIds`。

创建知识库：

```http
POST /api/weknora/knowledge-bases
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "name": "Java 课程资料",
  "description": "Java 基础、面向对象、集合和并发资料"
}
```

作用：新建 WeKnora 知识库。

上传文件：

```http
POST /api/weknora/knowledge-bases/{kbId}/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

```text
file: 课程资料.pdf
```

作用：把课程资料上传到指定知识库，后续 Agent 才能检索到。

### 4.5 给 Agent 上传 Markdown 文档

如果你要把 `.md` 文档直接交给某个 Agent 使用，优先使用 Agent 专属上传接口。这个接口会做两件事：先把 Markdown 文件上传到指定 WeKnora 知识库，再把该知识库 ID 自动写入 Agent 的 `knowledgeBaseIds`。

```http
POST /api/agent/definitions/{agentId}/knowledge-documents
Authorization: Bearer <admin token>
Content-Type: multipart/form-data
```

```text
knowledgeBaseId: kb-java
file: howToUseAgent.md
```

作用：上传 Markdown 文档并绑定到 Agent，后续该 Agent 聊天时会自动检索这份文档所在知识库。

代码含义：

- `{agentId}` 是要绑定文档的 Agent ID。
- `knowledgeBaseId` 是 WeKnora 知识库 ID；如果 Agent 原来没有绑定这个 ID，后端会追加到 `knowledgeBaseIds`。
- `file` 只允许 `.md` 或 `.markdown` 文件。
- 该接口属于 Agent 定义维护能力，只允许 `admin` 调用。
- 如果同一个 `knowledgeBaseId` 已经绑定过，文件仍会上传，但不会重复追加 ID。

服务层核心逻辑：

```java
Result uploadResult = weKnoraService.uploadFile(kbId, file.getBytes(), fileName);
boolean attached = attachKnowledgeBase(agent, kbId);
```

作用：先调用 WeKnora 上传文件，再更新 Agent 的知识库配置。

代码含义：

- `uploadFile()` 实际调用 WeKnora 文件上传接口。
- `attachKnowledgeBase()` 会把 `kbId` 追加进 Agent 的 `knowledgeBaseIds` JSON 数组。
- 返回结果里的 `attached=true` 表示本次新增绑定，`attached=false` 表示之前已经绑定过。

返回示例：

```json
{
  "code": "200",
  "msg": "请求成功",
  "data": {
    "agentId": 1,
    "knowledgeBaseId": "kb-java",
    "fileName": "howToUseAgent.md",
    "attached": true,
    "knowledgeBaseIds": "[\"kb-java\"]",
    "uploadResult": {}
  }
}
```

### 4.6 知识库结果如何进入 Prompt

```java
prompt.append("\n\n知识库参考资料（优先基于资料回答；资料不足时说明无法从资料中确认）：\n")
        .append(knowledgeContext);
```

作用：把检索命中的知识库文本追加到系统提示词后面。

代码含义：模型回答时会优先参考这段资料；如果资料不足，应该说明无法从资料确认。

知识库结果会从这些字段中抽取文本：

```java
"content", "text", "answer", "chunk", "snippet", "summary", "title",
"pagecontent", "documentcontent", "data", "result", "results"
```

作用：兼容不同 WeKnora 返回结构。

代码含义：只要返回 JSON 中出现这些字段名，后端就会尝试把对应文本收集进 Prompt。

## 6. 工具调用总览

工具不是单独的 HTTP 调用入口，而是 Agent 聊天内部根据 `enabledTools` 自动执行。真正触发工具的是下面两个聊天接口。

普通聊天：

```http
POST /api/agent/sessions/{sessionId}/chat
Authorization: Bearer <student token>
Content-Type: application/json
```

```json
{
  "content": "我有哪些 Java 作业还没提交？帮我安排学习计划。"
}
```

作用：聊天时触发已启用工具，把平台数据拼入 Prompt。

代码含义：工具服务从 `UserContext` 读取当前登录用户，学生、教师、管理员看到的数据范围不同。

内部总入口：

```java
return agentToolExecutor.buildToolContext(
        enabledTools,
        currentUserContent.trim(),
        chatProperties.getToolContextMaxItems(),
        chatProperties.getToolContextMaxCharacters()
);
```

作用：按工具列表生成工具上下文。

代码含义：

- `enabledTools` 来自 Agent 定义。
- `currentUserContent` 用来提取搜索关键词。
- `toolContextMaxItems` 控制每个工具最多返回多少条。
- `toolContextMaxCharacters` 控制工具上下文最多占多少字符。

## 7. 工具一：course_search

### 6.1 启用配置

```json
{
  "enabledTools": "[\"course_search\"]"
}
```

作用：启用课程查询工具。

代码含义：聊天时会执行 `CourseSearchTool.execute()`。

兼容别名：

```text
course_query
```

### 6.2 内部接口

```java
private String buildCourseSearchSection(String userQuery, int maxItems) {
    Page<Course> page = new Page<>(1, maxItems);
    QueryWrapper<Course> query = new QueryWrapper<>();
    if (!applyCourseVisibility(query)) {
        return "没有可查询的课程范围。";
    }
    applyKeywordFilter(query, extractSearchTerms(userQuery),
            "title", "description", "teacher", "difficulty");
    query.orderByDesc("id");
    courseMapper.selectPage(page, query);
    ...
}
```

作用：查询当前用户可见范围内的课程。

代码含义：

- `applyCourseVisibility()` 先按角色限制数据范围。
- `extractSearchTerms(userQuery)` 从用户问题里提取关键词。
- 关键词会匹配 `title`、`description`、`teacher`、`difficulty`。
- 查询结果最多返回 `maxItems` 条。

### 6.3 可见范围

```text
student -> 查询自己所在教学班对应的课程
teacher -> 查询 teacher_id = 当前用户 ID 的课程
admin   -> 查询全部课程
其他角色 -> 只查询 status = 1 的课程
```

作用：避免学生或教师通过 Agent 看到不属于自己的数据。

### 6.4 输出到 Prompt 的内容

```text
课程ID=1；名称=Java 基础；教师=王老师；难度=入门；简介=...
```

作用：给模型提供课程列表和课程摘要。

### 6.5 辅助验证接口

```http
GET /courses?title=Java&pageNum=1&pageSize=5
Authorization: Bearer <token>
```

作用：直接查询课程列表，验证数据库里是否有课程数据。

```http
GET /courses/my?pageNum=1&pageSize=10
Authorization: Bearer <student token>
```

作用：查看当前学生自己的课程，用来验证 `course_search` 对学生的可见范围。

## 8. 工具二：homework_search

### 7.1 启用配置

```json
{
  "enabledTools": "[\"homework_search\"]"
}
```

作用：启用作业查询工具。

代码含义：聊天时会执行 `HomeworkSearchTool.execute()`。

兼容别名：

```text
homework_query
assignment_search
```

### 7.2 内部接口

```java
private String buildHomeworkSearchSection(String userQuery, int maxItems) {
    Page<Homework> page = new Page<>(1, maxItems);
    QueryWrapper<Homework> query = new QueryWrapper<>();
    if (!applyHomeworkVisibility(query)) {
        return "没有可查询的作业范围。";
    }
    applyKeywordFilter(query, extractSearchTerms(userQuery), "title", "content");
    query.orderByDesc("deadline").orderByDesc("id");
    homeworkMapper.selectPage(page, query);
    ...
}
```

作用：查询当前用户可见范围内的作业。

代码含义：

- `applyHomeworkVisibility()` 按角色限制作业范围。
- 关键词会匹配 `title` 和 `content`。
- 结果按截止时间倒序，再按 ID 倒序。

### 7.3 可见范围

```text
student -> 查询自己所在教学班的作业
teacher -> 查询 teacher_id = 当前用户 ID 的作业
admin   -> 查询全部作业
其他角色 -> 只查询 status = 1 的作业
```

作用：保证 Agent 返回的作业和当前用户身份一致。

### 7.4 学生提交情况

```java
query.eq("homework_id", homeworkId)
        .eq("student_id", user.getUserId())
        .orderByDesc("id");
```

作用：学生查询作业时附带自己的最新提交记录。

代码含义：

- 只查当前学生自己的提交。
- 如果没有提交，输出 `未提交`。
- 如果已提交，输出分数、批改状态和反馈。

### 7.5 输出到 Prompt 的内容

```text
作业ID=3；标题=Java 集合作业；教学班ID=2；截止时间=2026-07-10T23:59；
满分=100；提交情况=未提交；内容=...
```

作用：让模型能回答“有哪些作业、是否提交、什么时候截止”。

### 7.6 辅助验证接口

```http
GET /homeworks?title=Java&pageNum=1&pageSize=10
Authorization: Bearer <token>
```

作用：直接查询作业列表。

```http
GET /homeworks/{homeworkId}/submits
Authorization: Bearer <token>
```

作用：查看某个作业的提交列表。

```http
GET /homework-submits?homeworkId=3&studentId=4&pageNum=1&pageSize=10
Authorization: Bearer <token>
```

作用：按作业和学生查询提交记录，用来验证 Agent 输出的提交状态。

## 9. 工具三：learning_progress

### 8.1 启用配置

```json
{
  "enabledTools": "[\"learning_progress\"]"
}
```

作用：启用学生学习进度查询工具。

代码含义：聊天时会执行 `LearningProgressTool.execute()`。

兼容别名：

```text
progress_query
```

### 8.2 内部接口

```java
private String buildLearningProgressSection(int maxItems) {
    AuthUtils.LoginUser user = UserContext.get();
    if (!isStudent(user)) {
        return "学习进度工具仅返回学生本人的学习记录；当前用户没有可读取的个人学习进度。";
    }
    List<LearningProgress> progressList = listStudentProgress(user.getUserId(), maxItems);
    ...
}
```

作用：读取当前学生自己的学习进度。

代码含义：

- 只有 `student` 角色会返回个人学习记录。
- 教师、管理员调用时不会读取某个学生的隐私进度。
- `maxItems` 控制最多取几条进度记录。

### 8.3 查询规则

```java
query.eq("student_id", studentId)
        .orderByDesc("last_learn_time")
        .orderByDesc("update_time")
        .orderByDesc("id");
```

作用：按当前学生 ID 查询最近学习记录。

代码含义：

- 只查 `student_id = 当前用户 ID`。
- 优先显示最近学习过的课时。

### 8.4 输出到 Prompt 的内容

```text
课时ID=8；课时=Java 多态；教学班ID=2；进度=45%；已学秒数=900；最近学习=2026-07-05T12:00
```

作用：让模型能回答“我学到哪里了、哪些课时没完成”。

### 8.5 辅助验证接口

```http
GET /learning-progress?studentId=4&pageNum=1&pageSize=10
Authorization: Bearer <token>
```

作用：直接查看学生学习进度数据。

代码含义：`studentId=4` 是默认测试数据里的 `student` 用户 ID，实际以数据库为准。

## 10. 工具四：learning_plan

### 9.1 启用配置

```json
{
  "enabledTools": "[\"learning_plan\"]"
}
```

作用：启用学习计划生成工具。

代码含义：聊天时会执行 `LearningPlanTool.execute()`。

兼容别名：

```text
study_plan
```

### 9.2 内部接口

```java
private String buildLearningPlanSection(int maxItems) {
    AuthUtils.LoginUser user = UserContext.get();
    if (!isStudent(user)) {
        return "学习计划工具需要学生账号的个人学习进度；当前用户没有可生成计划的个人进度。";
    }
    List<LearningProgress> progressList = listStudentProgress(user.getUserId(), maxItems);
    ...
}
```

作用：基于学生学习进度生成学习计划摘要。

代码含义：

- 只使用当前学生自己的进度。
- 没有进度时返回默认建议。
- 有进度时统计平均进度、未完成课时数和低于 60% 的薄弱课时。

### 9.3 计划生成规则

```java
if (value < 100) {
    unfinishedCount++;
}
if (value < 60) {
    weakLessons.add(lessonTitle + "(" + value + "%)");
}
```

作用：识别未完成课时和薄弱课时。

代码含义：

- 小于 `100%` 计入未完成。
- 小于 `60%` 计入优先复习。

### 9.4 输出到 Prompt 的内容

```text
学习记录数=5；平均进度=62%；未完成课时数=3；优先复习=Java 多态(45%)；
建议：先补齐低于 60% 的课时，再完成未达 100% 的课时；每天安排 30-45 分钟学习，完成后及时提交相关作业。
```

作用：让模型基于平台数据给出更具体的学习安排。

### 9.5 辅助验证接口

```http
GET /learning-progress?studentId=4&pageNum=1&pageSize=20
Authorization: Bearer <token>
```

作用：学习计划工具没有单独 HTTP 接口，可以通过学习进度接口验证它的数据来源。

## 11. 创建会话并触发知识库和工具

### 10.1 创建会话

```http
POST /api/agent/sessions
Authorization: Bearer <student token>
Content-Type: application/json
```

```json
{
  "agentId": 1,
  "title": "Java 学习咨询"
}
```

作用：给当前学生创建一个绑定 Agent 的聊天会话。

代码含义：

- `agentId` 指向已经创建并启用的 Agent。
- 会话会绑定当前登录用户。
- 后续聊天会按这个 Agent 的 `knowledgeBaseIds` 和 `enabledTools` 执行。

### 10.2 普通聊天

```http
POST /api/agent/sessions/{sessionId}/chat
Authorization: Bearer <student token>
Content-Type: application/json
```

```json
{
  "content": "我 Java 课程学到哪里了？有哪些作业没提交？请结合资料给我一个学习计划。"
}
```

作用：触发知识库检索和所有已启用工具，返回完整回答。

代码含义：如果 Agent 配置了知识库和工具，这个问题会同时触发 `knowledgeBaseIds` 检索、`course_search`、`homework_search`、`learning_progress` 和 `learning_plan`。

## 12. 上下文规模配置

```yaml
agent:
  chat:
    knowledge-context-max-snippets: 5
    knowledge-context-max-characters: 4000
    tool-context-max-items: 5
    tool-context-max-characters: 4000
```

作用：限制进入 Prompt 的知识库和工具上下文大小。

代码含义：

- `knowledge-context-max-snippets`：最多抽取多少条知识库文本片段。
- `knowledge-context-max-characters`：知识库上下文最多多少字符。
- `tool-context-max-items`：每个工具最多查询多少条数据。
- `tool-context-max-characters`：工具上下文最多多少字符。

## 13. 推荐 Apifox 验证顺序

1. 管理员登录，获取 `admin token`。
2. 调用 `GET /api/weknora/knowledge-bases`，确认知识库 ID。
3. 如需知识库资料，调用 `POST /api/weknora/knowledge-bases/{kbId}/upload` 上传文件。
4. 如果是给指定 Agent 上传 Markdown，调用 `POST /api/agent/definitions/{agentId}/knowledge-documents`，上传 `.md` 并自动绑定知识库。
5. 调用 `POST /api/weknora/search`，确认知识库能检索到内容。
6. 用管理员 token 调用 `POST /api/agent/definitions`，创建带 `knowledgeBaseIds` 和 `enabledTools` 的 Agent。
7. 学生登录，获取 `student token`。
8. 用学生 token 调用 `POST /api/agent/sessions` 创建会话。
9. 调用 `POST /api/agent/sessions/{sessionId}/chat` 测普通非流式聊天。
10. 如工具结果不符合预期，分别用 `/courses`、`/homeworks`、`/homework-submits`、`/learning-progress` 检查源数据。
