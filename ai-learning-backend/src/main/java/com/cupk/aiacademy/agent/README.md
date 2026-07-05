# Agent 模块接口说明

本文档说明当前 `agent` 模块已经提供的接口、请求字段和推荐调用流程，便于前端或其他后端同学接入。

## 1. 使用前准备

### 1.1 登录并携带 Token

除 `/auth/login`、`/auth/register` 外，当前项目接口都会经过全局鉴权拦截器。调用 Agent 接口前先登录：

```http
POST /auth/login
Content-Type: application/json

{
  "username": "student",
  "password": "[REDACTED]"
}
```

登录成功后从 `data.token` 取出 token，后续请求都带上：

```http
Authorization: Bearer <token>
Content-Type: application/json
```

流式接口额外建议带：

```http
Accept: text/event-stream
```

注意：普通 JSON 接口不要加 `Accept: text/event-stream`，否则可能因为响应类型不匹配返回 `406`。

### 1.2 统一响应格式

Agent 模块成功和失败都使用 `Result` 结构：

```json
{
  "code": "200",
  "msg": "请求成功",
  "data": {}
}
```

常见错误：

```json
{
  "code": "400",
  "msg": "消息内容不能为空"
}
```

模型调用失败时会返回 `500`，并尽量返回已经落库的消息 ID：

```json
{
  "code": "500",
  "msg": "AI 回复失败，请稍后重试。",
  "data": {
    "sessionId": 1,
    "userMessageId": 10,
    "assistantMessageId": 11
  }
}
```

## 2. 推荐调用流程

1. 登录获取 token。
2. 调用 `GET /api/agent/definitions?status=1` 获取可用 Agent。
3. 调用 `POST /api/agent/sessions` 创建会话。
4. 调用 `POST /api/agent/sessions/{sessionId}/chat` 普通聊天，或调用 `POST /api/agent/sessions/{sessionId}/stream` 流式聊天。
5. 调用 `GET /api/agent/sessions/{sessionId}/messages` 拉取历史消息。
6. 需要时调用重命名或删除会话接口。

## 3. Agent 定义管理接口

Agent 定义保存在 `t_agent` 表中。当前聊天逻辑会使用以下字段：

- `systemPrompt`：Agent 系统提示词。
- `model`：模型名称。
- `temperature`：模型温度，范围 `0.00` 到 `2.00`。
- `status`：`1` 表示启用，`0` 表示停用。


### 3.1 查询 Agent 列表

```http
GET /api/agent/definitions?keyword=教学&status=1
Authorization: Bearer <token>
```

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `keyword` | 否 | 按 `code` 或 `name` 模糊查询 |
| `status` | 否 | `1` 启用，`0` 停用 |

### 3.2 查询 Agent 详情

```http
GET /api/agent/definitions/{agentId}
Authorization: Bearer <token>
```

### 3.3 新建 Agent

```http
POST /api/agent/definitions
Content-Type: application/json

{
  "code": "teaching_assistant",
  "name": "教学助手",
  "description": "回答课程、作业和学习规划相关问题",
  "systemPrompt": "你是一个智能教学管理平台助手，回答要准确、简洁。",
  "model": "gpt-4o-mini",
  "temperature": 0.7,
  "knowledgeBaseIds": "[1,2]",
  "enabledTools": "[\"course_search\"]",
  "status": 1
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `code` | 是 | Agent 编码，最大 64 字，不能重复 |
| `name` | 是 | Agent 名称，最大 100 字 |
| `description` | 否 | 描述，最大 255 字 |
| `systemPrompt` | 否 | 系统提示词 |
| `model` | 否 | 模型名称，最大 100 字 |
| `temperature` | 否 | 温度，范围 `0.00` 到 `2.00` |
| `status` | 否 | `1` 启用，`0` 停用，默认启用 |

### 3.4 修改 Agent

```http
PUT /api/agent/definitions/{agentId}
Content-Type: application/json

{
  "code": "teaching_assistant",
  "name": "教学助手",
  "description": "更新后的描述",
  "systemPrompt": "你是一个智能教学管理平台助手。",
  "model": "gpt-4o-mini",
  "temperature": 0.6,
  "knowledgeBaseIds": "[1]",
  "enabledTools": "[]",
  "status": 1
}
```

### 3.5 启用或停用 Agent

```http
PUT /api/agent/definitions/{agentId}/status?status=0
```

`status=0` 表示停用，`status=1` 表示启用。停用后的 Agent 不能再创建新会话或继续聊天。

## 4. 会话接口

### 4.1 创建会话

```http
POST /api/agent/sessions
Authorization: Bearer <token>
Content-Type: application/json

{
  "agentId": 2,
  "title": "高数学习计划"
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `agentId` | 否 | 绑定的 Agent ID，不传默认使用 `1` |
| `title` | 否 | 会话标题，最大 100 字，不传默认 `新会话` |
| `userId` | 否 | 正常接口调用不用传，系统会从 token 获取当前用户 |

返回 `data` 是 `AgentSession`：

```json
{
  "id": 1,
  "agentId": 2,
  "userId": 3,
  "title": "高数学习计划",
  "status": 1,
  "createTime": "2026-07-05T10:00:00",
  "updateTime": "2026-07-05T10:00:00"
}
```

### 4.2 查询当前用户会话列表

```http
GET /api/agent/sessions
Authorization: Bearer <token>
```

已登录时优先使用 token 中的用户 ID。没有登录上下文的内部调用才会使用可选参数 `userId`。

```http
GET /api/agent/sessions?userId=3
```

### 4.3 修改会话标题

```http
PUT /api/agent/sessions/{sessionId}
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "新的会话标题"
}
```

只能修改当前登录用户自己的会话。

### 4.4 删除会话

```http
DELETE /api/agent/sessions/{sessionId}
Authorization: Bearer <token>
```

这里是软删除：会话 `status` 会变为 `0`，消息记录仍保留在数据库中。

### 4.5 查询会话消息

```http
GET /api/agent/sessions/{sessionId}/messages
Authorization: Bearer <token>
```

返回消息结构：

```json
{
  "id": 10,
  "sessionId": 1,
  "role": "user",
  "content": "帮我制定高数学习计划",
  "createTime": "2026-07-05T10:01:00"
}
```

`role` 当前主要有：

- `user`：用户消息。
- `assistant`：AI 回复消息。

## 5. 聊天接口

### 5.1 会话普通聊天

```http
POST /api/agent/sessions/{sessionId}/chat
Authorization: Bearer <token>
Content-Type: application/json

{
  "content": "帮我制定一周高数复习计划"
}
```

字段约束：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `content` | 是 | 用户消息，不能为空，最大 4000 字 |

返回 `data`：

```json
{
  "sessionId": 1,
  "userMessageId": 10,
  "assistantMessageId": 11,
  "content": "可以按每天 2 小时安排...",
  "createTime": "2026-07-05T10:02:00"
}
```

说明：

- 普通聊天会把用户消息和 AI 回复都写入 `t_agent_message`。
- 如果会话标题还是 `新会话`，第一次聊天会自动用用户消息前 24 个字符生成标题。
- Prompt 会带上最近历史上下文，默认最多 `30` 条、`12000` 字符。
- 会话绑定的 Agent 必须存在且 `status=1`。

### 5.2 会话流式聊天

```http
POST /api/agent/sessions/{sessionId}/stream
Authorization: Bearer <token>
Content-Type: application/json
Accept: text/event-stream

{
  "content": "请流式回答：什么是机器学习？"
}
```

SSE 事件类型：

| 事件名 | data 内容 | 说明 |
| --- | --- | --- |
| `message` | 文本片段 | 模型增量输出 |
| `complete` | `AgentChatResponse` JSON | 模型回复结束，返回完整回复和消息 ID |
| `error` | `Result` JSON | 模型失败或服务端异常 |

事件示例：

```text
event: message
data: 机器学习

event: message
data: 是人工智能的一个分支

event: complete
data: {"sessionId":1,"userMessageId":10,"assistantMessageId":11,"content":"机器学习是人工智能的一个分支...","createTime":"2026-07-05T10:02:00"}
```

Apifox 测试时选择可以接收 SSE 的请求方式，并确认请求头中有 `Accept: text/event-stream`。

### 5.3 单轮聊天

```http
POST /api/agent/chat-once
Authorization: Bearer <token>
Content-Type: application/json

{
  "content": "介绍一下这个平台"
}
```

说明：

- 该接口不创建会话，不写入消息表。
- 该接口使用固定系统提示词，不读取 `t_agent` 配置。
- 适合简单调试或无需历史上下文的场景。

### 5.4 早期调试接口

```http
GET /chat?message=你好
Authorization: Bearer <token>
```

该接口已标记为 `Deprecated`，只保留给早期本地调试兼容。正式功能请使用 `/api/agent/chat-once` 或会话聊天接口。

## 6. Agent 聊天配置

当前 Agent 上下文裁剪有默认值，不配置也能运行：

- `historyMessageLimit` 默认 `30`。
- `historyMaxCharacters` 默认 `12000`。

如需在 `application.yml` 显式配置，可以添加：

```yaml
agent:
  chat:
    history-message-limit: ${AGENT_CHAT_HISTORY_MESSAGE_LIMIT:30}
    history-max-characters: ${AGENT_CHAT_HISTORY_MAX_CHARACTERS:12000}
```

## 7. 当前实现范围

已完成：

- Agent 专属异常处理，不影响全局异常处理模块。
- Agent 定义的增删查改中的查询、新建、修改、启停。
- 会话创建、查询、重命名、软删除。
- 会话消息查询。
- 普通聊天、单轮聊天、SSE 流式聊天。
- 按会话归属校验当前用户权限。
- 使用 Agent 的 `systemPrompt`、`model`、`temperature` 构造模型请求。
- 模型失败时记录失败回复，并返回失败消息 ID。
- 历史上下文按条数和字符数裁剪。

暂未完成：

- 前端页面接入。

## 8. 下一步建议

