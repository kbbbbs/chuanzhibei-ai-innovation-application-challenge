课程知识图谱本地数据库

运行要求
Docker Desktop 已启动。后端使用项目 Java 21 与 Spring Boot 管理的 Neo4j Java Driver。
运行 scripts/knowledge-graph/start-neo4j.ps1 创建或启动数据库。
使用 -Action restart 重启，-Action status 查看状态。脚本不会删除数据卷。

验证版本
镜像 Neo4j Community 5.26.33，固定内容摘要
sha256:577eb43120e8688e62e753e962c47d30044ecaa95147ee5386c29f226b878008
驱动由 Spring Boot 3.5.16 管理，实际解析为 5.28.13。
Neo4j 5.26 LTS 发布页 https://neo4j.com/release-notes/database/
Spring Boot 依赖清单 https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html

数据库使用命名数据卷 ai-learning-knowledge-graph-data，仅绑定 127.0.0.1 的 7687 和 7474 端口。
随机凭据只写入已忽略的 .codex_tmp/knowledge-graph/neo4j.env。已有卷须沿用首次设置的凭据。
该文件的 NEO4J_AUTH 值由用户名和密码用 / 分隔，后端分别读取 NEO4J_USERNAME 和 NEO4J_PASSWORD。

后端进程需要设置以下环境变量
RAG_GRAPH_ENABLED=true
NEO4J_URI=bolt://127.0.0.1:7687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD 使用上述私有文件中的密码部分

例如在启动后端的 PowerShell 会话中加载，不输出凭据
$authLine = Get-Content .codex_tmp/knowledge-graph/neo4j.env | Where-Object { $_.StartsWith('NEO4J_AUTH=') }
$authParts = $authLine.Substring(11).Split('/', 2)
$env:NEO4J_USERNAME = $authParts[0]
$env:NEO4J_PASSWORD = $authParts[1]
$env:NEO4J_URI = 'bolt://127.0.0.1:7687'
$env:RAG_GRAPH_ENABLED = 'true'

真实数据库测试
在同一会话设置 NEO4J_INTEGRATION_TEST=true，然后从 ai-learning-backend 运行
mvn -Dtest=Neo4jGraphRepositoryTest,Neo4jGraphRepositoryIntegrationTest test
集成测试创建独立随机 graphId，检查幂等投影、不可变版本、范围隔离、先修方向和结果上限。
默认测试没有 NEO4J_INTEGRATION_TEST 时跳过真实数据库测试，单元测试仍验证关闭状态与输入拒绝。

运行时 graph.enabled 默认关闭。开启后数据库不可达时接口返回明确的 503，不展示备用示范图。
凭据须保持本机私有，数据库服务不向外网发布。生产部署请使用适当的持久卷备份和受控网络。

课程使用流程
在设置上述环境变量的会话中，通过 scripts/competition/start-local.ps1 启动后端。
JavaRuntimeHome 可使用本机 Java21 安装目录。前端从 ai-learning-frontend 运行 npm run dev。
教师进入知识库中的“课程知识图谱”，选择本人课程，新建章节图谱。
选择已解析资料与页码范围，可勾选“授权当前课程阅读该资料版本”再创建。
使用实体/关系表单，或导入包含 nodes、edges 的 JSON。每项引用界面列出的真实切片标识。
从资料提取候选使用已配置的课程内容生成模型，完成后核查并保存草稿。
核对实体、关系及来源后执行审核并发布，等待同步任务完成。发布失败可重试。
有学习资格的学生在课程知识图谱中查看发布版本、展开知识关系和阅读依据。

第2章课件候选导入
原始课件由使用者本机提供，不随仓库分发。该工具校验课件 SHA-256，适用于已核对的
《第2章-机器学习概述-okk.pdf》，整理24个实体、26条有依据的关系。
先在应用内创建课程与章节，再运行以下命令并输入教师登录密码。
python scripts/knowledge-graph/import_chapter.py --username <教师账号> --pdf <PDF绝对路径> --course-id <课程ID> --chapter-id <章节ID>
已上传该课件时可增加 --document-id <资料ID>。工具会将选定资料版本授权给指定课程阅读。
工具等待实际解析完成，匹配后台真实切片，并导入待审核草稿。输出图谱地址供教师核查发布。
模型抽取和人工候选导入均保留审核流程。原课件更新或重建索引后，需要依据当前资料建立新图谱。
