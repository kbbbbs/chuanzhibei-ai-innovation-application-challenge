# K02 人工验证：异步解析与逐页OCR

日期：2026-10-25。本轮代码、自动化与真实服务结果见[K02验证报告](K02-test-report.md)。以下步骤供教师人工验收，不能把自动测试代替内容审校。

2026-10-28 已增加公式保存与排版。新能力及旧资料升级请同时参照 [公式增量人工验证](K02-formula-manual-verification.md)；以下课程统计仍是 10 月 25 日的历史记录。

图片与试卷题号整理另见 [图文增量人工验证](K02-visual-manual-verification.md)，需重新解析旧资料。

## 1. 启动与模型配置

```powershell
Set-Location D:\ai-learning
.\scripts\competition\start-local.ps1
```

另开一个终端启动前端：

```powershell
Set-Location D:\ai-learning\ai-learning-frontend
npm run dev
```

按前端终端显示的地址打开（项目默认24678端口；自动化UI测试使用5173端口）。

默认使用隔离演示库。后端需重新构建并重启才能使用新代码；后台worker默认启用，最多处理2份文档。可通过`rag.ingestion.worker-enabled=false`关闭自动任务，用于受控测试；日常验收应启用。

OCR独立使用`OCR_BASE_URL`、`OCR_MODEL`和`OCR_API_KEY`；密钥兼容`OCR_APIKEY`，最后取`SILICONFLOW_API_KEY`。不使用通用DeepSeek聊天密钥替代、不使用GPT、旧WeKnora或其他OCR引擎静默兜底。不要将密钥粘贴到聊天、截图或本文。

模型配置检查通过仍不等于课程OCR通过。可先用管理员模型探针核对图片接口，但课程级的扫描、混合页、原页定位和内容核验仍必须单独做。本轮已修复ref正文被清空的问题，并完成8原件、20扫描页及混合页流程复验；第3/4章第23页仍有模型输出截断，均明确PARTIAL且不入索引。

## 2. 教师界面验收

使用资料所有者教师账号进入现有知识库的“资料与出处”页面：

1. 上传PDF、DOCX、PPTX、MD或UTF-8 TXT。确认“上传成功”后显示排队/提取/OCR/分块/索引等真实状态，而不是立即显示已解析。
2. 确认进度、失败页与需核验页可见。任务COMPLETED但文档PARTIAL时，仍显示“部分可用”和缺失页，不显示完整覆盖。
3. 打开“逐页核验”，查看所核验的源版本。替换资料处理中的核验页应取待处理新版本，不能展示旧可用版本冒充新稿。
4. 对照原页、文字层原文、模型原始结果、整理文本和用于检索的文本。没有置信度时显示“服务未提供置信度”。
5. 对失败位置选择“重试此页/位置”；对真实PDF/PPTX页可选“强制识别本页”。其他成功页可以复用，但失败提示不能进入检索正文。
6. 区分“重新解析全部”和“重建向量索引”。OCR失败应重试解析，向量重建不能修复空识别内容。
7. 处理中点“取消处理”，应显示取消请求，最终CANCELLED；不能把停止处理标为解析完成。替换失败或取消时，旧可用版本仍保留。

DOCX/TXT/MD按字符位置核验，不应出现编造的PDF页码和图像预览。旧DOC/PPT应被拒绝；大于20 MB以及PDF/PPTX大于200页应被拒绝，不应留下可用假文档。

## 3. API验收

以下适用于PowerShell 7。使用自己的测试教师账号，登录令牌只存在本机变量：

```powershell
$credential = Get-Credential -Message '输入资料所有者的测试教师账号'
$loginBody = @{username=$credential.UserName;password=$credential.GetNetworkCredential().Password} | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/auth/login' -ContentType 'application/json' -Body $loginBody
if ($login.code -ne '200') { throw '教师登录失败' }
$headers = @{Authorization='Bearer ' + $login.data.token}
$api = 'http://127.0.0.1:8080/api/rag'
$library = Invoke-RestMethod -Method Post -Uri "$api/knowledge-bases" -Headers $headers `
  -ContentType 'application/json' -Body '{"name":"K02人工验收","description":"逐页解析与OCR测试"}'
$baseId = $library.data.id
$file = Get-Item -LiteralPath (Read-Host '输入待验收文件的完整路径')
$upload = Invoke-RestMethod -Method Post -Uri "$api/knowledge-bases/$baseId/documents" -Headers $headers -Form @{file=$file}
$documentId = $upload.data.documentId
$jobId = $upload.data.jobId
$upload.data | Select-Object documentId,jobId,processing_state,active_index_version
```

预期返回code=200和两个ID，表示原件及任务已保存，**不是OCR已成功**。轮询任务：

```powershell
$job = Invoke-RestMethod -Uri "$api/jobs/$jobId" -Headers $headers
$job.data | Select-Object stage,progress,totalPages,completedPages,failedPages,needsReviewPages,documentState,errorCode
```

阶段终态为COMPLETED/FAILED/CANCELLED；文档另区分READY/PARTIAL/FAILED等。必须同时检查文档状态、失败页和可用索引，不能只看HTTP200或stage=COMPLETED。

查看指定源版本的检查点：

```powershell
$version = $job.data.documentVersion
$pages = Invoke-RestMethod -Uri "$api/documents/$documentId/pages?version=$version" -Headers $headers
$pages.data | Select-Object pageNo,pageNumber,pageStatus,method,confidence,errorCode,needsReview
```

`pageNo`是处理单元编号；`pageNumber`在非页式资料中为空。页面正文与OCR结果是私有教学资料，核验时不要将完整返回值发布到公共日志或仓库。

指定页重试：

```powershell
$retryBody = @{pages=@(14);forceOcr=$true} | ConvertTo-Json
$retry = Invoke-RestMethod -Method Post -Uri "$api/documents/$documentId/parse-retry" `
  -Headers $headers -ContentType 'application/json' -Body $retryBody
$jobId = $retry.data.jobId
```

此例仅用于确有第14页的PDF/PPTX；空pages表示全部重新解析。重试产生新的parse revision，不应重复插入同一版本/修订/页检查点。选定页重试保持其他失败页原状；如历史检查点尚不完整会返回409，改用空pages重新解析全部。选定页重试保持其他失败页原状；如历史检查点尚不完整会返回409，改用空pages重新解析全部。处理中取消：

```powershell
Invoke-RestMethod -Method Post -Uri "$api/jobs/$jobId/cancel" -Headers $headers
```

另用学生/其他教师账号请求同一任务、检查点、原页预览和重试，应被拒绝；原页PNG路由为`/documents/{id}/pages/{page}/preview?version=...`，必须带鉴权，响应private/no-store，不能通过公开uploads目录读取。

## 4. 原件、扫描与混合页验收

计划使用`D:\PPT\人工智能`中的8份原始PDF，逐份记录原页数、检查点、失败页、可用索引和状态。原件保持不变，文件散列用于证明来源。

受控真实用例另外从每份原件第1、14页生成16页扫描副本，再加卷积PDF第2、3、15、16页，合计20页；144 dpi、JPEG质量0.9，副本仍需小于20 MB。它们是从真实原页制作的测试副本，不冒称原文件本身就是扫描件。manifest应记录原文件散列、原页码、对应副本页与副本散列。

混合3页由合成文字层、卷积PDF第14页扫描副本和空白页组成。预期分别保留文字、触发真实OCR以及空白状态；空白页不能将提示文案写成教材内容。

**最终实测：**334原页有102页文字层、230页OCR就绪、2页截断；20扫描页全部OCR_READY；混合页为文字/OCR/空白，坏文件与两种超限均400。首次失败与最终结果分开保留，来源manifest见报告链接。优先人工核对卷积14页和第3/4章23页，截断文本不会作为完整内容索引。

## 5. 取消、崩溃恢复与旧版本保护

- 正常取消：任务最终CANCELLED，不发布新索引；对已发布文档做替换后取消，原可用版本仍可读。
- 进程重启：已提交原件和页检查点保留；过期租约可重新领取，旧worker token不能写入或完成。活跃租约不被另一worker抢占。
- 页重试：成功页可复用，指定失败页重新处理，active指针只在新结果准备完成后切换。
- 发布/取消竞态：取消与最终版本/索引切换需要在同一任务锁顺序下完成；文档状态也不能被过期worker改回处理中。独立竞态测试已重现旧worker写回PARSING，修正后通过。当前租约180秒，异常退出后应等旧租约过期再观察恢复。

## 6. 重跑测试与当前边界

```powershell
Set-Location D:\ai-learning\ai-learning-backend
mvn test
Set-Location D:\ai-learning\ai-learning-frontend
npx playwright test e2e/k02-processing-ui.spec.js e2e/native-rag-ui.spec.js
npm run build
```

普通后端测试不应触发真实OCR。显式设置`K02_LIVE_OCR_CHECK=true`后单独运行`K02CourseMaterialLiveTest`会调用付费服务，并使用私有课程资料；应先确认该验证进程已加载独立OCR环境。该用例禁用Embedding，仅使用隔离H2和临时原件目录。

真实用例的统计写在忽略的`target/k02-live/course-materials.json`；原页PNG、完整识别文本和扫描PDF只留在私有target中，不复制到公开docs。公开报告仅保存安全统计和散列。没有文字逐字标注真值时，不报告OCR准确率百分比。

详细计数、真实MySQL清理、跳过原因及抽查观察均见验证报告。K03检索质量、K05课程课时范围和K04生成/历史/流式授权仍按后续任务完成。
