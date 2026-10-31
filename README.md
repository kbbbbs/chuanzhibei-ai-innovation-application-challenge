# AI 智学平台

Vue 3 / Vite 前端与 Spring Boot 后端组成的课程学习平台，包含教师备课、题库、学生学习与项目自建 RAG。当前 RAG 自行管理原件、逐页解析、OCR、分块、索引、检索、权限和引用，正常启动不需要 WeKnora。

本仓库用于协作开发及人工验收。**K02 已有实现和测试证据，内容仍需逐页人工核验；K03 功能已实现，真实检索质量验收未通过。** 2026-10-31 的一次续验中，40 条可答题的混合检索已标来源 Top5 命中为 33/40（82.5%），关键词对照为 35/40（87.5%），低于 85% 目标。不能把工程测试通过当作语义效果达标，也不能把检索候选当作可回答依据。

| 协作入口 | 内容 |
| --- | --- |
| [配置说明](docs/CONFIGURATION.md) | 四类模型、环境变量优先级、数据库、存储和排错 |
| [仓库交接说明](docs/REPOSITORY_HANDOFF.md) | 安全快照、原仓库保留、上传范围及协作权限 |
| [进展、人工验收与迭代](docs/COLLABORATION_AND_VALIDATION.md) | 当前进展；K02/K03 用户故事与验收对照；反馈后如何按 Scrum + XP 迭代 |
| [K02 人工验证](docs/competition/development/K02-manual-verification.md) | 异步任务、逐页 OCR、重试与取消 |
| [公式核验](docs/competition/development/K02-formula-manual-verification.md) / [图文核验](docs/competition/development/K02-visual-manual-verification.md) | 公式、图片、题号关联及已知内容风险 |
| [K03 报告](docs/competition/development/K03-test-report.md) / [人工验证](docs/competition/development/K03-manual-verification.md) | 混合检索、版本、授权、降级及真实未达标成绩 |
| [开发 Agent 记录](docs/competition/development/agent-records/index.md) | 真实提示、修改、失败、修正和测试的脱敏记录 |

## 克隆与准备

获得私有仓库访问权限后：

```powershell
git clone https://github.com/kbbbbs/ai-learning-collaboration.git
Set-Location ai-learning-collaboration
```

以下启动示例使用 Windows PowerShell。准备：

- JDK 21 或更高版本、Maven，确保 `mvn` 可调用。
- Node.js 满足 `^20.19.0 || >=22.12.0`，并有 npm。这是当前锁文件中 Vite / Vue 插件的要求，早期 Node 20 不能直接使用。
- OCR / 语义检索需要自己的硅基流动密钥；生成回答需要另行配置可用的非 GPT 聊天模型。

先核对版本：

```powershell
java -version
mvn -version
node --version
npm --version
Copy-Item .env.example .env.local
```

编辑 `.env.local`，按[配置说明](docs/CONFIGURATION.md)填好所需角色。文件只放本机，不提交、不截图分享。直接执行 Maven 或从 IDE 启动不会自动读取此文件，下面的启动脚本才会加载。

仓库包含源代码、数据库结构、测试、配置模板和脱敏证据；不包含真实密钥、私有数据库、上传原件、完整课程 OCR、`node_modules` 或构建产物。其他同学需要自己的配置和合法取得的验收资料，克隆不等于复制本机业务数据。新仓库发布的是当前安全快照，不把旧仓库可能含私密值的历史提交一并上传。

## 本机启动：推荐隔离演示库

在项目根目录启动后端：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/competition/start-local.ps1
```

如果默认 Java 不是 JDK 21+，用本机实际 JDK 路径指定构建与运行环境：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/competition/start-local.ps1 -JavaRuntimeHome 'C:\Tools\jdk-21'
```

默认使用 `contest-demo` 配置，后端为 `http://127.0.0.1:8080`。数据库保存在 `ai-learning-backend/data/contest-demo/academy.mv.db`，RAG 原件保存在同目录下的 `documents/`，重启保留；它不使用现有 MySQL 课程库。不要让多个后端进程同时打开同一份 H2 文件。首次下载依赖所需时间取决于网络。

另开终端，进入克隆目录中的前端：

```powershell
Set-Location ai-learning-frontend
npm ci
npm run dev -- --host 127.0.0.1
```

浏览器打开 **http://localhost:24678**，也可按终端实际输出访问。前端 `/api` 代理到本机 8080。两个终端分别按 Ctrl+C 停止服务，停止不会自动删除演示资料。

仅隔离演示库提供 `admin`、`teacher`、`student`，密码均为 `123456`。默认账号和演示 JWT 只供本机验证，不用于对外部署。每位协作者可以运行自己的演示库；共用测试服务时需单独准备测试账号及配置。

## 第一轮人工验证

1. 教师登录，进入知识库，创建自己的私有验收库。
2. 上传 PDF、DOCX、PPTX、MD 或 UTF-8 TXT。单文件上限 20 MB；PDF/PPTX 上限 200 页/张；提取文本上限 100 万字符。旧 DOC/PPT 需先转换为受支持格式。
3. 观察排队、提取、OCR、分块、索引和终态。上传返回成功不代表解析完成；部分成功必须核对缺失页。
4. 在逐页核验中对照原页、原始 OCR、整理文字、公式与图片。扫描 PDF 和需识别的页面由项目调用 DeepSeek-OCR；不需要预先把扫描件变成文字版。没有 OCR 配置或识别失败时应如实显示不可用/部分可用。
5. 进入“检索与验证”，使用原概念和同义改写，核对来源页、原文、文档/解析/索引版本，以及实际 `HYBRID`、`KEYWORD` 或 `NONE` 状态。仅配置完成不能证明真实向量接口可用。
6. 按[协作验收文档](docs/COLLABORATION_AND_VALIDATION.md)逐项记录通过、失败或阻塞及证据，交回开发者。公式排版正确不代表识别内容正确；图片关联正确也不代表曲线数值已正确读出。

没有任何模型密钥时，可用普通文字资料检查基本上传、关键词检索、来源和权限；扫描 OCR、语义效果和生成回答不能据此验收通过。无答案问题仍可能返回候选，K04 的拒答及回答支持度还需单独验收。

## 自动检查

后端测试和前端生产构建：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/competition/verify.ps1
```

该检查脚本直接调用 Maven，请先让本终端 `JAVA_HOME` 指向 JDK 21+。也可分开执行：

```powershell
# 从仓库根目录开始
Set-Location ai-learning-backend
mvn test
```

```powershell
# 从仓库根目录开始
Set-Location ai-learning-frontend
npm ci
npm run build
node --test e2e/formula-rendering.test.mjs
```

前端四份受控接口页面测试不调用真实模型。先在前端目录另开终端运行：

```powershell
npm run dev -- --host 127.0.0.1 --port 5173
```

确认本机已安装 Chrome，再在前端目录执行：

```powershell
npx playwright test e2e/native-rag-ui.spec.js e2e/k02-processing-ui.spec.js e2e/visual-rag-ui.spec.js e2e/k03-retrieval-ui.spec.js
```

这里使用 5173 是因为 `playwright.config.js` 固定了测试地址，与日常开发 24678 不同。不要为普通页面回归直接运行全部 e2e：`native-rag-real.spec.js` 是另行启用的真实服务联测，会访问后端并调用模型，且需先核对它与当前异步上传协议是否匹配。

普通后端回归中的真实服务测试按条件跳过。显式开启模型/OCR 评测会产生真实请求；历史课程评测还依赖未进仓库的私有 OCR / 原件证据，不能把缺失材料、跳过或模拟测试记作真实质量通过。查看 [K03 报告](docs/competition/development/K03-test-report.md) 中的具体边界。

## MySQL 开发环境

人工验收优先使用上面的隔离演示库。确需 MySQL 时，在专用开发实例上初始化业务表，配置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 和至少 32 字节的随机 `JWT_SECRET`，然后从根目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/competition/start-local.ps1 -UseMySql
```

`database/ai_academy_schema.sql` 含完整业务结构及最小演示种子，其导入会重建 `ai_academy` 数据库，只能在确认可重建的空开发实例使用。它不是已有数据库的增量升级方案。自建 RAG 启动时通过 `RagSchemaMigrator` 增量升级自身表；`database/migrations/` 是对应 SQL 参考，不要在应用已迁移后重复手工执行。不通过 Git 分享真实数据 dump、数据库密码或教学原件。

## 项目结构与协作边界

```text
ai-learning-backend/     Spring Boot、业务服务、自建 RAG、迁移与测试
ai-learning-frontend/    Vue 页面、公式/图文展示与页面测试
database/                数据库结构及迁移参考
scripts/competition/    启动、验证、评测和开发记录工具
docs/                   配置、协作验收、需求与证据
AGENTS.md               开发 Agent 记录及 Scrum + XP 约定
```

当前语义索引对文本分块生成向量，保留图片及公式与来源的关联；选用 VL Embedding 模型名不代表已经验收图像本体的向量检索。旧 WeKnora 文件仅作历史兼容，旧库 UUID 不会自动迁入自建库。

开发者按故事与验收条件接收缺陷，先写能复现的失败测试，小步修复后回归，再请原验证者复验。验证者反馈实际观察，不直接把“界面显示完成”判为内容通过。详见[进展、人工验收与迭代文档](docs/COLLABORATION_AND_VALIDATION.md)。

赛事 10037 / 10039 的需求依据、实现方案和记录在 `docs/competition/`。代码上传不等于报名、材料或资格审核完成；参赛 Prompt 迭代链与视频应来自真实过程，不补造历史记录。
