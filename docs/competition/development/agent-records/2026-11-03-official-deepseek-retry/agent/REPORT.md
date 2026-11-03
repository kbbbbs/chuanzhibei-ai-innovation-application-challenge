# DeepSeek 官方接口 Agent 业务重试

用户原始要求：“我在./新建文档里放置了ds官方的url以及key，用该api进行重试”。本轮使用 diagnosing-bugs 的实际 HTTP 业务反馈回路，以真实 `pendingAction`、确认执行、数据库查询和审计一致性判定，不将 HTTP 200 或模型列表探针当作业务通过。

## 结论

官方 `https://api.deepseek.com`、`deepseek-flash` 在当前默认截止时间下通过本轮有限样本。创建课程规划 2676 ms、课程只读查询 4591 ms、创建章节规划 4114 ms。两个写计划均在确认前未写入，确认后各新增一条合成数据，重复 actionId 被拒绝，管理员可读到对应的唯一审计。学生确认接口业务 code403。

原中转模型标识为 `opencode/deepseek-v4.1-flash`；本轮服务及模型标识均已更换，不是严格同模型单变量对照，不能据此断言先前失败唯一由中转服务造成。没有改产品代码、`.env.local`、默认超时或此前前端未提交修复，没有重建 jar、跑 Maven、OCR 或 Embedding。本轮仅证明三个合成业务请求，不宣称整个 Agent 工具目录或所有模型请求稳定通过。

## 执行归属与时间

时间均按机器真实 UTC+08:00（Asia/Shanghai）记录；未将环境提示 Asia/Tokyo 冒充日志时区。

- Sol 子代理于 2026-11-03 09:27 开始，09:29 启动隔离实例 PID34460；准备脚本后会话因模型服务限流中断。该限制属于开发代理服务，不计作项目或 DeepSeek 业务错误。09:29–14:39 期间未声称持续测试；应用日志有 clock leap/thread starvation 提示，保留真实记录。
- 主代理于 14:40:55 启动本子代理准备的 `business-retry.mjs` 一次，实际执行课程 WRITE 和 READ 两个业务 chat；14:41:03 完成。本报告不将这两条请求冒称由 Sol 亲自启动。
- Sol 恢复后于 14:43:29 亲自运行 `verify-audit.mjs`，仅管理员登录及只读查审计；14:45:15 亲自运行 `chapter-retry.mjs`，完成第三个业务请求及章节确认闭环；14:46:07 完成自有实例关闭和原服务验证。

## 环境与预算

读取项目 AGENTS.md、diagnosing-bugs SKILL.md、前轮 Agent REPORT；未找到 GLOSSARY/ADR。直接使用现有 jar、`D:\language\Java\bin\java.exe`、端口8082、独立 H2、RAG 目录和随机 JWT。隔离数据与秘密仅在被忽略的 `.codex_tmp/official-deepseek-retry/`。按 hostname 精确定位 `api.deepseek.com` 后提取其后对应 key，未取文档首个旧中转 key。generation/planning 的 base/key/model 均显式赋官方配置；空 env 值未覆盖继承。

意图5秒、runtime planning10秒、参数10秒、chat25秒与默认一致。没有放大 deadline，没有设置 thinking 协议参数。3个业务 chat、0个直接 chat 诊断；日志中6次规划阶段调用均 attempt1，另有READ后续回答调用。未采集 SDK 内部网络重试，不把应用阶段次数等同上游 HTTP 总次数。因默认配置已有成功结果，未进行默认 thinking 与 disabled 的直接协议比较，也未保存隐藏推理。

## 结果与首次 FAIL

| 请求 | 会话 | 总耗时 | 规划阶段实测 | 结果 |
| --- | --- | --- | --- | --- |
| course.create | 1 | 2676 ms | intent1579 ms；parameters1028 ms | 真实待确认计划，确认后课程ID1、草稿status2 |
| course_search READ | 2 | 4591 ms | intent1296 ms；parameters1932 ms | 返回该合成课程，pendingAction=null |
| chapter.create | 3 | 4114 ms | intent1630 ms；parameters2471 ms | 真实待确认计划，确认后章节ID1、courseId1 |

课程和章节各自确认前的查询均无对应数据，确认后恰有1条；重复 actionId 的课程请求为 HTTP400/code400，章节亦拒绝。学生使用不存在的合成 actionId 检查角色前置拒绝，未冒充对真实教师计划的归属授权测试。

`business-results.json` 首次 verdict=FAIL 原样保留：唯一失败项 `audit-has-own-action` 使用教师查询日志，返回空。源码 `OperationLogController.pages/visibleStudentIds` 将教师日志列表限定为其学生 user_id，因而不含教师自己的操作日志。这是测试观察权限选择错误，不是官方上游失败，也未证明审计缺失。`verify-audit.mjs` 改用管理员只读查询并按同一 actionId 精确筛选，得到1条 `agent.course.create`，其 result.courseId 与真实确认响应一致。纠正结果另存 `audit-verification.json`（PASS），未重发课程模型请求或再次创建课程。章节独立5项检查全部PASS，另存 `chapter-results.json`。

## 收尾与复用

`cleanup-results.json` 的7项检查全部PASS：仅关闭自己8082/PID34460；原8080/PID25896和5173/PID8852未变，分别HTTP401（无登录凭据的受保护接口）、HTTP200；`.env.local` 和现有 jar 与私有基线一致。公开基线仅含非秘密配置和布尔值，原文件散列移入私有目录。保留隔离的1门合成课程及1个子章节，未删除用户数据。

复用入口：`start-official.ps1`、`business-retry.mjs`、`verify-audit.mjs`、`chapter-retry.mjs`、`cleanup.ps1`。脚本不含真实秘密；首次课程脚本原有教师审计断言保留，用独立 verifier 更正。它们会在隔离库写入自身合成数据，复用时应选择新的独立私有目录/前缀，避免把已有数据当作零写入基线。本轮不再增加请求或回归。

真实应用日志为 `official.stdout.log`、`official.stderr.log`。最终文件散列及秘密字面量扫描见 `artifact-verification.json`，仅报告扫描布尔值，不输出秘密或私有 env。

## 会话证据

子会话 `01a19f04-90b4-7d60-92af-f8cb5b655d33` 已实际核对：第11行是本子代理任务 agent_message，第12行为首个工具调用；显式 `--start-line 11 --since 2026-11-03T01:27:19Z`。读取完全文件后使用仓库导出器于开始、审计验证节点和最终反馈前刷新 `session/`。每次均同时传入 `.env.local` 与 `.codex_tmp/official-deepseek-retry/redaction.env` 作为已知秘密脱敏来源，后者含本轮官方 key 与独立JWT。源记录中的父代理任务正文为平台加密字段，导出器省略该字段，不能声称其全文在明文导出中可复核；本报告保留用户原始要求及执行边界，主代理会话另记录委派。

导出不复制本机会话原件，不保存隐藏推理，仅截至指定快照，未声称设置平台自动钩子。主代理启动业务脚本的真实调用须以主代理导出记录复核，本子会话保留其明确告知与本地已落盘结果，未伪造该调用归属。
