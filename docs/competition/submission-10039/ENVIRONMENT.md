# 环境与十分钟复现指南

本轮比赛录制使用D:/ai-learning-github-latest的现有入口24679/8082，前端通过/backend/访问后端。专用教师czb_teacher、学生czb_student已准备，人工智能一班已包含学生，课程从零创建后在发布面板选该班。当前点击步骤、8份PDF素材、账号与启动配对见[新版录制准备](recording/DEMO_READY.md)。以下依赖、启动和最小示例使用当前公开源码。历史启动计时另见归档报告。

本指南面向 Windows 本机评审。材料包解压后，进入 `code/` 源码目录执行命令。使用仓库克隆时，进入包含 `ai-learning-backend/`、`ai-learning-frontend/` 和 `scripts/` 的项目根目录。

## 环境与依赖

| 项目 | 要求或实际版本 | 准备方式 |
| --- | --- | --- |
| JDK | 至少 21，本次计时使用 OpenJDK 24 | 将本机 JDK 安装目录设置为 `JAVA_HOME` |
| Maven | 建议 3.9，本次使用 3.9.16 | `mvn` 可在终端调用 |
| Node.js | `^20.19.0` 或 `>=22.12.0`，本次使用 24.13.1 | 通过 Node 官方发行版准备 |
| npm | 随 Node 安装，本次使用 11.8.0 | 用锁文件安装依赖 |
| 浏览器 | 当前 Chrome 或 Edge | 访问本机前端 |
| 数据库 | 默认演示使用 H2 文件库 | 随后端依赖安装，无需另装 MySQL |
| 回答模型 | 可用的非 GPT 聊天服务 | 由评审者私下配置地址、模型和凭据 |
| Embedding | Qwen/Qwen3-VL-Embedding-8B | 配置相应服务凭据 |
| OCR | deepseek-ai/DeepSeek-OCR | 扫描件、公式页或混合页识别时使用 |

后端依赖由 `ai-learning-backend/pom.xml` 管理，包括 Spring Boot 3.5.16、Spring AI 1.0.0-M5、MyBatis Plus 3.5.12、PDFBox 3.0.4 和 POI 5.4.1。前端依赖以 `ai-learning-frontend/package-lock.json` 为安装依据，包括 Vue 3、Vite、Element Plus、Pinia、KaTeX 和 Cytoscape。源码包保留完整构建清单与锁文件。

联网环境需要能够下载 Maven/npm 依赖并访问模型 API。课程 RAG 核心演示使用自建资料处理与检索服务，默认启动入口采用 H2 演示配置。知识图谱的 Neo4j 配置按技术文档和源码已有配置准备。

## 第一步，填写私有模型配置

在项目根目录运行版本检查并复制模板。

```powershell
java -version
mvn -version
node --version
npm --version
Copy-Item -LiteralPath .env.example -Destination .env.local
notepad .env.local
```

本机默认 `java` 若为旧版本，将 `JAVA_HOME` 设置为实际 JDK 21+ 安装目录。后续后端命令用 `-JavaRuntimeHome $env:JAVA_HOME` 同时指定构建和运行的 JDK。

`.env.local` 使用逐行 `KEY=value` 格式。填写自己的值，不加引号或行尾注释。现有进程环境中的非空同名变量优先，因此修改配置后需要重启相应终端与后端。

| 字段 | 填写内容 |
| --- | --- |
| `AI_GENERATION_BASE_URL` | 实际非 GPT 聊天服务 API 基地址 |
| `AI_GENERATION_MODEL` | 服务支持的实际模型名 |
| `AI_GENERATION_API_KEY` | 该聊天服务的私有凭据 |
| `RAG_EMBEDDING_ENABLED` | 保持模板中的 `true` |
| `RAG_EMBEDDING_BASE_URL` | 模板中的服务地址，使用其它合法服务时按实际地址填写 |
| `RAG_EMBEDDING_MODEL` | `Qwen/Qwen3-VL-Embedding-8B` |
| `RAG_EMBEDDING_API_KEY` | Embedding 私有凭据 |
| `OCR_BASE_URL` | OCR 实际服务地址 |
| `OCR_MODEL` | `deepseek-ai/DeepSeek-OCR` |
| `OCR_API_KEY` | OCR 私有凭据 |

规划角色沿用回答模型时，将模板中的 `AI_PLANNING_BASE_URL`、`AI_PLANNING_MODEL` 和 `AI_PLANNING_API_KEY` 留空，由启动脚本忽略空值。Embedding 与 OCR 可按模板使用 `SILICONFLOW_API_KEY` 共享别名，独立角色密钥优先。

完整模板在源码根 `.env.example`。真实凭据只留在本机私有配置。默认演示 profile 已提供 H2 和本机演示 JWT 配置。

## 第二步，启动后端

保持终端工作目录为源码根目录，确认 `JAVA_HOME` 指向本机 JDK 21+ 后运行。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/competition/start-local.ps1 -JavaRuntimeHome $env:JAVA_HOME
```

脚本先构建后启动服务。等待日志显示应用已启动，默认后端地址为 `http://127.0.0.1:8080`。首次运行时需要下载依赖。

演示数据库位于后端 `data/contest-demo/academy.mv.db`，资料原件位于 `data/contest-demo/documents/`。停止服务后数据保留。重新演示时继续使用同一源码位置及 profile，即可保留课程和资料。

## 第三步，启动前端并登录

另开终端，从源码根目录进入前端后运行。

```powershell
Set-Location ai-learning-frontend
npm ci
npm run dev -- --host 127.0.0.1
```

浏览器打开 `http://localhost:24678`。默认前端请求本机 8080 后端。前端终端保持运行。

| 角色 | 用户名 | 本机演示密码 |
| --- | --- | --- |
| 教师 | `teacher` | `123456` |
| 学生 | `student` | `123456` |
| 管理员 | `admin` | `123456` |

以上账号由隔离演示 seed 初始化，仅供本机复现使用。

## 第四步，建立最小课程与资料

干净副本可使用材料中的合成示例docs/competition/submission-10039/demo/gradient.md。当前本机比赛账号和人工智能一班已预置，详见新版录制准备。

1. 教师在“课程管理”中新建课程，填写名称、分类和简介，点击“下一步”，进入自动建立的课程资料库。
2. 上传gradient.md，等待实际处理终态，在“学生访问”选择“开放给学生”。正式比赛录制在此处一次多选上传8份PDF。
3. 点击“下一步，准备课程内容”，选择“跳过备课”，在课程内容中“新增章节”，再“添加课时”。资源类型选“Markdown 课本”，粘贴示例末尾正文并保存。
4. 干净副本由管理员进入“教学班管理”，为这门已创建的课程开班，选择授课教师，将student加入该班。当前本机比赛已有待关联的人工智能一班，教师发布时可直接选择。
5. 教师回到课程管理，点击课程行“发布”，勾选相应教学班，点击“确认发布”。
6. 学生进入“我的班级”，打开刚发布课程和课时，右侧切到“问答”，显示“课时快问”。

## 第五步，验证问答与检索

1. 学生输入“学习率过大会怎样”，观察真实检索和生成状态，打开回答中的资料引用查看原文。
2. 教师打开同一课程详情的“课程资料”，切到“检索与验证”，输入相同问题并查看文件、页码、片段及实际检索模式。
3. 试用新对话、历史恢复、停止生成，以及资料的学生访问设置。课程发布范围和当前资料权限共同决定学生能否阅读来源。
4. 当前新版运行联测使用1份合成短资料，已验证创建向导、自动课程库、学生访问设置、发布到预置班级、课时快问及教师检索。8份正式PDF按录制时实际上传和处理状态展示。

## 历史启动计时与实测依据

| 建议时间段 | 评审操作 |
| --- | --- |
| 0:00–1:00 | 检查版本、复制并填写私有配置 |
| 1:00–4:00 | 安装依赖并启动前后端 |
| 4:00–7:00 | 登录，创建课程、课时、教学班，加入学生 |
| 7:00–10:00 | 上传示例、授权课程、完成一次选文 RAG 提问并查看出处 |

该安排以工具已安装、模型凭据已准备且网络可用为前提。2026 年 11 月 17 日的实测从无 `target/` 与 `node_modules/` 的源码副本开始，使用同机共享依赖缓存，安装、构建、后端启动与真实学生登录共 89.631 秒。另有两份前端后续文件的增量构建记录。首次在全新电脑下载依赖、填写凭据及人工创建课程的耗时由实际环境决定。完整实测见 [启动计时](reproduction.md)。

## 常见处理

| 情况 | 处理方法 |
| --- | --- |
| Maven 使用了旧 JDK | 检查 `JAVA_HOME`，重新执行带 `-JavaRuntimeHome` 的后端命令 |
| 8080 或 24678 已占用 | 关闭本机先前启动的对应服务，释放端口后重试 |
| Windows 提示 JAR 文件占用 | 在自己的旧后端终端停止服务后再构建，或在独立源码副本构建 |
| 登录成功但学生无课程资料 | 核对课程已发布、教学班进行中、学生已加入、资料版本已授权 |
| 模型配置缺失或超时 | 核对角色三项配置并重启后端，保留实际错误信息 |
| 检索显示关键词降级 | 核对 Embedding 配置与索引兼容性，按实际模式记录 |
| 文档显示部分可用 | 打开逐页核验查看失败页与原始结果，按需重试相应页面 |
| 停止后重新演示 | 在两个终端按 Ctrl+C 停止，按相同目录和配置再次启动 |

## 复现性声明

本材料提供源代码、锁文件、环境模板、合成示例、实际启动计时和真实联测记录。模型服务及私有凭据由运行者配置。固定课程检索评测的题集、散列和逐题结果随报告提供，相关原始课程 PDF 由合法持有者按其授权范围使用。完整评测时使用实际解析管线和实际服务，并单独保留失败页、降级、跳过与环境差异。
