# K01 人工验收：持久化与授权基础

依据：[已确认代码实施方案](../代码实施方案_确认需求版.docx)第4节与K01。范围为后端数据与授权基础；逐页OCR任务执行在K02，检索策略在K03，课程/课时有效范围解析在K05。

## 本次应验收的行为

| 故事 | 验收条件 |
| --- | --- |
| 私有资料 | 教师/管理员仅能操作自己拥有的库；管理员身份不能读取其他教师私有原件。旧shared不再授予读取权。 |
| 学生边界 | 后端拒绝学生创建、上传、更新、重建和删除个人库，即使数据库留有其旧个人库也不能写。 |
| 明确课程授权 | 文档所有者且有课程维护权才能grant；绑定本身不授予读取。学生还需已发布课程、有效教学班和成员资格。 |
| 独立下载权 | readExcerpt与downloadAllowed独立；仅开放摘录时不能下载原件。 |
| 文档版本 | 替换文件产生新版本；相同内容去重不递增。v1授权不自动开放v2，v1正文和原件保持原版。 |
| 索引版本 | 新索引准备完成后切换active指针；失败保留旧版。读取只用有效索引，不混入BUILDING或历史索引。 |
| 文件生命周期 | 原件存于公开uploads/media目录之外；校验大小与SHA256。失败清理可重试，删除后立刻取消资料访问。 |
| 旧库升级 | 启动执行可重复增量迁移，保留旧原件、正文、向量及会话；旧shared不会被转换成课程grant。 |

## 1. 启动

在PowerShell中，从项目根目录启动：

```powershell
Set-Location D:\ai-learning
.\scripts\competition\start-local.ps1
```

默认是隔离演示库。`-UseMySql`会使用本机私有配置中的业务库，并在启动时增量升级。本轮自动MySQL验证只创建和删除了随机临时测试库，没有升级既有业务库。

`database/native_rag.sql`是新库最终结构；`database/migrations/V001__rag_foundation.sql`与后端资源镜像由`RagSchemaMigrator`执行。迁移器在执行ADD COLUMN前检查真实元数据，不能将整份迁移SQL当成可重复的裸SQL脚本直接执行。首次回填完成后写入K01_001；后续待解析文档不会被启动过程误激活。

## 2. 准备教师与学生身份

使用两个真实测试账号：教师拥有一个课程，学生已加入该课程的有效教学班，课程已发布。课程ID可从现有课程管理界面确认。下例适用于PowerShell 7；登录令牌只保存在本机变量，不粘贴到聊天、截图或文档。

```powershell
function Login-K01($message) {
  $credential = Get-Credential -Message $message
  $body = @{username=$credential.UserName; password=$credential.GetNetworkCredential().Password} | ConvertTo-Json
  $result = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/auth/login' -ContentType 'application/json' -Body $body
  if ($result.code -ne '200') { throw '登录失败' }
  return @{Authorization='Bearer ' + $result.data.token}
}
$teacherHeaders = Login-K01 '输入测试教师账号'
$studentHeaders = Login-K01 '输入已加入课程的测试学生账号'
$courseId = [int](Read-Host '输入该教师本人拥有的已发布课程ID')
$api = 'http://127.0.0.1:8080/api/rag'
```

## 3. 私有库与学生写入限制

```powershell
$base = Invoke-RestMethod -Method Post -Uri "$api/knowledge-bases" -Headers $teacherHeaders `
  -ContentType 'application/json' -Body '{"name":"K01人工验收资料","description":"仅含合成测试文本"}'
$baseId = $base.data.id
$sampleFile = [IO.Path]::Combine([IO.Path]::GetTempPath(), 'k01-' + [guid]::NewGuid().ToString() + '.txt')
[IO.File]::WriteAllText($sampleFile, 'Gradient descent minimizes training loss.', [Text.UTF8Encoding]::new($false))
$uploaded = Invoke-RestMethod -Method Post -Uri "$api/knowledge-bases/$baseId/documents" `
  -Headers $teacherHeaders -Form @{file=Get-Item -LiteralPath $sampleFile}
$documentId = $uploaded.data.id
$uploaded.data | Select-Object id,document_version,parse_revision,active_index_version,embedding_status

$studentCreate = Invoke-RestMethod -Method Post -Uri "$api/knowledge-bases" -Headers $studentHeaders `
  -ContentType 'application/json' -Body '{"name":"不应创建的学生个人库"}'
$studentCreate.code
```

教师创建/上传应为code=200，文档version=1且有active_index_version；学生创建应为code=403。业务JSON沿用项目Result约定，不能只看HTTP200。原件下载等资源接口的拒绝使用HTTP403。

用学生或其他教师请求 `/documents/{documentId}`、`/knowledge-bases/{baseId}/documents`、`/chunks/{chunkId}`和个人库原件下载，均应拒绝。将旧shared设为true也不能使其获得读取权限；课程学习须走下一节明确的课程上下文。

## 4. 摘录、原件下载和撤销

```powershell
$grantBody = @{courseId=$courseId;documentVersion=1;readExcerpt=$true;downloadAllowed=$false} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$api/documents/$documentId/grants" -Headers $teacherHeaders `
  -ContentType 'application/json' -Body $grantBody | Select-Object code
$v1 = "$api/courses/$courseId/documents/$documentId/versions/1"
$content = Invoke-RestMethod -Uri $v1 -Headers $studentHeaders
$content.data | Select-Object document_version,file_name,content
```

预期学生能读到合成原文。访问`$v1/download`应为HTTP403。将grantBody的downloadAllowed明确改为true，再POST同一路由，可允许该学生下载；响应应带`Cache-Control: private, no-store`。管理员不能用教师私有原件路由绕过所有权。

撤销授权：

```powershell
Invoke-RestMethod -Method Delete -Uri "$api/documents/$documentId/grants/$courseId/versions/1" -Headers $teacherHeaders
Invoke-RestMethod -Uri $v1 -Headers $studentHeaders
```

撤销后正文应code=403，下载应HTTP403，响应中不能保留原文和引用内容。重新grant后，也可在教学班管理中将测试学生退班；即使grant仍在，后续课程资料访问也应被拒绝。不要用仍有有效其他教学班资格的学生验证“彻底退课”。

## 5. 文档替换与版本隔离

先恢复v1 grant，然后替换合成文件：

```powershell
[IO.File]::WriteAllText($sampleFile, 'Photosynthesis converts solar energy.', [Text.UTF8Encoding]::new($false))
$updated = Invoke-RestMethod -Method Put -Uri "$api/documents/$documentId/file" `
  -Headers $teacherHeaders -Form @{file=Get-Item -LiteralPath $sampleFile}
$updated.data | Select-Object document_version,active_index_version,duplicate
```

预期version=2且active索引改变。已有v1授权仍只能读到梯度下降文本；v2路由在明确grant version=2之前应拒绝。相同内容重复上传/替换应duplicate=true，不重复增加文档或版本。已删除来源的绑定ID会保留供K05返回失效状态，不因删除而悄悄扩大到其他资料。

## 6. 删除、失败恢复与自动测试

教师DELETE `/documents/{documentId}`或`/knowledge-bases/{baseId}`后，所有版本的文档读取、原件和授权应失效。物理文件删除失败会保留在`native_rag_file_cleanup`，重启时重试，不会因为数据库已删除就把私有文件公开。

写入前有STAGING持久化意图。未提交且超过10分钟的中断写入，在启动清理时恢复；同原件关联的合法`.objectKey.writerUUID.partial`也会清理。被有效文档引用的原件不会删除。当前清理入口是启动过程和内部维护方法，没有新增公开清理API或后台定时任务。

```powershell
Set-Location D:\ai-learning\ai-learning-backend
mvn test
```

普通回归不调用付费模型，也不创建真实MySQL测试库。真实验证均需显式开关：`K00_LIVE_MODEL_CHECK=true`运行`RuntimeModelLiveTest#actualEmbeddingIsPersistedAndUsedInNativeRagRetrieval`；`K01_MYSQL_CHECK=true`运行`K01MySqlMigrationLiveTest`。后者需要进程中DB_USERNAME/DB_PASSWORD，只使用本机3306的随机新建隔离schema，并核对自建标记后删除，不使用DB_URL指定的正式库。新终端需要先加载自己的私有变量。

Windows当前账号不能创建符号链接时，相应用例会明确跳过。可在有权限的隔离环境重跑`RagFileStoreTest`核对链接防护；不能将跳过算作通过。文件发布要求底层文件系统支持硬链接，当前本机正常发布/并发不覆盖由实际测试验证。

## 后续边界

- page、ingestion job、binding和generation evidence已建基础结构；逐页OCR执行、任务恢复调度及质量在K02实施。
- K03继续改检索融合、模型指纹与质量评测；本轮索引版本切换不等于语义质量验收。
- K05实现课时继承/覆盖、锁定和完整有效范围解析；K01只提供课程资格和精确文档grant基础。
- K04继续完成历史正文/标题/引用及流式输出的完整最新权限处理；不能把本轮基础授权描述成全链路已完成。
- 前端个人库入口和课程资料选择尚未在K01调整，后端先强制执行新权限。
