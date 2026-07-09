# Agent 本地测试指南

这一步做完后可以开始测试。建议按“模型健康检查 -> 后端冒烟脚本 -> 前端手工验证”的顺序做，这样能区分是模型配置问题、后端 Agent 编排问题，还是前端入口问题。

## 1. 测试前准备

### 1.1 数据库

当前 Agent 写操作确认链路依赖这些表：

- `t_agent`
- `t_agent_session`
- `t_agent_message`
- `t_agent_pending_action`
- `t_operation_log`

如果是重新导入数据库，请使用已经包含 `t_agent_pending_action` 的脚本：

```powershell
D:\ai-learning\database\ai_academy.sql
```

或：

```powershell
D:\ai-learning\database\最新点这个.sql
```

### 1.2 模型配置

后端启动前建议配置真实模型参数：

```powershell
$env:AI_OPENAI_BASE_URL="https://api.deepseek.com"
$env:AI_OPENAI_API_KEY="[REDACTED]"
$env:AI_OPENAI_MODEL="deepseek-v4-flash"
```

如果没有配置模型，后面的“受控写操作计划”仍然可以测试，因为它走本地意图路由；但普通聊天和模型健康检查会失败。

### 1.3 启动后端

```powershell
cd D:\ai-learning\ai-learning-backend
.\mvnw.cmd spring-boot:run
```

默认后端地址是：

```text
http://localhost:8080
```

## 2. 一键冒烟测试脚本

脚本位置：

```text
D:\ai-learning\scripts\agent-smoke-test.mjs
```

要求 Node.js 18 或更高版本。

在新的 PowerShell 窗口执行：

```powershell
cd D:\ai-learning
node scripts/agent-smoke-test.mjs
```

默认账号：

```text
admin / 123456
```

脚本会做这些事：

1. 登录并取得 token。
2. 调用 `GET /api/agent/model/health` 打印模型健康状态。
3. 确保存在一个测试 Agent：`agent_smoke_test`，并启用 `notice_create_plan`。
4. 调用 `POST /api/agent/sessions` 创建 Agent 会话。
5. 调用 `POST /api/agent/sessions/{sessionId}/chat` 测普通非流式聊天。
6. 发送“发布通知”的自然语言请求，验证能生成待确认操作计划。
7. 从响应体 `data.pendingAction.actionId` 获取 `actionId`，验证 `t_agent_pending_action` 链路可用。
8. 调用 pending action 列表和详情接口，确认前端无需解析文本也能拿到 `actionId`。

默认不会真正执行写操作。看到下面这种结尾就说明基础链路通过：

```text
[PASS] Agent 冒烟测试完成：登录、模型健康检查、会话、非流式聊天、写操作计划、pending action 均已覆盖。
```

## 3. 常用测试命令

### 3.1 指定后端地址

```powershell
node scripts/agent-smoke-test.mjs --base-url http://localhost:8081
```

### 3.2 指定账号

```powershell
node scripts/agent-smoke-test.mjs --username admin --password 123456
```

### 3.3 要求模型必须可用

普通冒烟测试会把模型失败打印为 warning，然后继续测试本地写操作链路。要把模型失败视为整体验收失败，使用：

```powershell
node scripts/agent-smoke-test.mjs --require-model
```

### 3.4 真正确认执行一条测试通知

这个命令会调用 `POST /api/agent/actions/confirm`，写入一条通知，并写操作日志：

```powershell
node scripts/agent-smoke-test.mjs --confirm
```

执行后可以检查：

- `t_notice`：新增测试通知。
- `t_operation_log`：新增 `agent.notice.create` 操作日志。
- `t_agent_pending_action`：对应 `actionId` 被删除，避免重复执行。

### 3.5 课程 CRUD 模块冒烟测试

课程模块已经迁移到通用 `AgentActionSpec` + schema-first 参数抽取模型，可用独立脚本验证创建、修改、状态变更三条写计划：

```powershell
node scripts/agent-course-crud-smoke-test.mjs
```

默认只生成待确认计划和 pending action，不真正写课程表。需要真实执行“创建课程 -> 修改课程 -> 变更课程状态”时：

```powershell
node scripts/agent-course-crud-smoke-test.mjs --confirm --category-id 1
```

如果你的课程分类 ID 不是 `1`，把 `--category-id` 改成实际存在的分类 ID。执行后可检查：

- `t_course`：新增课程、课程标题被修改、课程状态被变更。
- `t_operation_log`：新增 `agent.course.create`、`agent.course.update`、`agent.course.status` 操作日志。
- `t_agent_pending_action`：已执行的 pending action 会被删除。
- pending action 详情：应包含 `parameterSchema`、`jsonSchema`、`missingRequiredFields` 和 `parameterExtraction`。如果模型返回非法参数，`parameterExtraction.validationIssues` 会显示类型或枚举错误。

### 3.6 自然语言样例批量测试

要批量测试“日常说法 -> 待确认操作计划”，使用：

```powershell
node scripts/agent-natural-language-samples.mjs
```

脚本会自动创建或更新测试 Agent：`agent_natural_language_samples`，并启用当前所有写操作计划工具。默认只生成 `pendingAction`，不会调用确认执行接口，因此不会真正写入课程、章节、作业、公告等业务表。

默认校验：

- 每条自然语言都必须生成 `pendingAction`。
- `pendingAction.actionCode` 必须符合预期。
- 正向样例不能有 `missingRequiredFields`。
- 缺参样例必须包含预期缺失字段。
- 参数值默认只打印 warning；需要参数值也严格匹配时，加 `--strict-params`。

常用命令：

```powershell
# 查看全部样例
node scripts/agent-natural-language-samples.mjs --list

# 只跑课程相关样例
node scripts/agent-natural-language-samples.mjs --case course

# 只跑某几个样例，并严格校验参数值
node scripts/agent-natural-language-samples.mjs --case chapter-create,notice-create --strict-params

# 模型健康检查失败时直接失败
node scripts/agent-natural-language-samples.mjs --require-model
```

注意：名称不存在、名称匹配多条这类测试，需要进入确认执行前的实体解析阶段才会暴露。当前脚本默认不确认执行，所以这类样例只验证是否能进入正确的待确认动作；后续可单独做带测试库数据的确认执行脚本。

## 4. 手工接口测试

### 4.1 登录

```powershell
$login = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/auth/login" `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"[REDACTED]"}'

$token = $login.data.token
$headers = @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" }
```

### 4.2 模型健康检查

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/agent/model/health" `
  -Headers $headers
```

如果 `healthy=false`，优先看返回里的 `provider`、`model`、`category`、`message`。

### 4.3 创建会话

```powershell
$session = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agent/sessions" `
  -Headers $headers `
  -Body '{"agentId":1,"title":"手工测试会话"}'

$sessionId = $session.data.id
```

### 4.4 普通非流式聊天

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agent/sessions/$sessionId/chat" `
  -Headers $headers `
  -Body '{"content":"请用一句话介绍你自己"}'
```

### 4.5 查询待确认操作

生成待确认计划后，可以先查当前登录用户的 pending action：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/agent/actions/pending" `
  -Headers $headers
```

查看详情：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/agent/actions/pending/<actionId>" `
  -Headers $headers
```

前端确认弹窗会优先读取聊天响应里的 `pendingAction`，旧历史消息才会回退到文本里的 `actionId=...`。

### 4.6 确认执行接口

先通过脚本或前端生成 `actionId`，再确认执行：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agent/actions/confirm" `
  -Headers $headers `
  -Body '{
    "actionId":"<actionId>",
    "actionCode":"notice.create",
    "confirmed":true,
    "parameters":{
      "title":"手工确认测试通知",
      "content":"这是一条手工确认执行的测试通知。",
      "noticeType":"system",
      "status":1
    }
  }'
```

## 5. 前端验证

启动前端：

```powershell
cd D:\ai-learning\ai-learning-frontend
npm run dev
```

浏览器登录后测试：

1. 打开 Agent 助手页面，或打开右下角 AI 浮窗。
2. 切换到 `Agent 智能助手`。
3. 输入：`请发布一条课程通知，标题是测试通知，内容是这是一条测试通知，先生成待确认操作计划。`
4. 期望看到“待确认操作”。
5. 点击“确认执行”，确认弹窗里 `actionId` 和 `actionCode` 应自动填入，不需要从回复文本复制。
6. 也可以点击聊天页顶部的“待确认动作”按钮，查看当前登录用户未确认的 pending action，并从列表进入确认弹窗。

## 6. 常见失败判断

| 现象 | 优先检查 |
| --- | --- |
| 登录失败 | 数据库是否导入、账号密码是否是 `admin / 123456` |
| 创建 Agent 返回 403 | 当前账号不是管理员 |
| `model/health` 不健康 | `AI_OPENAI_BASE_URL`、`AI_OPENAI_API_KEY`、`AI_OPENAI_MODEL` |
| 普通聊天失败但写操作计划成功 | 模型配置问题，Agent 本地编排链路基本可用 |
| 写操作计划没有 `pendingAction.actionId` | 测试 Agent 是否启用了 `notice_create_plan`，或自然语言是否包含“发布/创建通知”意图 |
| 确认执行失败 | `parameters` 是否包含 `title`、`content`，当前用户是否有教师或管理员权限 |

## 7. 推荐验收标准

最小可用标准：

```powershell
node scripts/agent-smoke-test.mjs
```

完整后端标准：

```powershell
node scripts/agent-smoke-test.mjs --require-model
node scripts/agent-smoke-test.mjs --confirm
```

完整项目标准：

```powershell
cd D:\ai-learning\ai-learning-backend
.\mvnw.cmd test

cd D:\ai-learning\ai-learning-frontend
npm run build
```
