# 环境变量与本地运行配置

适用：Docker/原生网页安装、管理员模型配置及 K02 / K03 协作验证。依据为当前源码、配置文件和[批准方案](implementation/2026-11-05-installation-ui/PLAN.md)。2026-11-05 用户已取消产品 GPT/OpenAI 限制：可以配置这些名称和端点；协议是否兼容仍须逐角色验证。历史报告保留当时策略和失败成绩。本轮安装、生命周期和全站重写仍待完整验收，不能据本文认定已通过。

## 网页管理与启动方式

| 方式 | 入口与配置来源 | 用途 |
| --- | --- | --- |
| Docker 新安装 | [部署指南](deployment/docker.md)；`compose.yaml` 默认叠加 `compose.mysql.yaml` | 默认内置 MySQL，也可只启公共 Compose 使用外部 MySQL |
| 原生新安装 | `scripts/competition/start-local.ps1 -Setup`，或 JDK21 `java -jar ...jar` | 最小安装服务不依赖业务数据库启动；在网页检查数据库、建首管理员 |
| H2 本机演示 | 原有 `scripts/competition/start-local.ps1`，默认 `contest-demo` | 隔离演示库与演示账号；不是生产部署 |
| 旧 MySQL 环境启动 | `scripts/competition/start-local.ps1 -UseMySql` 及 `DB_*`、JWT | 保留既有实例；不能用空库假装迁移完成 |

新安装用固定宿主命令 `--installation-code` 生成高熵一次性码；恢复用
`--recovery-code`，只有当前实例处于恢复状态才允许。命令的标准输出是凭证，
只在私有终端查看，不保存到普通应用日志。PowerShell 脚本参数分别为
`-InstallationCode`、`-RecoveryCode`，可配 `-SkipBuild` 使用已匹配当前代码的 JAR。
所有命令必须使用同一工作目录和 `AI_INSTALLATION_DIR`，不能指向另一个实例。
Docker 的完整 `exec backend java -jar /app/backend.jar ...` 命令见部署指南。

原生目录默认 `./data/installation`，相对应用工作目录；本机脚本工作目录为
`ai-learning-backend`。Docker 固定 `/app/config/private`。私有盘保存实例身份、
配置 revision 和操作/恢复状态；不应删目录重装。Windows 原生目录使用 ACL，
Linux 限制服务 UID 访问。备份须同时覆盖数据库、私有配置、上传和 RAG。

首次安装检查库类型，只有空库可初始化；兼容旧库需安装授权加原管理员认证。
未知非空库、不兼容库或已安装实例连接到不同身份的数据库应停止并处理，
不能导入会 `DROP DATABASE` 的开发脚本。没有旧管理员的恢复由宿主管理者处理，
普通向导不得新建替代管理员。模型全部禁用可完成基础安装，外部模型不参与
业务 ready 判定；真实 OCR、语义检索和生成能力仍需显式探针与质量验收。

管理员模型页保存的是 pending revision，点击应用后由 Java 父启动器排空业务、
启动目标子进程并确认实际 revision。`ACCEPTED`/页面重连不是成功；
只有 `APPLIED` 且 active 匹配目标才算完成。失败/超时保留上一成功配置并进入
`RECOVERY_REQUIRED`，通过绑定操作的恢复会话或宿主恢复码进入修正/显式回滚；
不会自动重试计费请求、换模型或静默回退。完整状态和权限见
[API 契约](implementation/2026-11-05-installation-ui/API-CONTRACT.md)。

### 部署策略变量

| 变量 | 默认与边界 |
| --- | --- |
| `AI_INSTALLATION_DIR` | 原生 `./data/installation`；Docker `/app/config/private`，不可由网页指定路径 |
| `AI_INSTALLATION_TRUSTED_ORIGINS` | Compose 默认 `http://localhost:18080,http://127.0.0.1:18080`；原生前端24678应显式配置其精确浏览器 Origin |
| `CORS_ALLOWED_ORIGINS` | 逗号分隔的精确浏览器 Origin；默认空，即不开放跨域。前端经同源 `/backend` 代理访问（nginx、Vite），通常无需设置；仅当页面与 API 不同源时配置 |
| `INSTALLATION_MODEL_HTTP_ALLOWLIST` | 逗号分隔可信模型 HTTP 主机；默认空，不放开任意内网探测，网页不能写该策略 |
| `WEB_PORT` / `WEB_BIND_ADDRESS` | Compose `18080` / `127.0.0.1`；更改时同步可信 Origin，远端用 TLS 或 SSH 隧道 |
| `AI_MANAGED_DB_HOST/PORT/NAME/USERNAME` | MySQL 叠加文件固定内部 `db:3306/ai_academy`；不返回浏览器凭证 |
| `AI_MANAGED_DB_PASSWORD_FILE` | Compose `/run/secrets/managed-db-password`，由部署者生成的私有文件提供 |

原生 Vite 向导示例：在仅用于该实例的终端设置
`$env:AI_INSTALLATION_TRUSTED_ORIGINS='http://localhost:24678,http://127.0.0.1:24678'`，
再用 `-Setup` 启动。不要把已有实例的 `DB_*`/Spring datasource 环境直接带入
新的安装实例；这些是旧业务启动兼容信号。使用全新实例目录不能迁移原业务数据。
默认 Compose 不读根 `.env.local`，也不把宿主全部模型变量透传给容器；只把明确
写入 Compose 的策略变量传入。要集中由网页管理模型，保持其进程模型环境变量未设置。

## 1. 先选验证方式

| 要验证的内容 | 需要的配置 |
| --- | --- |
| 普通文字上传、原件、关键词检索、权限 | 无模型密钥也可检查；不能据此验收 OCR / 语义 / 回答 |
| 扫描件、数学页、图片读取 | OCR 地址、模型及硅基流动密钥 |
| K03 语义与关键词混合检索 | 开启 Embedding，配置确认模型及可用密钥；先建立兼容向量 |
| RAG 生成回答、备课、出题、Agent | generation 三项；planning 可以显式继承 generation |

优先使用 [README 的隔离演示库启动步骤](../README.md#本机启动推荐隔离演示库)。首次复现不要导入他人的业务数据库，也不要把 `.env.local` 发给协作者；每位验证者填写自己的凭证。

## 2. 配置文件怎么填写

在仓库根目录执行 `Copy-Item .env.example .env.local`，编辑新文件。使用一行一个 `KEY=value`，值不加引号，不写 `export`、`$env:`，不在行尾加注释；该脚本不是通用 dotenv 解析器，不会展开变量引用。密钥只粘贴在本机私有文件，不贴到聊天、Issue 或截图。

扫描与混合检索所需配置可以这样填写（下列 `YOUR_...` 必须替换，不是可用凭证）：

```dotenv
SILICONFLOW_API_KEY=YOUR_SILICONFLOW_KEY
RAG_EMBEDDING_ENABLED=true
RAG_EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1
RAG_EMBEDDING_MODEL=Qwen/Qwen3-VL-Embedding-8B
RAG_EMBEDDING_TIMEOUT_SECONDS=30
RAG_RETRIEVAL_RRF_K=1
RAG_RETRIEVAL_CANDIDATE_LIMIT=20
OCR_BASE_URL=https://api.siliconflow.cn/v1
OCR_MODEL=deepseek-ai/DeepSeek-OCR
OCR_TIMEOUT_SECONDS=60
```

使用此共享别名时，`RAG_EMBEDDING_API_KEY`、`OCR_API_KEY` 不设置即可。若两角色各有凭证，则分别填写它们，未设置的那个仍可取 `SILICONFLOW_API_KEY`。不要把通用 `API_KEY` 中的 DeepSeek 聊天密钥拿来调用硅基流动。

需要生成回答时，再填同一聊天供应商配套的三项：

```dotenv
AI_GENERATION_BASE_URL=https://YOUR_CHAT_PROVIDER/v1
AI_GENERATION_MODEL=YOUR_CHAT_MODEL
AI_GENERATION_API_KEY=YOUR_CHAT_PROVIDER_KEY
AI_GENERATION_TIMEOUT_SECONDS=30
```

上面的聊天地址和模型是占位示例，应从自己的服务后台复制实际值。当前生成/规划客户端使用 OpenAI 兼容聊天协议，地址通常以 `/v1` 结尾；这不表示已实现 Anthropic、Gemini 或其他供应商的全部原生协议。不要误填控制台网页或带密钥的查询 URL。模型名不能省略。网页规划可显式整组继承 generation 的地址/模型/密钥，超时可单独设置；独立规划服务需完整填写。Embedding、OCR 各自使用已有适配器，不因聊天端点可用就视为这两角色已支持。

### 四类模型字段与优先级

安装配置按字段解析：显式角色进程环境 > 实例私有配置 > 兼容环境别名/默认。
环境来源显示为 `ENVIRONMENT` 并列入 `lockedFields`，网页不能覆盖它；
已保存配置为 `PRIVATE_FILE`，规划整组继承字段为 `INHERITED`。
`KEEP` 保留当前凭证，`REPLACE` 提交新凭证，`CLEAR` 删除本角色保存值；
GET 只回是否配置，不回密钥或可当作新值的掩码。不存在请求失败后的供应商切换。

| 角色 | 地址 / 模型 / 密钥 | 继承或兼容规则 |
| --- | --- | --- |
| generation | `AI_GENERATION_BASE_URL`、`AI_GENERATION_MODEL`、`AI_GENERATION_API_KEY` | 对应字段先取显式 generation，再取 `AI_OPENAI_*`；地址最后兼容 `BASE_URL`，密钥最后兼容 `API_KEY`。模型无通用默认值 |
| planning | `AI_PLANNING_BASE_URL`、`AI_PLANNING_MODEL`、`AI_PLANNING_API_KEY` | 网页可整组显式继承 generation；独立环境组不完整时应显示配置不完整，模型兼容 `AI_AGENT_PLANNER_MODEL` |
| embedding | `RAG_EMBEDDING_BASE_URL`、`RAG_EMBEDDING_MODEL`、`RAG_EMBEDDING_API_KEY` | 密钥依次取 `RAG_EMBEDDING_API_KEY`、旧别名 `RAG_EMBEDDING_APIKEY`、`SILICONFLOW_API_KEY`；不继承 generation |
| ocr | `OCR_BASE_URL`、`OCR_MODEL`、`OCR_API_KEY` | 密钥依次取 `OCR_API_KEY`、旧别名 `OCR_APIKEY`、`SILICONFLOW_API_KEY`；不继承 generation |

当前协议范围：generation/planning 使用 OpenAI 兼容 `/chat/completions`；
embedding 使用兼容 `/embeddings` 的文本批量输入与向量响应；
OCR 使用含 `image_url` 图片内容的聊天 completions 请求和已有 OCR 提示协议，
不是任意供应商原生 `/ocr` API。网页可接受模型配置不证明对端支持相同请求/响应，
四角色必须分别探测；一个角色通过不能替其他角色填通过。

`AI_OPENAI_*` 保留为兼容变量名，不限制实际供应商。允许 OpenAI/GPT 不是自动默认到 GPT；
仍须明确启用、有效地址/模型/凭证并通过当前适配器。URL 禁止 userinfo、query、
fragment 和不被策略允许的远程明文 HTTP。Embedding/OCR 不继承聊天角色的密钥；
只有部署者显式提供的 `SILICONFLOW_API_KEY` 兼容别名可供这两角色共用。

### 操作系统变量与 `.env.local` 谁优先

当前 PowerShell 启动脚本：

1. 保留已有进程环境中的非空设置。
2. 对缺失的 Embedding / OCR 角色变量及 `SILICONFLOW_API_KEY`，先读取本机 Windows User 环境值。
3. 再从根目录 `.env.local` 填充进程中尚无非空值的字段；空行值忽略，不覆盖已有值。
4. MySQL 需显式 JWT；隔离演示库在未提供 JWT 时使用本机演示值。

因此 `.env.local` 不是强制覆盖层。若改文件后无变化，先关闭旧后端，检查本终端/Windows User 中是否仍有同名旧值，再重启。脚本只主动补读上面两类模型的 Windows User 值；generation / planning / 数据库等新保存的 User 变量需要新终端继承，或直接写入 `.env.local`。

`${NAME:fallback}` 的 fallback 是“未设置时”生效，不等于“任意空串都继承”。通过 IDE、Docker 或其他启动器显式注入空 `AI_PLANNING_*`、`RAG_EMBEDDING_API_KEY` 等值，可能阻断继承。需要继承时删除该变量，别设置 `""`。`.env.local` 中的空模板行被本项目启动脚本忽略，因此不等同于显式空系统属性。

直接运行 `mvn spring-boot:run`、`java -jar` 或 IDE 不会自动加载根 `.env.local`，也不会运行脚本的 User 环境补读逻辑。应使用启动脚本，或在该进程的环境配置中逐项提供变量；勿把密钥写进 IDE 共享运行配置和命令行参数。

## 3. 启动参数、路径与可调变量

从根目录运行 `powershell -File scripts/competition/start-local.ps1`。`-JavaRuntimeHome '实际JDK目录'` 同时指定 Maven 构建与应用运行的 JDK；不传时取本终端 `JAVA_HOME`。JDK 至少 21，且 Maven 必须可在 PATH 中找到。`-SkipBuild` 仅在已有匹配当前代码的 jar 时使用，不能在首次克隆或改代码后跳过构建。

| 配置 | 默认 / 用途 | 人工验收注意 |
| --- | --- | --- |
| `RAG_EMBEDDING_ENABLED` | 原业务配置未设置为 false；新模板仅注释示例 | false 时只能验关键词模式，不能验语义达标 |
| `RAG_EMBEDDING_TIMEOUT_SECONDS` | 30 秒 | 变更后重启；服务故障应明确降级 |
| `RAG_RETRIEVAL_RRF_K` | 1 | 仅用原调参题选出；不要继续按已观察续验题挑值 |
| `RAG_RETRIEVAL_CANDIDATE_LIMIT` | 每通道20 | 是候选数，不是回答置信度 |
| `OCR_TIMEOUT_SECONDS` | 60 秒 | 超时/截断不能计作识别成功 |
| `AI_GENERATION_TIMEOUT_SECONDS` | 30 秒 | 是模型角色超时，其他场景也有各自总超时 |
| `AI_PLANNING_TIMEOUT_SECONDS` | 10 秒 | 规划失败不自动换其他供应商 |
| `RAG_CHAT_ATTACH_IMAGES` | false | 默认先用 OCR 读图，再把观察提供给文字回答模型；仅在确认回答模型支持图片后显式启用 |
| `RAG_VISUAL_TIMEOUT_MS` | 15000 毫秒 | 单位与上面的秒不同 |
| `RAG_STORAGE_PATH` | 正常配置为 `./data/native-rag` | 相对后端工作目录；演示 profile 固定覆盖为 `./data/contest-demo/documents` |
| `UPLOAD_DIR` | `uploads` | 普通上传目录，不等于私有 RAG 原件目录 |
| `JWT_SECRET` | 旧 MySQL 环境模式显式配置至少32字节；网页新安装由内部随机生成 | 已有外部值保留，更换会使旧登录令牌失效 |
| `JWT_EXPIRATION` | 86400000 毫秒 | 默认24小时 |

修改模型地址/名称后，旧向量可能与当前模型指纹不兼容。解析文字本身正确时选择“重建向量索引”；原页 OCR 错误或缺失时需重试/重新解析，向量重建不会改正 OCR。

### 隔离演示与 MySQL

| 项目 | 默认 `contest-demo` | `-UseMySql` |
| --- | --- | --- |
| 数据库 | 后端 `data/contest-demo/academy.mv.db` | `DB_URL` 指定的 MySQL |
| 数据库账号 | profile 内的本地 H2 配置 | `DB_USERNAME` / `DB_PASSWORD` |
| RAG 原件 | 后端 `data/contest-demo/documents` | `RAG_STORAGE_PATH`，默认后端 `data/native-rag` |
| 初始账号 | `admin` / `teacher` / `student`，密码`123456`，均为演示数据 | schema中的最小演示种子仅供开发；已有库按各自账号管理 |
| JWT | 未设置时由脚本填本机演示值 | 启动前必须私下配置 |

MySQL 示例（密码与JWT自行填写，不共享）：

```dotenv
DB_URL=jdbc:mysql://localhost:3306/ai_academy?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=YOUR_TEST_DATABASE_USER
DB_PASSWORD=YOUR_TEST_DATABASE_PASSWORD
JWT_SECRET=YOUR_PRIVATE_RANDOM_VALUE_AT_LEAST_32_BYTES
```

`DB_*` 不影响隔离演示 profile 的数据源。现有业务表需先在专用空实例准备；`database/ai_academy_schema.sql` 会重建数据库，不能拿它增量升级已有业务数据。真实数据导出不作为协作初始化材料。RAG 自有表由应用启动迁移，已执行过的迁移不重复手工执行。

macOS/Linux 或 IDE 用户可在自身进程配置同名环境变量，从 `ai-learning-backend` 启动 `contest-demo` profile；Windows 启动脚本使用 `java.exe`，不作为跨平台脚本。直接 Maven 方式为 `mvn spring-boot:run -Dspring-boot.run.profiles=contest-demo`，需先配置环境并保证 Maven 使用 JDK21+，它不读取 `.env.local`。

## 4. 配置后怎样确认

1. 网页保存后执行应用并等待对应 operation 为 `APPLIED`；环境配置模式修改后重启进程。前端正常登录并能列出知识库，不代表模型可用。
2. 用隔离演示管理员登录后，`GET /api/admin/models/status` 可检查四角色配置状态。`CONFIGURED_UNVERIFIED` 仅表示配置形式满足约束，此 GET 不产生模型请求，也不返回密钥/地址。
3. 需要真实连通性时，对相应角色调用 `POST /api/admin/models/{role}/probe`，`role` 为 `generation`、`planning`、`embedding`、`ocr`。该请求会调用真实模型并可能计费；探针用合成输入，不能替代课程质量验收。
4. 教师上传一份普通文字与一份扫描/公式资料，在 K02 逐页核验中看实际结果；等待索引完成后，再按 K03 检查实际模式、版本及来源。不用截图密钥证明配置。

演示管理员状态检查示例只输出脱敏状态，不输出登录对象：

```powershell
$login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/auth/login' -ContentType 'application/json' -Body '{"username":"admin","password":"123456"}'
$headers = @{Authorization = 'Bearer ' + $login.data.token}
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/admin/models/status' -Headers $headers
```

API返回 HTTP200 也需核对业务 `code` 和报告状态。连通性通过不代表 OCR 的公式/图表内容正确，混合检索也尚未达到85%目标，见[K03真实成绩](competition/development/K03-test-report.md)。

## 5. 常见问题

| 现象 | 检查与处理 |
| --- | --- |
| Maven 报 Java release 不支持 | `mvn -version`核对 JDK；启动时指定实际 `-JavaRuntimeHome`，或修正本终端 `JAVA_HOME` |
| Node / Vite 启动报 engines 或 API 不存在 | 使用锁文件要求的 Node 版本后重新`npm ci`，不要手改锁文件凑合 |
| 24678 被占用 | 关闭自己之前的前端终端，或显式换端口；页面测试仍使用其5173配置 |
| 网页打开但 API 失败 | 后端是否在8080，前端是否从 Vite 访问；不能直接双击 HTML 文件 |
| 新填密钥未生效 | 重启后端；确认 `.env.local`在仓库根、没有引号/行尾注释、没有更高优先级旧环境值 |
| `MODEL_NOT_CONFIGURED` | 所需角色地址、密钥、模型缺一不可；生成角色不自动取硅基流动共享别名 |
| `MODEL_ENDPOINT_INVALID` | 使用合法 API 基地址；检查 URL 与部署的 HTTP 可信主机策略，不用控制台网页或不安全远程 HTTP |
| `FIELD_EXTERNALLY_MANAGED` | 更高优先级环境变量锁定字段；修改部署环境并重启相应实例后再编辑，不能把网页保存当覆盖成功 |
| 应用后短暂502或连接断开 | 轮询同一 operation，等待明确 `APPLIED`/恢复终态；不要重复点击提交或自行认定成功 |
| `RECOVERY_REQUIRED` | 保留私有配置与旧 active；进入绑定操作的恢复页，必要时宿主生成 `--recovery-code`，修正后显式应用/回滚 |
| `KEYWORD` / `NO_COMPATIBLE_VECTORS` | 看实际原因；检查模型连通性、索引模型/维度，再重建向量；不要把关键词命中称为语义命中 |
| OCR 部分失败 | 对照失败页与原始输出，重试该页；截断、缺公式或缺图不能用“任务已结束”替代通过 |
| 向量服务断开但仍返回结果 | 明确降级为关键词是预期行为；核对 `actualMode` 与 `degradedReason`，不是证明向量接口成功 |
| 重启后演示资料消失 | 是否换了后端工作目录、删除本地data或用了其他profile；Git中本就没有他人的私有资料 |
| `mvn clean`后无法复跑历史课程指标 | 历史真实OCR/向量缓存位于被忽略的target，需私下恢复或重新真实处理，不能用模拟内容冒充 |

多人交接只共享脱敏环境模板、代码版本、样本来源标识和验证结果。真实密钥、JWT、完整学生数据及课程原件按私有渠道管理；具体的用户故事、验收表和失败后的迭代规则见[协作验收文档](COLLABORATION_AND_VALIDATION.md)。
