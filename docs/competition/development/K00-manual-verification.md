# K00 人工验证方法

更新：2026-10-22。本文件验证运行模型契约、图片 OCR 接口和基础向量入库检索。扫描 PDF 的逐页 OCR 管线及教师学生完整流程仍按后续任务实施；10月20日历史验收结果保留在原报告。

## 1. 模型配置

程序读取进程环境变量。不要把真实密钥写入本文件、提交记录或截图。

| 能力 | 推荐变量 | 兼容及继承规则 |
| --- | --- | --- |
| 生成（Agent、备课、出题、RAG 问答） | `AI_GENERATION_BASE_URL`、`AI_GENERATION_API_KEY`、`AI_GENERATION_MODEL` | 每个字段依次取对应 `AI_GENERATION_*`、旧 `AI_OPENAI_*`；地址和密钥最后兼容 `BASE_URL`、`API_KEY`。模型名必须明确提供。 |
| 规划（规划器、参数提取、修复、动作选择） | `AI_PLANNING_BASE_URL`、`AI_PLANNING_API_KEY`、`AI_PLANNING_MODEL` | 未设置的字段明确继承生成配置。模型还兼容 `AI_AGENT_PLANNER_MODEL`。失败后不切换供应商。 |
| Embedding | `RAG_EMBEDDING_ENABLED`、`RAG_EMBEDDING_BASE_URL`、`RAG_EMBEDDING_API_KEY`、`RAG_EMBEDDING_MODEL` | 密钥兼容 `RAG_EMBEDDING_APIKEY`，最后取 `SILICONFLOW_API_KEY`；独立于生成。选用 `Qwen/Qwen3-VL-Embedding-8B`，保留原生4096维。 |
| OCR | `OCR_BASE_URL`、`OCR_API_KEY`、`OCR_MODEL` | 密钥兼容 `OCR_APIKEY`，最后取 `SILICONFLOW_API_KEY`；选用 `deepseek-ai/DeepSeek-OCR`，已实现 PNG/JPEG HTTP 适配，PDF 管线待 K02。 |

地址填写兼容接口的基础地址，可包含 `/v1`，不填写 `/chat/completions`。支持 HTTPS；本机开发服务可用 loopback HTTP。需要密钥字段的本地免鉴权服务可填开发用占位值，不能将其误当成已验证鉴权能力。

独立超时变量：`AI_GENERATION_TIMEOUT_SECONDS`（默认30）、`AI_PLANNING_TIMEOUT_SECONDS`（默认10）、`RAG_EMBEDDING_TIMEOUT_SECONDS`（默认30）、`OCR_TIMEOUT_SECONDS`（默认60）。流式调用按整个调用时间截止。上下文窗口元数据 `AI_GENERATION_CONTEXT_WINDOW_TOKENS` / `AI_PLANNING_CONTEXT_WINDOW_TOKENS` 默认为0，表示未知；这是操作者声明，实际探测只证明指定长度样本。

如现有环境变量可用，无需重新填写。如果需要补充配置，将 `.env.example` 复制为已忽略的 `.env.local`，只在本机填入所选非 GPT 服务；`scripts/competition/start-local.ps1` 会加载它。该脚本先将缺失的 Embedding/OCR 变量从 Windows 用户环境读入，再从私有文件补缺；优先级为已有进程值、用户环境值、私有文件。修改后重启后端；若旧终端仍有旧值，应使用新进程或清除该进程旧变量。空私有配置行会忽略。

## 2. 启动和管理员登录

在项目根目录的 PowerShell 中构建并启动隔离演示环境：

```powershell
Set-Location D:\ai-learning\ai-learning-backend
mvn -DskipTests package
Set-Location D:\ai-learning
.\scripts\competition\start-local.ps1 -SkipBuild
```

该脚本默认使用 `contest-demo`，不要添加 `-UseMySql`。若8080已被另一个后端占用，先结束自己此前启动的演示进程；不要结束不明进程。测试本身使用独立内存 H2，不会修改正式 MySQL。

另开一个 PowerShell，输入你自己的管理员账号，登录结果中的令牌仅保存在变量里：

```powershell
$adminCredential = Get-Credential -Message '输入平台管理员账号'
$loginBody = @{
  username = $adminCredential.UserName
  password = $adminCredential.GetNetworkCredential().Password
} | ConvertTo-Json
$loginResult = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/auth/login' `
  -ContentType 'application/json' -Body $loginBody
if ($loginResult.code -ne '200') { throw '管理员登录失败，请检查本地演示账号。' }
$modelHeaders = @{ Authorization = 'Bearer ' + $loginResult.data.token }
```

## 3. 只读检查

```powershell
$modelStatus = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/admin/models/status' -Headers $modelHeaders
$modelStatus.data | Format-Table role,configuration,model,adapterImplemented
```

预期返回 generation、planning、embedding、ocr 四项；没有地址、密钥或供应商响应正文。此 GET 不产生模型请求。

| 状态 | 判断 |
| --- | --- |
| `CONFIGURED_UNVERIFIED` | 配置通过本地检查，还没有证明服务或能力正常。 |
| `MODEL_NOT_CONFIGURED` | 地址、密钥或模型名缺失；其余教务功能仍能启动。 |
| `MODEL_DISABLED` | 该能力未启用，例如默认 Embedding。 |
| `MODEL_BLOCKED` | 明确的 GPT/OpenAI 模型名、请求覆盖或官方服务地址被拒绝。 |
| `MODEL_ENDPOINT_INVALID` | 地址格式或协议不符合配置约定。 |

匿名请求应为401，教师或学生请求应为403。旧 `/api/agent/model/health` 也改为管理员权限，且仍会实际调用模型；日常查看配置使用新状态接口。

## 4. 显式能力探测

每次 POST 可能产生模型费用，生成和规划各最多4次请求，仅发送合成测试文本，不发送课程 PDF。若普通响应探测失败，后3项跳过。

```powershell
$generationProbe = Invoke-RestMethod -Method Post `
  -Uri 'http://127.0.0.1:8080/api/admin/models/generation/probe' -Headers $modelHeaders
$generationProbe.data | ConvertTo-Json -Depth 8
$planningProbe = Invoke-RestMethod -Method Post `
  -Uri 'http://127.0.0.1:8080/api/admin/models/planning/probe' -Headers $modelHeaders
$planningProbe.data | ConvertTo-Json -Depth 8
```

逐项检查 `TEXT`、`JSON_OBJECT`、`STREAM`、`CONTEXT_SAMPLE` 的 `PASS` / `FAIL` / `SKIPPED`。HTTP200仅表示探测已执行，不表示模型通过；必须读 `data.status` 和 `checks`。上下文探测约2432字符，只验证该样本的标识提取，不证明最大窗口或教学质量。

未配置接口可先跳过真实探测；填入接口并重启后，重新执行本节。401/403/429、超时、格式不支持等只报告安全错误码，不会用 GPT 或另一供应商补答。上下文样本失败保留记录后再复验，不能改掉结果当作通过。

继续验证 Embedding 与 OCR：

```powershell
foreach ($modelRole in @('embedding','ocr')) {
  $probeResult = Invoke-RestMethod -Method Post `
    -Uri "http://127.0.0.1:8080/api/admin/models/$modelRole/probe" -Headers $modelHeaders
  $probeResult.data | ConvertTo-Json -Depth 8
}
```

Embedding 探针发送两条不同合成文本，检查批量数量、维度和向量差异。OCR 探针只发送本机生成的 `OCR TEST 2026` PNG，检查识别文本。配置缺失仍跳过，实际调用失败为 FAIL；通过不等于 K02/K03 整体质量已验收。首次启动旧 MySQL 库时，只对已有的 `native_rag_chunk.vector_json TEXT` 列拓宽为 LONGTEXT，保留数据；H2 与已足够宽的列不修改。本轮未连接或变更实际 MySQL 库。

## 5. 重跑自动验证

```powershell
Set-Location D:\ai-learning\ai-learning-backend
mvn test
```

普通测试只调用 loopback 假服务或内存对象；五项真实模型测试默认跳过。要单独探测进程环境中的真实接口：

```powershell
$previousLiveFlag = $env:K00_LIVE_MODEL_CHECK
try {
  $env:K00_LIVE_MODEL_CHECK = 'true'
  mvn '-Dtest=RuntimeModelLiveTest' test
} finally {
  $env:K00_LIVE_MODEL_CHECK = $previousLiveFlag
}
```

此 Maven 自动真实探测不自动加载用户环境和 `.env.local`，需先在该终端配置完整的四角色环境。结果在后端 `target/k00-live/`：`generation.json`、`planning.json`、`embedding.json`、`ocr.json`、`native-rag.json` 和脱敏 `status.json`。显式开启后，真实调用失败或配置不足会让测试失败，不再转为跳过。RAG 用独立 H2 和临时目录，验证合成文本的真实向量入库与检索，不写正式数据库。2026-10-22的复验结果见 [接口验证报告](API-verification-20261022.md)。

## 6. K00 人工验收记录

- [ ] 管理员看见四角色状态；匿名、教师、学生被拒绝。
- [ ] 生成和规划配置分别生效；没有 SDK 隐式 GPT 默认模型。
- [ ] 普通响应、JSON、流式及上下文样本分别记录结果。
- [ ] 生成/规划接口不可用时明确失败，不静默切换。
- [ ] Agent 的旧模型覆盖若是 GPT，修订为空（继承生成）或所选非 GPT 模型；系统会拒绝原 GPT 覆盖，不会偷偷替换。
- [ ] OCR 合成图片识别通过；扫描 PDF 逐页 OCR 仍按 K02 单独验收。
- [ ] Embedding 批量请求通过，原生4096维向量可保存并用于检索。
- [ ] 真实模型检查与本地协议测试分开记录；本轮没有宣称课程问答质量通过。
