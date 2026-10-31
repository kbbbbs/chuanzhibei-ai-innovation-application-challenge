# K02 逐页解析与OCR：实施和验证报告

日期：2026-10-25。依据：[已确认实施方案](../代码实施方案_确认需求版.docx)第4、5节及K02，承接K01。

后续增量：2026-10-28 的公式结构、显示与复验见 [公式专项报告](K02-formula-test-report.md)。下文保留 10 月 25 日的实际结果，不用新的少量样本覆盖历史 334 页统计。

同日补充的私有裁图、图文/题号关联及回答读图见 [图文专项报告](K02-visual-test-report.md)。

**K02代码和本轮流程验收完成。** 后端303项中291项通过、12项跳过，0失败/错误；UI 10项通过，前端构建通过，真实MySQL和真实课程OCR单独开启后通过。原件有2页被模型截断，明确保留为PARTIAL，不能声称334页内容全部可用。

## 1. 故事、验收与代码对应

| 故事/方案模块 | 实现位置与可见行为 | 验证 |
| --- | --- | --- |
| S01/O03/O04 逐页提取 | RagPageExtractor、PageAnalysisService、PdfPageRenderer；PDF按页、PPTX按幻灯片，DOCX/TXT/MD按字符位置；20 MB/200页；PDF旋转元数据交由PDFBox渲染 | 格式/坏文件/超限、真实页码/文本位置、分组PPTX兼容；像素与全局2渲染槽保护 |
| S02/O01/O02/O05 独立OCR | OcrProvider/Request/Result、Registry、ModelAdapter、ModelClient；复用RuntimeModelProperties的独立OCR配置，实际为deepseek-ai/DeepSeek-OCR | 真实付费调用；响应体/图像容量、超时、有限重试；缺配置准确失败，不使用GPT或WeKnora兜底 |
| S03/O06 结果与分块 | OcrResultNormalizer、RagTextChunker；原始文字、原始OCR、整理文本、检索文本分开；ref正文保留/坐标去除；跨页不合块 | 真实模型格式回归、重复表格行保留、字符范围、截断不缓存/不入索引；置信度未知保持null |
| S04/O07 异步与恢复 | RagIngestionService、IngestionJobRepository、RagIngestionWorker；上传返回documentId/jobId，检查点、租约、heartbeat、fencing、取消、限定页重试 | 队列/过期恢复、活动调用中断、并发旧worker不能覆盖完成状态；MySQL TIMESTAMP实测 |
| S05 索引发布 | 完成后事务切换active；替换/失败/取消保留旧原件和有效索引；有缺页显示PARTIAL | 发布/取消与版本保护、无重复片段、空白/失败/截断文本不进入索引 |
| S06/O08 教师核验 | DocumentProcessingStatus、DocumentPageReview、NativeKnowledgeWorkspace；进度、缺页、原页、逐页强制识别；解析重试与向量重建分开 | 5项新UI与5项原生RAG回归；Bearer私有预览、待处理v2不会展示旧v1、文本不编造PDF页 |

选择页重试只重新处理选定页，其他成功、空白或失败检查点保持原结果；最近一次修订被取消且没有检查点时，按页复用更早的最新结果。若历史尚不足覆盖完整文档，返回409并提示“重新解析全部”，避免默默扩大识别范围。

V002__rag_ingestion.sql与迁移器增加任务/页字段，保留K01资料与索引。关键接口：

| 路由 | 行为 |
| --- | --- |
| POST `/api/rag/knowledge-bases/{id}/documents`、PUT `/api/rag/documents/{id}/file` | 保存私有原件并排队；返回documentId/jobId |
| GET `/api/rag/jobs/{id}`、POST `/api/rag/jobs/{id}/cancel` | 查询阶段/缺页/核验页及取消 |
| POST `/api/rag/documents/{id}/parse-retry` | `{pages:[...],forceOcr:true/false}`；空数组表示全部 |
| GET `/api/rag/documents/{id}/pages?version=...` | 指定源版本的页/字符位置检查点 |
| GET `/api/rag/documents/{id}/pages/{page}/preview?version=...` | 需鉴权PNG，private/no-store/nosniff |
| POST `/api/rag/documents/{id}/reindex` | 仅重建向量索引 |

所有接口继续使用K01可信actor及所有权校验，学生个人库写入不开放。本链路执行项目自建模块；旧WeKnora代码不作为K02验收依据。

## 2. 最终验证结果

| 验证 | 实际结果 | 证据 |
| --- | --- | --- |
| 后端全量 | 303项，291通过，12跳过，0失败/错误 | [完整日志](K02-verification-20261025/logs/full-backend-final.log) |
| UI | 10通过，使用受控HTTP数据；不冒充真实OCR端到端 | [日志](K02-verification-20261025/logs/green-ui-accepted.log) |
| 前端构建 | 通过 | [日志](K02-verification-20261025/logs/frontend-build-accepted.log) |
| 导出工具 | 13通过；验证秘密/内部元数据/图像像素过滤 | [日志](K02-verification-20261025/logs/green-record-final.log) |
| 真实MySQL | 随机自有隔离库PASS；迁移、LONGTEXT、版本/清理、K02队列/租约/fencing | [安全结果](K02-verification-20261025/mysql-final.json)；未修改业务库 |
| 真实OCR | PASS；8原件+20扫描+3混合检查点，坏/超限400；最多2 worker | [安全结果及来源manifest](K02-verification-20261025/course-materials-final.json) |

普通全量的12项跳过：5项K00真实模型探针未开启；1项MySQL、1项课程OCR为显式门控（本轮另行开启通过）；1项旧WeKnora集成；4项Windows符号链接因权限条件跳过。跳过不计通过。各定向测试与全量有重叠，不能累计成更多独立测试。

真实MySQL仅用合成文本/向量验证数据库，不调用模型。课程OCR使用隔离H2和临时私有原件，禁用Embedding，不据此声称K03真实混合检索已完成。课程实测耗时647495毫秒；扫描20页副本3158538字节，144 dpi、JPEG质量0.9，来自8份原件第1/14页与卷积第2/3/15/16页。

## 3. 真实课程资料结果

| 原件 | 检查点 | 文字层就绪 | OCR就绪 | 缺失页 | 文档状态 |
| --- | --- | --- | --- | --- | --- |
| 第1章-课程介绍-绪论-okk.pdf | 46 | 24 | 22 | 无 | READY |
| 第2章-机器学习概述-okk.pdf | 36 | 9 | 27 | 无 | READY |
| 第3章-线性模型-okk.pdf | 40 | 9 | 30 | 23（OCR_OUTPUT_TRUNCATED） | PARTIAL |
| 第4章-前馈神经网络-okk.pdf | 38 | 17 | 20 | 23（OCR_OUTPUT_TRUNCATED） | PARTIAL |
| 第5章-卷积神经网络-okk.pdf | 34 | 13 | 21 | 无 | READY |
| 第6章-循环神经网络-okk.pdf | 37 | 20 | 17 | 无 | READY |
| 第7章-深度生成模型.pdf | 44 | 10 | 34 | 无 | READY |
| 第9章 人工智能-语言模型.pdf | 59 | 0 | 59 | 无 | READY |

原件共334页：102页直接提取、230页OCR就绪，2页截断；8份均有有效索引，其中6份READY、2份PARTIAL。原件散列未变化。截断的第3/4章第23页均包含公式，已目视原图；服务finish_reason=length，只能证明本次输出未完成，不能把截断文本作为完整教材内容。

扫描副本20/20为OCR_READY、0失败、20片段；完整识别结果保存在忽略的私有target，公共[逐页统计](K02-verification-20261025/scan-page-statistics.json)只有状态/字数等信息。混合3页分别TEXT_READY、OCR_READY、BLANK，仅2片段进入索引；坏PDF、超过20 MB、201页均返回400且不新增任务。

已目视卷积第14页原图，并检查扫描副本第10页的真实263字符结果，检出“核心逻辑、三维结构、单核、多核、特征、通道、RGB、Feature Map”。[抽查观察](K02-verification-20261025/cnn14-review-observations.json)只证明这些词项存在，不代表逐字审校或图意理解。所有OCR页仍标需核验；不从调用成功、非空字符或词项命中推算准确率。

## 4. 真实失败与修正链

1. 先写分页/任务/HTTP及UI测试，缺少实现产生编译或接口红灯；实现后定向43项通过。初期部分UI红灯来自测试拦截Vite模块的fixture错误，已如实保留，不能冒称功能红灯。
2. 合并OCR行最初删除重复表格行；真实回归红灯后改为保持OCR序列，只去重补入的原生文字层。
3. 首次真实课件耗时562256毫秒，334页中229页OCR_EMPTY_RESULT，仅3页OCR就绪，语言模型整份FAILED。原始[失败统计](K02-verification-20261025/course-materials-initial.json)与[失败摘要](K02-verification-20261025/initial-failure-summary.json)保留。
4. 同页3提示诊断确认原提示的正文在ref内；原实现误将正文整段删除。合成替代正文回归先红，再修为保留非布局标签的ref内容、去坐标、分行及幂等；保留原产品提示，不换GPT兜底。规范化失败也保留原始返回供私有核验。
5. 显式截断原先被标OCR_READY；独立红灯后传递errorCode，保存失败原文但检索文本留空，不缓存、不发布。旧worker跨事务覆盖READY也先以竞态测试重现，再将进度/文档状态放入同一外层事务。
6. PDFBox会吞Do操作的IOException，源图像护栏测试因此真实失败；改为能穿透的策略拒绝。PDF/PPTX共享全局2渲染槽，PPTX只读图像头预检。全量又发现缺分组几何导致PPTX文本读取失败，保留旧测试并修复；旧警告文案断言也更新为当前准确含义。
7. 选定页重试的3项回归真实失败：连带重试其他失败页、空取消修订遮蔽旧检查点、无完整历史仍接受选择重试。修正后纳入本轮最终全量。

所有失败日志与修正后结果均保留于[验证目录](K02-verification-20261025/)。没有把fixture故障、编译、模拟协议、真实服务或人工核验混为一类通过。

## 5. 人工验证与边界

按[K02人工验证方法](K02-manual-verification.md)重新启动后端与前端，用教师上传、核对页码/原图/原始OCR/整理文字，测试限定页重试、取消和旧版本保护；优先检查卷积第14页及上述两页截断。当前4096输出token预算下，复杂公式页仍可能截断，需要教师核验并拆分后重试或后续专项调优。

DOCX/TXT/MD按字符位置；DOCX内纯图片因没有可渲染的真实页面会明确失败并提示核验。扫描PDF与PPTX按真实页渲染；任意倾斜扫描校正、复杂公式精确还原及图意理解不作为已完成能力。未知置信度为null，需核验提示保持可见。

K03检索融合/质量、K05课时覆盖和K04生成证据/历史流式撤权继续按后续任务实施。

## 6. 开发过程记录与版本

根与3个子Agent真实JSONL均脱敏导出：[根](agent-records/2026-10-25-k02/root/records.md)、[分页/OCR](agent-records/2026-10-25-k02/k02_page_pipeline/records.md)、[数据/测试](agent-records/2026-10-25-k02/k02_job_persistence/records.md)、[前端](agent-records/2026-10-25-k02/k02_processing_ui/records.md)。子会话实际task_started起始行分别506、509、512，剔除fork继承前缀；manifest保存源时间/行号/call_id及源快照散列。

[最终相关文件散列](K02-verification-20261025/final-file-hashes.json)用于复核源码、测试和结果。公开记录不保存私有密钥、系统/开发者指令、隐藏分析、完整课件正文或图像像素。导出为明确快照，没有平台自动钩子；最后导出之后的调用和最终回复留待下次刷新补入。过程证据可用于整理P01真实迭代链，不自动等于已满足3条链或参赛资格审核通过。
