# 开发 Agent 参赛证据索引

用户于2026-10-21要求每步开发 Agent 使用记录，同轮确认先只记录开发过程。工作区根 `AGENTS.md` 已加入该要求。这里保留真实需求、提示、Agent可见说明、工具/子Agent操作、修改、测试失败与修正、验证结果和文件版本；不导出内部分析、密钥或系统指令。

## 当前记录

| 日期与任务 | 内容 | 状态 |
| --- | --- | --- |
| 2026-10-20 K00 | 已有 `K00-test-report.md`、人工验证与散列快照；这些是当时结果记录，不能冒称完整原始 Prompt 链 | 历史结果保留；原始会话可按实际时间另行归档 |
| 2026-10-21 SiliconFlow配置与记录工具 | 用户原始需求、仅开发日志与密钥留空的确认、配置红绿测试、后端回归、子Agent开发记录导出工具 | [根会话记录](2026-10-21-siliconflow-configuration/root/records.md)、[子Agent记录](2026-10-21-siliconflow-configuration/development_recording/records.md)；子记录已剔除继承前缀 |
| 2026-10-22 密钥及接口真实验证 | 兼容变量别名、图片OCR适配、4096维向量、MySQL列容量与精确迁移、真实调用及回归 | [验证报告](../API-verification-20261022.md)、[根会话](2026-10-22-api-verification/root/records.md)、[接口与向量子Agent](2026-10-22-api-verification/api_client_audit/records.md)、[OCR子Agent](2026-10-22-api-verification/ocr_http_client/records.md)；两个子会话均按实际任务边界剔除继承内容 |
| 2026-10-22 K01持久化与授权基础 | 严格actor、私有文件、文档/索引版本、增量升级、课程grant、失败补偿及真实MySQL/Embedding复验 | [K01报告](../K01-test-report.md)、[根记录](2026-10-22-k01/root/records.md)、[授权](2026-10-22-k01/k01_authorization_audit/records.md)、[数据层](2026-10-22-k01/k01_data_audit/records.md)、[文件层](2026-10-22-k01/k01_file_store/records.md)；子记录边界已核对 |
| 2026-10-25 K02逐页解析与OCR | 异步任务、检查点、限定页重试、原页核验、真实课程/扫描/混合页、失败修正与回归 | [K02报告](../K02-test-report.md)、[根](2026-10-25-k02/root/records.md)、[分页/OCR](2026-10-25-k02/k02_page_pipeline/records.md)、[数据/测试](2026-10-25-k02/k02_job_persistence/records.md)、[前端](2026-10-25-k02/k02_processing_ui/records.md)；子边界已核对 |
| 2026-10-27 K02公式处理核查 | 核对文字层、OCR保留、分块、检索上下文与显示；只读检查，未改产品代码或重跑测试 | [根记录](2026-10-27-k02-formula-audit/root/records.md)、[核查与散列](2026-10-27-k02-formula-audit/audit.json) |
| 2026-10-27 公式处理业界方案调研 | 查阅官方文档，区分公式结构化识别与视觉检索；提出建议，未改代码 | [根记录](2026-10-27-formula-industry-review/root/records.md)、[来源与范围](2026-10-27-formula-industry-review/research.json) |
| 2026-10-28 K02公式增量 | 公式结构与来源、数学OCR路由、完整分块/上下文、本地排版、真实失败修复与内容问题留档 | [测试报告](../K02-formula-test-report.md)、[根记录](2026-10-28-k02-formulas/root/records.md)；无子Agent；12页流程通过不等于内容全部正确 |
| 2026-10-28 K02图文增量 | 私有裁图/整页兜底、图文及题号关联、引用图片、回答读图、真实曲线估值失败与拦截、回归 | [测试报告](../K02-visual-test-report.md)、[根](2026-10-28-k02-visuals/root/records.md)、[布局审查](2026-10-28-k02-visuals/k02_visual_layout/records.md)、[回答](2026-10-28-k02-visuals/k02_visual_chat/records.md)、[前端](2026-10-28-k02-visuals/k02_visual_ui/records.md)；3个子会话实际边界已核对 |
| 2026-10-30起 K03混合检索 | 授权有效快照、批量向量协议、RRF、兼容性与实际降级、来源版本展示；初验失败保留，预登记调参后一次续验33/40（82.5%），未达85%，不宣称语义更优 | [报告](../K03-test-report.md)、[根](2026-10-30-k03/root/records.md)、[核心](2026-10-30-k03/k03_retrieval_core/records.md)、[评测](2026-10-30-k03/k03_evaluation/records.md)、[前端](2026-10-30-k03/k03_ui/records.md)、[调参与续验](2026-10-30-k03/k03_tuning_finish/records.md)、[页面收尾](2026-10-30-k03/k03_ui_finish/records.md)、[记录审查](2026-10-30-k03/k03_record_audit/records.md)；10月31日收尾，按各自实际起点导出 |
| 2026-10-31 GitHub协作交接 | 新私有仓库、安全快照上传、README/32项配置说明、24条K02/K03人工验收与迭代流程、启动脚本和记录过滤边界先RED后修复；新副本397项后端、22项Python、前端构建/8渲染、隔离启动验证 | [发布核验](../GitHub-publication-20261031/verification.json)、[根](2026-10-31-github-collaboration/root/records.md)、[README/配置](2026-10-31-github-collaboration/github_readme_setup/records.md)、[三部分协作文档](2026-10-31-github-collaboration/collaboration_validation_doc/records.md)、[上传审查](2026-10-31-github-collaboration/github_upload_audit/records.md)；不将人工待测计为PASS，不推含凭证的旧历史 |

记录通过真实本机会话 JSONL 显式导出；不存在已经安装的平台自动记录钩子。每轮最终反馈前刷新快照，并标记源会话、原行号、时间范围和源文件散列。运行中的最后一段记录可能尚未写入，按快照如实说明；必要时下一轮刷新，不补造缺失步骤。

## 与参赛材料的对应

2026-10-21再次读取10037/10039官方公开详情接口，正文散列与前次保存一致。10039要求保存至少3条完整的核心 Prompt 迭代链，并在5–8分钟1080p演示中包含至少2分钟AI协同过程；技术PDF不超过30页。[10039官方详情](https://www.boxuegu.com/matchTrack/detail/?id=10039)

原实施方案约定的证据链继续使用：

| 链 | 核心内容 | 当前状态 |
| --- | --- | --- |
| P01 OCR资料链 | K01/K02，逐页识别、错误、修复、来源与验证 | 10月25日逐页OCR及10月28日公式专项真实失败/修复/12页复验，保留漏式、漏λ和等式改写观察；P01可按真实记录整理，不自动计作全部3条链完成 |
| P02 检索与回答链 | K03/K04，语义召回、范围、真实引用、失败及修复 | K03已有真实课程59页/59片段、批量协议失败与修复、默认RRF未达标成绩、仅调参题选配置的真实过程；详见K03报告。K04回答/拒答尚未验收，过程日志不自动等于整条链完成 |
| P03 教学与权限链 | K05至K10，课时覆盖、发布、私有资料、学生入口 | 后续实施时从实际需求开始同步记录 |

配置步骤与日志文件不能重复计作3条完整链。后续每条链应能对应“用户意图/原Prompt → Agent改动 → 真实失败 → 修正Prompt/改动 → 验证 → 成品版本”，再从真实记录中节选录屏与PDF材料。日志齐全不等于参赛资格、旧基座或跨赛复用问题已获主办方确认。

官方规则证据：10037正文SHA-256为 `28c5a2dd54a8d09e5320062d2823da30960b623311f9952851c2ff2182bbb1f7`；10039为 `bc11a4e53d71a9e25d0a743e998d998cca832f1e93108f7219223d66398836ae`。公开接口原响应保存在本机任务输出目录；不把接口抓取时间冒充事件时间。
