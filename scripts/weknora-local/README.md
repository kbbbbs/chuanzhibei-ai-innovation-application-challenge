# Agent RAG 上游 WeKnora 配置

这个目录把 WeKnora 固定为本项目本地 Agent 的 RAG 上游。主链路是：

`Agent -> WeKnora knowledge-search -> 检索片段 -> AgentPromptAssembler -> Agent LLM`

WeKnora 负责文档解析、远程 embedding、向量检索和知识片段返回；最终回答、Schema 动作选择、参数抽取和工具执行仍由本项目 Agent 负责。

本项目后端使用：

- `WEKNORA_BASE_URL=http://localhost:8090`
- `WEKNORA_API_KEY=<你在 WeKnora UI 里生成的 API Key>`

## 我能自动完成的部分

- 下载或更新 `Tencent/WeKnora` 到 `D:\ai-learning\.omx\WeKnora`。
- 生成本地 `.env`，把 WeKnora API 端口改成 `8090`，前端 UI 改成 `8091`，避开本项目后端的 `8080`。
- 挂载 `config/builtin_models.yaml`，只预置远程 Embedding 模型；Agent RAG 主链路只调用 WeKnora Search，不调用 WeKnora Knowledge Chat 或 Agent Chat。
- 启动、停止、健康检查 WeKnora。
- 把生成后的 WeKnora API Key 写入 Windows 用户环境变量，供 `ai-learning-backend` 读取。

## 需要你处理的部分

- 安装并启动 Docker Desktop。当前机器已经可以使用 Docker。
- 提供远程 Embedding API Key。当前已验证配置：`gemini-embedding-2-preview`，`https://yunwu.ai/v1`，维度 `3072`。
- 首次启动后在 WeKnora UI 注册/登录，并在设置页生成 WeKnora API Key。

不需要安装 Ollama；当前配置全部使用远程模型。

## 启动步骤

1. 安装并打开 Docker Desktop。
2. 运行：

```powershell
.\scripts\weknora-local\start-weknora.ps1
```

3. 编辑 `D:\ai-learning\.omx\WeKnora\.env`，把 `EMBEDDING_API_KEY` 从 `replace-me` 改成真实值。
4. 如果修改了模型配置，重启 WeKnora app：

```powershell
docker compose -f .\.omx\WeKnora\docker-compose.yml -f .\.omx\WeKnora\docker-compose.ai-learning.yml --env-file .\.omx\WeKnora\.env restart app
```

5. 打开 `http://localhost:8091` 注册/登录；也可以运行下面的脚本完成注册/登录，并创建只包含 `retrieve + ingest + manage_kbs` 的 Agent RAG API Key（不包含 WeKnora chat/agent 权限）：

```powershell
.\scripts\weknora-local\bootstrap-agent-rag.ps1
```

6. 如果在 UI 中手动生成了 API Key，再写入本项目后端环境变量：

```powershell
.\scripts\weknora-local\configure-ai-learning-weknora.ps1 -ApiKey "<your-weknora-api-key>"
```

7. 重启 `ai-learning-backend`。
8. 在 WeKnora UI 创建知识库，选择 `ai-learning-embedding-default`，上传文档并等待解析/向量化完成。
9. 在本项目 Agent 管理中，把该知识库 ID 保存为 JSON 数组，例如：

```json
["your-weknora-kb-id"]
```

10. 验证 WeKnora：

```powershell
.\scripts\weknora-local\verify-weknora.ps1 -ApiKey "<your-weknora-api-key>"
```

绑定知识库并完成一次真实 Agent RAG 搜索后，可进一步验证：

```powershell
.\scripts\weknora-local\verify-weknora.ps1 `
  -ApiKey "<your-weknora-api-key>" `
  -KnowledgeBaseId "<your-knowledge-base-id>" `
  -Query "这门课程的学习目标是什么"
```

## 和 Agent 的关系

- Agent 定义中的 `knowledgeBaseIds` 保存 WeKnora 知识库 ID。
- `WeKnoraKnowledgeContextProvider` 调用 `/api/v1/knowledge-search` 获取片段。
- `AgentPromptAssembler` 把片段加入本项目 Agent 的 Prompt。
- 最终回答仍由 `ChatModelClient` 使用本项目配置的 Agent LLM 生成。

## 和推荐功能的关系

第一阶段推荐功能不依赖 WeKnora。WeKnora 本地跑通后，下一步可以把它作为语义召回层加入推荐：

- 规则推荐继续负责课程、课时、练习的确定性排序。
- WeKnora 负责从知识库/论文/课程资料中按 query 做语义召回。
- 最终推荐分数可以融合为 `规则分 * 0.65 + 语义分 * 0.35`，并把命中的片段作为推荐理由补充到前端卡片里。
