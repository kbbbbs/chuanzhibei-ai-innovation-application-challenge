import { test, expect } from '@playwright/test'

async function mountWorkspace(page, tab = 'documents', { rejectFirstAccessUpdate = false } = {}) {
  const source = await (await page.request.get('/src/components/knowledge/NativeKnowledgeWorkspace.vue')).text()
  const store = await (await page.request.get('/src/store/user.js')).text()
  const dep = name => (source + store).match(new RegExp('/node_modules/[^\"\\s]*' + name + '\\.js\\?v=[a-z0-9]+'))?.[0]
  await page.route('**/__course-component', route => route.fulfill({contentType:'text/html',body:`<html><head><link rel="stylesheet" href="/node_modules/element-plus/dist/index.css"></head><body><div id="test-root"></div><script type="module">
import { createApp } from '${dep('vue')}'
import { createPinia } from '${dep('pinia')}'
import ElementPlus from '${dep('element-plus')}'
import {createRouter,createMemoryHistory} from '${dep('vue-router')}'
import Component from '/src/components/knowledge/NativeKnowledgeWorkspace.vue'
const app=createApp(Component,{manage:true,courseId:12,workspaceTab:'${tab}',embedded:true})
app.use(createPinia());app.use(ElementPlus);app.use(createRouter({history:createMemoryHistory(),routes:[]}));app.mount('#test-root')
</script></body></html>`}))
  await page.addInitScript(()=>{localStorage.setItem('userId','7');localStorage.setItem('roleCode','teacher');localStorage.setItem('token','fixture')})
  const calls=[]
  const access = [
    { document_id: 'doc', version: 0, read_excerpt: true, download_allowed: true },
    { document_id: 'slides', version: 1, read_excerpt: true, download_allowed: true },
  ]
  await page.route('**/backend/**', async route=>{
    const path=new URL(route.request().url()).pathname.replace('/backend','')
    calls.push({path,body:route.request().postData()})
    const ok=data=>route.fulfill({json:{code:'200',data}})
    if(path==='/api/rag/courses/12/knowledge-base')return ok({id:'course-kb',name:'课程专属库',owner_id:7})
    if(path==='/api/rag/knowledge-bases')return ok([{id:'legacy',name:'不应使用的旧库',owner_id:7}])
    if(path.endsWith('/documents'))return ok([
      {id:'doc',title:'教材',document_version:1,parse_status:'success'},
      {id:'slides',title:'课件',document_version:1,parse_status:'success'},
    ])
    if (path.endsWith('/document-access')) {
      if (route.request().method() === 'PUT') {
        if (rejectFirstAccessUpdate) {
          rejectFirstAccessUpdate = false
          return route.fulfill({ json: { code: '500', msg: '权限保存暂不可用' } })
        }
        const { documentIds, readExcerpt, downloadAllowed } = route.request().postDataJSON()
        for (const documentId of documentIds) {
          const previous = access.findIndex(row => row.document_id === documentId && row.version === 1)
          if (previous !== -1) access.splice(previous, 1)
          access.push({ document_id: documentId, version: 1, read_excerpt: readExcerpt, download_allowed: downloadAllowed })
        }
      }
      return ok(access)
    }
    if(path==='/api/rag/search')return ok({hits:[],actualMode:'NONE'})
    return ok([])
  })
  page.on('pageerror', error=>console.log('PAGEERROR',error.message))
  await page.goto('/__course-component')
  return calls
}

test('course workspace resolves its own library and hides global library selection',async({page})=>{
  const calls=await mountWorkspace(page)
  await expect(page.locator('button').filter({hasText:'上传资料'})).toBeVisible()
  await expect(page.locator('.library-panel')).toHaveCount(0)
  await expect.poll(()=>calls.some(c=>c.path==='/api/rag/courses/12/knowledge-base')).toBe(true)
  expect(calls.some(c=>c.path==='/api/rag/knowledge-bases')).toBe(false)
  await expect(page.getByRole('row', { name: /教材/ })).toContainText('仅教师使用')
})

test('course search carries course scope',async({page})=>{
  const calls=await mountWorkspace(page,'search')
  await page.getByPlaceholder('输入关键词或问题，查看实际命中的原文').fill('树')
  await page.getByRole('button',{name:'检索',exact:true}).click()
  await expect.poll(()=>calls.some(c=>c.path==='/api/rag/search' && JSON.parse(c.body).courseId===12)).toBe(true)
})

test('course materials groups primary buttons and aligns document operations', async ({ page }) => {
  await mountWorkspace(page)
  const toolbar = page.getByTestId('document-toolbar')
  const upload = toolbar.locator('button').filter({ hasText: '上传资料' })
  const refresh = toolbar.getByRole('button', { name: '刷新', exact: true })
  await expect(upload).toBeVisible()
  expect((await refresh.boundingBox()).x).toBeGreaterThan((await upload.boundingBox()).x)
  const actions = page.locator('.document-actions').first()
  await expect(actions).toHaveCSS('display', 'grid')
  await expect(actions.getByRole('button', { name: '查看与修改' })).toBeVisible()
  const headers = await page.getByRole('columnheader').allTextContents()
  expect(headers.slice(0, 3)).toEqual(['资料', '处理状态', '学生访问'])
})


async function mountPreparation(page, { ready = false } = {}) {
  const source = await (await page.request.get('/src/views/teacher/IntelligentResourceGen.vue')).text()
  const store = await (await page.request.get('/src/store/user.js')).text()
  const dep = name => (source + store).match(new RegExp('/node_modules/[^"\\s]*' + name + '\\.js\\?v=[a-z0-9]+'))?.[0]
  await page.route('**/__preparation-component', route => route.fulfill({contentType:'text/html',body:`<html><body><div id="test-root"></div><script type="module">
import {createApp} from '${dep('vue')}'
import {createPinia} from '${dep('pinia')}'
import ElementPlus from '${dep('element-plus')}'
import {createRouter,createMemoryHistory} from '${dep('vue-router')}'
import Component from '/src/views/teacher/IntelligentResourceGen.vue'
const app=createApp(Component,{courseId:12,embedded:true});app.use(createPinia());app.use(ElementPlus);app.use(createRouter({history:createMemoryHistory(),routes:[]}));app.mount('#test-root')
</script></body></html>`}))
  await page.addInitScript(()=>{localStorage.setItem('userId','7');localStorage.setItem('roleCode','teacher');localStorage.setItem('token','fixture')})
  const calls=[]
  let applied = false
  const prepared = () => ({id:21,projectStatus:applied ? 'PUBLISHED' : 'DRAFT', currentStage:applied ? 'PUBLISHED' : 'READY_TO_PUBLISH', generationStatus:'SUCCEEDED',config:{targetCourseId:12,title:'已创建的课程',categoryId:1,audience:'学生',includeQuestions:false},outline:{chapters:[{chapterKey:'c1',title:'新章节',lessons:[{lessonKey:'l1',title:'新课时'}]}]},lessons:[{lessonKey:'l1',chapterKey:'c1',title:'新课时',contentMd:'Generated',status:'GENERATED'}],questions:[]})
  await page.route('**/backend/**',async route=>{
    const path=new URL(route.request().url()).pathname.replace('/backend','')
    const body=route.request().postDataJSON()
    calls.push({path,body})
    const ok=data=>route.fulfill({json:{code:'200',data}})
    if(path==='/courses/12')return ok({id:12,title:'已创建的课程',categoryId:1,description:'课程简介',status:2,chapters:[]})
    if(path==='/categories')return ok([{id:1,name:'计算机'}])
    if(path==='/api/rag/courses/12/knowledge-base')return ok({id:'course-kb',owner_id:7,name:'课程资料'})
    if(path.endsWith('/documents'))return ok([{id:'d1',file_name:'教材.pdf',parse_status:'success'}])
    if(path==='/api/preparations' && ready && route.request().method()==='GET')return ok([prepared()])
    if(path==='/api/preparations/21')return ok(prepared())
    if(path==='/api/preparations/21/apply-preview')return ok({courseId:12,courseVersion:'v1',applicationId:applied ? 21 : null,existingLessons:[{id:9,chapterTitle:'原章节',title:'原课时'}]})
    if(path==='/api/preparations/21/apply'){applied=true;return ok({applicationId:21,courseId:12})}
    if(path==='/api/preparations' && route.request().method()==='POST')return ok({id:21,projectStatus:'DRAFT',currentStage:'CONFIG',config:body})
    if(path==='/api/preparations/21/apply-preview')return ok({courseId:12,courseVersion:'v1',alreadyApplied:applied,applicationId:applied?21:null,existingLessons:[{id:9,title:'原课时',chapterTitle:'原章节'}]})
    if(path==='/api/preparations/21/apply'){applied=true;return ok({applicationId:21,courseId:12})}
    if(path==='/api/preparations/21/config')return ok({id:21,projectStatus:'DRAFT',currentStage:'CONFIG',config:body})
    if(path==='/api/preparations/21/generate-all')return ok({id:21,projectStatus:'DRAFT',currentStage:'WAITING_SOURCES_ALL',generationStatus:'WAITING'})
    return ok([])
  })
  await page.goto('/__preparation-component')
  return calls
}

test('course preparation inherits course information and submits its target course',async({page})=>{
  const calls=await mountPreparation(page)
  await expect(page.getByTestId('course-preparation-sources')).toContainText('教材.pdf')
  await expect(page.getByLabel('课程名称',{exact:true})).toHaveCount(0)
  await expect(page.getByText('选择教材或课件',{exact:true})).toHaveCount(0)
  await page.getByPlaceholder('例如：计算机专业大二本科生').fill('大学生')
  await page.getByRole('button',{name:'保存并生成完整草稿'}).click()
  await expect.poll(()=>calls.some(c=>c.path==='/api/preparations' && c.body?.targetCourseId===12 && c.body?.title==='已创建的课程')).toBe(true)
  await expect(page.getByText('等待课程资料处理，有可用内容后自动生成')).toBeVisible()
})

test('teachers save access per document and the control keeps the last confirmed access after a failure', async ({ page }) => {
  const calls = await mountWorkspace(page, 'documents', { rejectFirstAccessUpdate: true })
  const documentRow = page.getByRole('row', { name: /教材/ })
  const slidesRow = page.getByRole('row', { name: /课件/ })
  const accessSelect = documentRow.getByRole('combobox', { name: '教材的学生访问权限' })
  const changeAccess = async label => {
    await expect(accessSelect).toBeEnabled()
    await documentRow.locator('.el-select').click({ timeout: 5000 })
    await page.getByRole('option', { name: label, exact: true }).click({ timeout: 5000 })
  }

  await expect(page.getByRole('checkbox')).toHaveCount(0)
  await expect(page.getByText(/已选 .* 份资料/)).toHaveCount(0)
  await expect(page.locator('.document-toolbar').getByRole('button', { name: '根据资料出题' })).toBeVisible()
  await expect(documentRow).toContainText('仅教师使用')
  await expect(slidesRow).toContainText('开放并允许下载')

  await changeAccess('开放给学生')
  await expect(page.locator('.el-alert').getByText('权限保存暂不可用')).toBeVisible()
  await expect(documentRow).toContainText('仅教师使用')

  for (const label of ['开放给学生', '开放并允许下载', '仅教师使用']) {
    await changeAccess(label)
    await expect(documentRow).toContainText(label)
    await expect(slidesRow).toContainText('开放并允许下载')
  }
  expect(calls.filter(call => call.path.endsWith('/document-access') && call.body).map(call => JSON.parse(call.body))).toEqual([
    { documentIds: ['doc'], readExcerpt: true, downloadAllowed: false },
    { documentIds: ['doc'], readExcerpt: true, downloadAllowed: false },
    { documentIds: ['doc'], readExcerpt: true, downloadAllowed: true },
    { documentIds: ['doc'], readExcerpt: false, downloadAllowed: false },
  ])
})

test('multiple uploads use the current course library and failed files can retry', async ({ page }) => {
  await mountWorkspace(page)
  const uploaded = []
  let failed = false
  await page.route('**/knowledge-bases/course-kb/documents', async route => {
    if (route.request().method() === 'GET') return route.fulfill({ json: { code: '200', data: [] } })
    const body = route.request().postDataBuffer().toString()
    const filename = body.includes('one.md') ? 'one.md' : 'two.md'
    uploaded.push(filename)
    if (filename === 'one.md' && !failed) {
      failed = true
      return route.fulfill({ json: { code: '500', msg: '上传暂不可用' } })
    }
    return route.fulfill({ json: { code: '200', data: { id: filename } } })
  })
  await page.locator('input[type=file]').setInputFiles([
    { name: 'one.md', mimeType: 'text/markdown', buffer: Buffer.from('First') },
    { name: 'two.md', mimeType: 'text/markdown', buffer: Buffer.from('Second') }
  ])
  await expect.poll(() => uploaded.length).toBe(2)
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect.poll(() => uploaded.length).toBe(3)
  expect(uploaded).toEqual(['one.md', 'two.md', 'one.md'])
})

test('prepared content previews explicit replacements before applying to the same course', async ({ page }) => {
  const calls = await mountPreparation(page, { ready: true })
  await page.getByRole('button', { name: '预览并应用到课程', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '应用生成内容' })
  await dialog.locator('.el-select').click()
  await page.getByRole('option', { name: '替换 原章节 / 原课时', exact: true }).click()
  await dialog.getByRole('button', { name: '确认应用到当前课程', exact: true }).click()
  await expect.poll(() => calls.some(call => call.path.endsWith('/apply') && call.body?.courseVersion === 'v1' && call.body?.replacements?.[0]?.targetLessonId === 9)).toBe(true)
  await expect(page.getByText('生成内容已应用', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '回退本次应用', exact: true })).toBeVisible()
})

test('student source preview uses course-authorized chunk and page endpoints', async ({ page }) => {
  const source = await (await page.request.get('/src/components/knowledge/NativeKnowledgeWorkspace.vue')).text()
  const store = await (await page.request.get('/src/store/user.js')).text()
  const dep = name => (source + store).match(new RegExp('/node_modules/[^"\\s]*' + name + '\\.js\\?v=[a-z0-9]+'))?.[0]
  await page.route('**/__student-source', route => route.fulfill({ contentType: 'text/html', body: `<html><body><div id="test-root"></div><script type="module">
import { createApp } from '${dep('vue')}'
import { createPinia } from '${dep('pinia')}'
import ElementPlus from '${dep('element-plus')}'
import Component from '/src/components/knowledge/RagCitations.vue'
const app = createApp(Component, { courseId: 12, inline: true, citations: [{ chunkId: 'chunk', documentId: 'doc', documentVersion: 1, page: 2 }] })
app.use(createPinia()); app.use(ElementPlus); app.mount('#test-root')
</script></body></html>` }))
  await page.addInitScript(() => { localStorage.setItem('roleCode', 'student'); localStorage.setItem('token', 'fixture') })
  const paths = []
  await page.route('**/backend/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/backend', '')
    paths.push(path)
    if (path.endsWith('/preview')) return route.fulfill({ contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aSucAAAAASUVORK5CYII=', 'base64') })
    return route.fulfill({ json: { code: '200', data: { chunkId: 'chunk', documentId: 'doc', documentVersion: 1, page: 2, content: '授权内容' } } })
  })
  await page.goto('/__student-source')
  await expect(page.getByTestId('source-page-image')).toBeVisible()
  expect(paths).toContain('/api/rag/courses/12/chunks/chunk')
  expect(paths).toContain('/api/rag/courses/12/documents/doc/versions/1/pages/2/preview')
  expect(paths).not.toContain('/api/rag/chunks/chunk')
})
