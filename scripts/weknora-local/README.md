# 本地 WeKnora 配置

这个目录把 WeKnora 第二阶段的本地运行配置固定下来，目标是让本项目后端继续使用：

- `WEKNORA_BASE_URL=http://localhost:8090`
- `WEKNORA_API_KEY=<你在 WeKnora UI 里生成的 API Key>`

## 我能自动完成的部分

- 下载或更新 `Tencent/WeKnora` 到 `D:\ai-learning\.omx\WeKnora`。
- 生成本地 `.env`，把 WeKnora API 端口改成 `8090`，前端 UI 改成 `8091`，避开本项目后端的 `8080`。
- 挂载 `config/builtin_models.yaml`，预置 LLM 和 Embedding 两个内置模型。
- 启动、停止、健康检查 WeKnora。
- 把生成后的 WeKnora API Key 写入 Windows 用户环境变量，供 `ai-learning-backend` 读取。

## 需要你处理的部分

- 安装并启动 Docker Desktop。当前机器没有检测到 `docker` 命令。
- 提供模型 API Key。模板默认：
  - LLM：`deepseek-v4-flash`，`https://api.deepseek.com`
  - Embedding：`BAAI/bge-m3`，`https://api.siliconflow.cn/v1`，维度 `1024`
- 首次启动后在 WeKnora UI 注册/登录，并在设置页生成 WeKnora API Key。

如果你想全本地模型，需要另外安装 Ollama 并拉取可用的 chat/embedding 模型，然后把 `.omx\WeKnora\.env` 和 `.omx\WeKnora\config\builtin_models.yaml` 改成对应 provider/base_url/model。

## 启动步骤

1. 安装并打开 Docker Desktop。
2. 运行：

```powershell
.\scripts\weknora-local\start-weknora.ps1
```

3. 编辑 `D:\ai-learning\.omx\WeKnora\.env`，把 `LLM_API_KEY` 和 `EMBEDDING_API_KEY` 从 `replace-me` 改成真实值。
4. 如果修改了模型配置，重启 WeKnora app：

```powershell
docker compose -f .\.omx\WeKnora\docker-compose.yml -f .\.omx\WeKnora\docker-compose.ai-learning.yml --env-file .\.omx\WeKnora\.env restart app
```

5. 打开 `http://localhost:8091`，注册/登录后生成 API Key。
6. 写入本项目后端环境变量：

```powershell
.\scripts\weknora-local\configure-ai-learning-weknora.ps1 -ApiKey "<your-weknora-api-key>"
```

7. 重启 `ai-learning-backend`。
8. 验证 WeKnora：

```powershell
.\scripts\weknora-local\verify-weknora.ps1 -ApiKey "<your-weknora-api-key>"
```

## 和推荐功能的关系

第一阶段推荐功能不依赖 WeKnora。WeKnora 本地跑通后，下一步可以把它作为语义召回层加入推荐：

- 规则推荐继续负责课程、课时、练习的确定性排序。
- WeKnora 负责从知识库/论文/课程资料中按 query 做语义召回。
- 最终推荐分数可以融合为 `规则分 * 0.65 + 语义分 * 0.35`，并把命中的片段作为推荐理由补充到前端卡片里。
