import { test, expect } from '@playwright/test'

// These are UI contract tests with controlled responses. They do not validate the backend or a model.
const kbId = 'c93a48f0-383b-4db2-a79a-b6277f9e4b41'
const docId = 'ddfcf410-09d1-4877-9537-69e86cf71476'
const chunkId = '91e286ca-bab4-41a6-b34c-55d5ec4d05e6'
const source = { chunkId, documentId:docId, kbId, title:'算法课程资料', content:'二分查找每次将搜索区间缩小一半。', page:3, ordinal:2, score:1.25, citation:'[1]' }

async function fixture(page, { shared=false, readOnly=false, stream='complete', formula=false } = {}) {
  const hit = formula ? {...source, content:String.raw`损失函数 \[L=\frac{1}{n}\sum_i x_i^2\]`} : source
  page.on('pageerror', error => console.error('UI runtime error:', error.message))
  await page.addInitScript(() => {
    localStorage.setItem('token','ui-contract-test-token')
    localStorage.setItem('userId','7')
    localStorage.setItem('roleCode','teacher')
    localStorage.setItem('username','界面测试')
  })
  const calls = []
  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (!path.startsWith('/api/')) return route.continue()
    calls.push({ path, method:request.method(), body:request.postDataJSON?.(), auth:request.headers().authorization })
    const ok = data => route.fulfill({json:{code:'200',data}})
    if (path === '/api/rag/knowledge-bases') return ok([{id:kbId,name:'算法课程资料',description:'课程原文',shared,owner_id:readOnly ? 8 : 7,read_only:readOnly,knowledge_count:1}])
    if (path.endsWith('/documents') && request.method()==='GET') return ok([{id:docId,title:'算法课程资料',file_name:'算法.pdf',parse_status:'success',chunk_count:1,page_count:4}])
    if (path === '/api/rag/search') return ok({hits:[hit],mode:'bm25',warning:''})
    if (path === `/api/rag/chunks/${chunkId}`) return ok(hit)
    if (path === '/api/rag/chat/stream') {
      if (stream==='delayed') await new Promise(resolve=>setTimeout(resolve,1000))
      const result = stream === 'no-evidence'
        ? {answer:'当前资料中未找到能支持回答的内容。',citations:[],mode:'bm25',status:'NO_EVIDENCE',sessionId:'s1'}
        : {answer:formula ? hit.content+'[1]' : `二分查找每次将搜索区间缩小一半。[${stream === 'sparse-citation' ? 3 : 1}]`,citations:[{...hit,citation:stream === 'sparse-citation' ? '[3]' : '[1]'}],mode:'bm25',status:'ANSWERED',sessionId:'s1'}
      const frames = `event: progress\r\ndata: {"stage":"search","message":"正在检索资料"}\r\n\r\nevent: delta\r\ndata: {"content":"二分查找"}\r\n\r\nevent: complete\r\ndata: ${JSON.stringify(result)}\r\n\r\n`
      return route.fulfill({status:200,contentType:'text/event-stream',body:frames}).catch(()=>{})
    }
    if (path.endsWith('/sessions')) return ok([])
    return ok([])
  })
  await page.goto('/teacher-knowledge-base')
  await expect(page.getByRole('tab',{name:'资料与出处'})).toBeVisible()
  return calls
}

test('native retrieval displays actual hit, authenticated source and final stream citations', async ({page}) => {
  const calls = await fixture(page)
  await page.getByRole('tab',{name:'检索与验证'}).click()
  await page.getByPlaceholder('输入关键词或问题，查看实际命中的原文').fill('二分查找')
  await page.getByRole('button',{name:'检索',exact:true}).click()
  await expect(page.getByText('关键词检索 · 分数用于本次结果排序，不能作为事实可信度。')).toBeVisible()
  await expect(page.locator('.search-hit')).toContainText('第 3 页')
  await page.getByRole('button',{name:'[1] 算法课程资料 · 第 3 页',exact:true}).click()
  await expect(page.locator('.source-text')).toHaveText(source.content)
  await page.keyboard.press('Escape')
  await expect(page.locator('.source-text')).not.toBeVisible()
  await page.getByRole('tab',{name:'知识问答'}).click()
  await page.getByPlaceholder('例如：这节课的核心概念是什么？').fill('二分查找是怎么工作的？')
  await page.getByRole('button',{name:'发送',exact:true}).click()
  await expect(page.locator('.chat-message.assistant')).toContainText('二分查找每次将搜索区间缩小一半。[1]')
  await expect(page.locator('.chat-message.assistant').getByRole('button',{name:'[1] 算法课程资料 · 第 3 页',exact:true})).toBeVisible()
  expect(calls.filter(call=>call.path.startsWith('/api/rag/') && call.auth!=='Bearer ui-contract-test-token')).toEqual([])
  expect(calls.some(call=>call.path.startsWith('/api/weknora'))).toBe(false)
  expect(calls.find(call=>call.path==='/api/rag/search').body.knowledgeBaseIds).toEqual([kbId])
})

test('read-only knowledge fixture hides mutations without claiming legacy shared flags grant access', async ({page}) => {
  await fixture(page,{shared:true,readOnly:true})
  await expect(page.getByText('只有资料所有者可以管理。学生学习资料需另行授权给课程。')).toBeVisible()
  await expect(page.getByRole('button',{name:'上传资料',exact:true})).toHaveCount(0)
  await expect(page.getByRole('button',{name:'编辑资料库',exact:true})).toHaveCount(0)
  await expect(page.getByRole('button',{name:'查看出处',exact:true})).toBeVisible()
})

test('stop generation terminates the request and marks incomplete response', async ({page}) => {
  await fixture(page,{stream:'delayed'})
  await page.getByRole('tab',{name:'知识问答'}).click()
  await page.getByPlaceholder('例如：这节课的核心概念是什么？').fill('二分查找')
  await page.getByRole('button',{name:'发送',exact:true}).click()
  await page.getByRole('button',{name:'停止生成',exact:true}).click()
  await expect(page.getByText('已停止生成，此回答未完成。')).toBeVisible()
  await expect(page.locator('.chat-message.assistant .rag-citations')).toHaveCount(0)
  await expect(page.getByRole('button',{name:'停止生成',exact:true})).toHaveCount(0)
})

test('no evidence clearly refuses and does not invent a source', async ({page}) => {
  await fixture(page,{stream:'no-evidence'})
  await page.getByRole('tab',{name:'知识问答'}).click()
  await page.getByPlaceholder('例如：这节课的核心概念是什么？').fill('资料没有的内容')
  await page.getByRole('button',{name:'发送',exact:true}).click()
  await expect(page.locator('.chat-message.assistant')).toContainText('资料中未找到足够依据')
  await expect(page.locator('.chat-message.assistant .rag-citations')).toHaveCount(0)
})

test('filtered evidence retains the original citation number in the answer', async ({page}) => {
  await fixture(page,{stream:'sparse-citation'})
  await page.getByRole('tab',{name:'知识问答'}).click()
  await page.getByPlaceholder('例如：这节课的核心概念是什么？').fill('二分查找')
  await page.getByRole('button',{name:'发送',exact:true}).click()
  await expect(page.locator('.chat-message.assistant').getByRole('button',{name:'[3] 算法课程资料 · 第 3 页',exact:true})).toBeVisible()
  await expect(page.locator('.chat-message.assistant').getByRole('button',{name:'[1] 算法课程资料 · 第 3 页',exact:true})).toHaveCount(0)
})


test('answer and authenticated citation both render the stored formula with a source-code view', async ({page}) => {
  await fixture(page,{formula:true})
  await page.getByRole('tab',{name:'知识问答'}).click()
  await page.getByPlaceholder('例如：这节课的核心概念是什么？').fill('给出损失函数')
  await page.getByRole('button',{name:'发送',exact:true}).click()
  await expect(page.locator('.chat-message.assistant .katex')).toHaveCount(1)
  await page.locator('.chat-message.assistant').getByRole('button',{name:'[1] 算法课程资料 · 第 3 页',exact:true}).click()
  await expect(page.locator('.source-formula .katex')).toHaveCount(1)
  await page.getByText('查看原文源码',{exact:true}).click()
  await expect(page.locator('.source-text')).toContainText('frac')
})
