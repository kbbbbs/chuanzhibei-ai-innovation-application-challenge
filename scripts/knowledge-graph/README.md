# 课程知识图谱运行

原版 `d6bb3d9` 的 Cytoscape 画布、Neo4j 仓库和固定镜像启动脚本已复用。课程详情中的“知识图谱”读取当前课程资料，自动逐片段生成，保存检查点并支持继续生成。

## 本地启动

先启动 Docker Desktop，再运行 `scripts/knowledge-graph/start-neo4j.ps1`。数据库使用原命名数据卷，绑定本机 7687 和 7474 端口。`-Action status` 查看状态，`-Action restart` 重启。

在启动后端的 PowerShell 会话加载本机私有配置。

```powershell
$authLine = Get-Content .codex_tmp/knowledge-graph/neo4j.env | Where-Object { $_.StartsWith('NEO4J_AUTH=') }
$graphCredentials = $authLine.Substring(11).Split('/', 2)
$env:NEO4J_USERNAME = $graphCredentials[0]
$env:NEO4J_PASSWORD = $graphCredentials[1]
$env:NEO4J_URI = 'bolt://127.0.0.1:7687'
$env:RAG_GRAPH_ENABLED = 'true'
& scripts/competition/start-local.ps1
```

已有数据库须沿用首次凭据。私有环境文件保存在已忽略的 `.codex_tmp` 下。模型继续使用当前管理员配置。

## 数据与应用

完成图谱的实体、类型化关系及来源声明存入 Neo4j。关系数据库保存课程归属、任务、检查点、来源版本和 `graph_ref`。旧 SQL 图谱在首次访问时迁移，原 JSON 保留用于追溯。新生成的快照通过 Neo4j 读取。

课程页面可查看相邻知识与关联路径，问答和检索可使用有出处的关联证据。学生查询按当前课程资格和资料版本授权过滤，撤权后重新校验历史图谱来源。

Neo4j 未启用或不可用时，图谱接口返回明确状态。课程问答与检索在再次核验权限后使用可用课程资料检索并显示提示。

## 验证

真实数据库测试需要 `NEO4J_INTEGRATION_TEST=true`、`NEO4J_TEST_URI`、`NEO4J_TEST_PASSWORD`。Spring 集成测试同时使用 `NEO4J_URI`、`NEO4J_PASSWORD`。运行结果及实际模型验证记录位于本次 `docs/competition/development/agent-records/2026-11-17-restore-d6bb3d9-graph`。
