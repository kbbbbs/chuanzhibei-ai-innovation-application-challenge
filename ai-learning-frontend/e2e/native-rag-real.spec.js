import { test, expect } from '@playwright/test'
import { writeFile } from 'node:fs/promises'

// Independent contest-demo database only. No request routing or model stubs are used.
test.use({ trace:'off' })
const api = process.env.RAG_REAL_API || 'http://127.0.0.1:8080'
const teachingText = '# 二分查找教学示例\n\n二分查找要求数组已经按照升序排列。先检查中间元素，如果目标较小则搜索左半区间，如果目标较大则搜索右半区间，每次将搜索区间缩小一半。\n\n时间复杂度为 O(log n)。对于升序数组 [2, 4, 6, 8, 10, 12, 14]，查找目标 10 时，先比较中间的 8，然后搜索右半区间。空数组应返回未找到；目标不存在时也必须终止。\n\n这是为联测编写的合成教学资料，未包含任何真实学生个人信息或课程成绩。\n'

async function login(request, username) {
  const response = await request.post(`${api}/auth/login`,{data:{username,password:process.env.RAG_DEMO_PASSWORD || '123456'}})
  const payload = await response.json()
  expect(String(payload.code),`${username} login response code`).toBe('200')
  expect(Boolean(payload.data?.token),'demo login returned a token').toBeTruthy()
  return payload.data
}
async function setLogin(page,user) {
  await page.addInitScript(data => {
    for (const key of ['token','userId','username','nickname','roleCode']) localStorage.setItem(key,String(data[key] ?? ''))
  },user)
}
const authorized = user => ({Authorization:`Bearer ${user.token}`})
async function jsonCall(request,user,path,method='get',data) {
  const response = await request[method](`${api}${path}`,{headers:authorized(user), ...(data ? {data} : {})})
  return {http:response.status(),body:await response.json()}
}

test('real document upload, retrieval, stream citations, history and shared access revocation',async ({page,request,browser},testInfo) => {
  test.setTimeout(240000)
  page.setDefaultTimeout(15000)
  const teacher = await login(request,'teacher')
  const student = await login(request,'student')
  await setLogin(page,teacher)
  const errors = []
  page.on('pageerror',error=>errors.push(error.message))
  const externalRequests = []
  page.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/backend/api/weknora')) externalRequests.push(new URL(r.url()).pathname)})
  let kbId,docId,sessionId,studentContext
  const evidence = {kind:'real-service-integration',syntheticTeachingContent:true,requestMocks:false,modelMocks:false,checks:{}}
  try {
    await page.goto('/teacher-knowledge-base')
    await page.getByRole('button',{name:'新建',exact:true}).click()
    const dialog = page.getByRole('dialog',{name:'新建知识库',exact:true})
    const name = `联测示例-二分查找-${Date.now()}`
    await dialog.locator('input').fill(name)
    await dialog.locator('textarea').fill('用于验证资料、引用与共享权限的合成教学资料')
    const createResponse = page.waitForResponse(r=>new URL(r.url()).pathname==='/backend/api/rag/knowledge-bases'&&r.request().method()==='POST')
    await dialog.getByRole('button',{name:'保存',exact:true}).click()
    const created = await (await createResponse).json()
    expect(String(created.code)).toBe('200'); kbId=created.data.id
    await expect(page.locator('.workspace-heading')).toContainText(name)
    await expect(page.getByText('此知识库为私有：只有你可以访问。共享前请检查资料中的个人信息。')).toBeVisible()
    const uploadResponse = page.waitForResponse(r=>new URL(r.url()).pathname===`/backend/api/rag/knowledge-bases/${kbId}/documents`&&r.request().method()==='POST')
    await page.locator('input[type=file]').setInputFiles({name:'binary-search.md',mimeType:'text/markdown',buffer:Buffer.from(teachingText,'utf8')})
    const uploaded = await (await uploadResponse).json()
    expect(String(uploaded.code)).toBe('200'); docId=uploaded.data.id
    await expect(page.getByText('解析完成',{exact:true})).toBeVisible()
    evidence.checks.upload = {status:uploaded.data.parse_status || uploaded.data.parseStatus,chunkCount:uploaded.data.chunk_count}

    await page.getByRole('tab',{name:'检索与验证'}).click()
    await page.getByPlaceholder('输入关键词或问题，查看实际命中的原文').fill('二分查找')
    const searchResponse = page.waitForResponse(r=>new URL(r.url()).pathname==='/backend/api/rag/search')
    await page.getByRole('button',{name:'检索',exact:true}).click()
    const searched = await (await searchResponse).json()
    expect(String(searched.code)).toBe('200')
    expect(searched.data.hits.length).toBeGreaterThan(0)
    expect(searched.data.hits.every(hit=>hit.documentId===docId)).toBeTruthy()
    await expect(page.locator('.search-hit').first()).toContainText('搜索区间缩小一半')
    evidence.checks.retrieval={mode:searched.data.mode,hitCount:searched.data.hits.length,warning:searched.data.warning || ''}

    await page.getByRole('tab',{name:'知识问答'}).click()
    await page.getByPlaceholder('例如：这节课的核心概念是什么？').fill('资料中二分查找有什么前提，为什么它的时间复杂度是 O(log n)？请引用原文。')
    await page.getByRole('button',{name:'发送',exact:true}).click()
    await expect(page.locator('.chat-message.assistant .rag-citations').last()).toBeVisible({timeout:120000})
    await expect(page.getByRole('button',{name:'停止生成',exact:true})).toHaveCount(0,{timeout:15000})
    const sessionList = await jsonCall(request,teacher,'/api/rag/sessions')
    sessionId=sessionList.body.data.find(item=>item.knowledgeBaseId===kbId)?.id
    expect(Boolean(sessionId)).toBeTruthy()
    const history = await jsonCall(request,teacher,`/api/rag/sessions/${sessionId}/messages`)
    const answer = history.body.data.findLast(message=>message.role==='assistant')
    expect(Boolean(answer?.content)).toBeTruthy()
    expect(answer?.citations?.length || 0).toBeGreaterThan(0)
    expect(answer.citations.every(source=>source.documentId===docId)).toBeTruthy()
    evidence.checks.answer={status:answer.status,mode:answer.mode,citationCount:answer.citations.length,historyMessageCount:history.body.data.length}

    await page.locator('.chat-message.assistant .rag-citations .el-button').first().click()
    await expect(page.locator('.source-text')).toContainText('二分查找')
    const fileDownload = page.waitForEvent('download')
    await page.getByRole('button',{name:'下载原始文件',exact:true}).last().click()
    const file = await fileDownload
    expect(file.suggestedFilename()).toBe('binary-search.md')
    expect(await file.failure()).toBeNull()
    evidence.checks.authenticatedDownload=true
    await page.keyboard.press('Escape')
    await expect(page.locator('.source-text')).not.toBeVisible()
    await page.screenshot({path:testInfo.outputPath('teacher-real-answer.png'),fullPage:true})
    await page.goto(`/teacher-knowledge-base?kbId=${kbId}`)
    await page.getByRole('tab',{name:'知识问答'}).click()
    await page.locator('.chat-toolbar .el-select').click()
    await page.getByRole('option').first().click()
    await expect(page.locator('.chat-message.assistant .rag-citations')).toBeVisible()
    evidence.checks.historyRestoredInUi=true

    await page.getByRole('button',{name:'编辑与共享',exact:true}).click()
    const editDialog=page.getByRole('dialog',{name:'编辑与共享知识库',exact:true})
    await editDialog.locator('.el-switch').click()
    await editDialog.getByRole('button',{name:'保存',exact:true}).click()
    await expect(page.getByText('此知识库已共享：平台内已登录用户可读取，只有所有者可以修改。')).toBeVisible()
    studentContext=await browser.newContext({baseURL:testInfo.project.use.baseURL,acceptDownloads:true})
    const studentPage=await studentContext.newPage()
    await setLogin(studentPage,student)
    await studentPage.goto(`/knowledge-base?kbId=${kbId}`)
    await expect(studentPage.locator('.workspace-heading')).toContainText(name)
    await expect(studentPage.getByRole('button',{name:'上传资料',exact:true})).toHaveCount(0)
    await studentPage.getByRole('tab',{name:'检索与验证'}).click()
    await studentPage.getByPlaceholder('输入关键词或问题，查看实际命中的原文').fill('二分查找')
    await studentPage.getByRole('button',{name:'检索',exact:true}).click()
    await expect(studentPage.locator('.search-hit').first()).toContainText('搜索区间缩小一半')
    const sharedRead=await jsonCall(request,student,`/api/rag/documents/${docId}/chunks`)
    expect(String(sharedRead.body.code)).toBe('200')
    const deniedWrite=await jsonCall(request,student,`/api/rag/knowledge-bases/${kbId}`,'put',{name:'不可越权修改',shared:false})
    expect(String(deniedWrite.body.code)).toBe('403')
    await studentPage.screenshot({path:testInfo.outputPath('student-real-shared-search.png'),fullPage:true})
    evidence.checks.sharedRead=true; evidence.checks.sharedWriteDenied=true

    await page.getByRole('button',{name:'编辑与共享',exact:true}).click()
    await editDialog.locator('.el-switch').click()
    await editDialog.getByRole('button',{name:'保存',exact:true}).click()
    await expect(page.getByText('此知识库为私有：只有你可以访问。共享前请检查资料中的个人信息。')).toBeVisible()
    const revoked=await jsonCall(request,student,`/api/rag/documents/${docId}/chunks`)
    expect(String(revoked.body.code)).toBe('403')
    const revokedDownload=await request.get(`${api}/api/rag/documents/${docId}/download`,{headers:authorized(student)})
    expect(revokedDownload.status()).toBe(403)
    await studentPage.getByRole('button',{name:'检索',exact:true}).click()
    await expect(studentPage.getByText(/未向你共享|无权访问|没有权限|无权限|不可访问/).first()).toBeVisible()
    evidence.checks.revocationDeniesChunksAndDownload=true

    await page.getByPlaceholder('例如：这节课的核心概念是什么？').fill('详细解释资料中的二分查找，每步给出例子并引用原文。')
    await page.getByRole('button',{name:'发送',exact:true}).click()
    await page.getByRole('button',{name:'停止生成',exact:true}).click()
    await expect(page.getByText('已停止生成，此回答未完成。')).toBeVisible()
    evidence.checks.clientCancellation=true
    expect(errors).toEqual([])
    expect(externalRequests).toEqual([])
    evidence.checks.noLegacyServiceRequests=true
    evidence.success=true
  } finally {
    await studentContext?.close()
    if (sessionId) await jsonCall(request,teacher,`/api/rag/sessions/${sessionId}`,'delete').catch(()=>{})
    if (kbId) await jsonCall(request,teacher,`/api/rag/knowledge-bases/${kbId}`,'delete').catch(()=>{})
    await writeFile(testInfo.outputPath('sanitized-real-summary.json'),JSON.stringify(evidence,null,2),'utf8')
  }
})
