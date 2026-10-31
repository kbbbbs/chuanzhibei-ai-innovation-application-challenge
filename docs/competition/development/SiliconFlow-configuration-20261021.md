# SiliconFlow 模型配置与本轮验证

2026-10-21。用户确认模型选型，并随后明确“先配置地址和模型，密钥暂留空”；开发记录先只覆盖开发过程。

## 已配置的环境变量

下列非敏感值已写入 Windows 当前用户环境变量，并同步到 Git 忽略的 `.env.local`，保证现有本地启动脚本能够读取。公共模板 `.env.example` 同步选型；真实密钥不写入模板或开发记录。

| 变量 | 值 |
| --- | --- |
| `RAG_EMBEDDING_ENABLED` | `true` |
| `RAG_EMBEDDING_BASE_URL` | `https://api.siliconflow.cn/v1` |
| `RAG_EMBEDDING_MODEL` | `Qwen/Qwen3-VL-Embedding-8B` |
| `OCR_BASE_URL` | `https://api.siliconflow.cn/v1` |
| `OCR_MODEL` | `deepseek-ai/DeepSeek-OCR` |

`RAG_EMBEDDING_API_KEY`、`OCR_API_KEY`、`SILICONFLOW_API_KEY` 按本次决定未配置。已有通用 `API_KEY` / `BASE_URL` 关联 DeepSeek，不将其发送给 SiliconFlow，也不改动生成或规划接口。

当前变量沿用原项目命名；如果以后两种能力共用一个硅基流动密钥，可在本机明确配置 `SILICONFLOW_API_KEY`。解析顺序为：

- Embedding：`RAG_EMBEDDING_API_KEY` 优先，其次 `SILICONFLOW_API_KEY`。
- OCR：`OCR_API_KEY` 优先，其次 `SILICONFLOW_API_KEY`。
- 生成与规划不继承 `SILICONFLOW_API_KEY`。

配置以真实进程环境为准；修改用户环境变量后重开终端或重启 Codex/后端。若当前进程未刷新，可直接在本机 `.env.local` 中填写对应密钥，由 `scripts/competition/start-local.ps1` 读取。空的 `.env.local` 行会被忽略；已有非空进程变量仍然优先。

## 接口依据与实施边界

两份地址原先连在同一个链接中，本次分别核对了官方 [Embeddings 接口](https://api-docs.siliconflow.cn/docs/api/embeddings-post) 和 [Chat Completions 接口](https://api-docs.siliconflow.cn/docs/api/chat-completions-post)。基础地址保留 `/v1`，不把完整请求路径填入 `*_BASE_URL`。

Embedding 选型在官方文档中列明。OCR 按用户指定的 DeepSeek-OCR 预设完整模型标识；由于密钥留空，账号模型列表、该标识当前可用性与真实 OCR 结果仍待接通后核实。不能把已有兼容接口或本次配置写成真实识别已通过。

本次更新了2026-10-20“模型留空”的选型状态：模型已选择，密钥尚未提供。K00 的旧报告保留当时事实；真实 OCR 适配、逐页处理、任务恢复和页面核验仍属 K02。

当前 `RagEmbeddingClient` 对向量返回维度有2048上限，尚未验证此模型实际返回维度。接通后应按 K03 验证返回结构、维度选项、模型指纹及索引版本；不凭填入模型名就宣布语义检索验收完成。此次没有改变向量算法或添加未经验证的维度参数。

## 测试与可追溯结果

本次新增2项配置测试，先运行得到1项失败（共享硅基密钥尚不能解析），再添加显式的 SiliconFlow 密钥别名。

- 配置测试：5项全部通过，验证密钥优先级、四角色隔离及缺密钥状态。
- 后端全部测试：135项，132通过，0失败，0错误，3跳过。
- 3项跳过为两项默认关闭的真实模型测试和一项已有旧 WeKnora 测试。
- 未请求 SiliconFlow 模型服务，未上传 PDF，没有真实 Embedding/OCR 成功记录。
- 开发记录导出工具11项测试通过，包含脱敏、时区、原提示保留、工具关联，以及子会话继承前缀的真实性检查；先复现失败再修复。

本轮原始测试输出保存在 `.codex_tmp/siliconflow-config-20261021/`；开发 Agent 对话、操作与工具结果另以脱敏原始记录归档到 `agent-records/2026-10-21-siliconflow-configuration/`。源码、测试与配置散列由本轮结果索引记录。所有既有工作区改动保留，未重置或提交。

根会话与子会话分别导出；子会话从实际任务开始的源第229行起保留，剔除前228行继承历史。每份导出说明读取截止、缺失与脱敏范围；最后快照之后的操作在下一次刷新时补入。

## 人工验证

1. 本轮没有密钥时，管理员查看 `/api/admin/models/status`，在本轮配置实际加载的前提下，Embedding 和 OCR 应为 `MODEL_NOT_CONFIGURED`，不能标为可用。
2. 以后在本机填写 SiliconFlow 专用密钥，重启后端，确认角色配置变成 `CONFIGURED_UNVERIFIED`。
3. POST `/api/admin/models/embedding/probe` 查看基本调用结果。维度或响应不兼容应保留失败记录，再进入 K03 修复验证。
4. OCR 即使补好密钥，当前探测仍会报告 `OCR_ADAPTER_PENDING_K02`，直到按 K02 完成模型适配和识别管线。
5. 详细管理员登录、状态检查和 POST 操作沿用 [K00 人工验证方法](K00-manual-verification.md)。该旧文档中的“OCR留空”指K00当日状态，本次模型选择以本文为准。
