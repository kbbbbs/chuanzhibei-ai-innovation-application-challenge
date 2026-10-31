#!/usr/bin/env node

const DEFAULT_BASE_URL = 'http://localhost:8080'
const TEST_AGENT_CODE = 'agent_natural_language_samples'

const WRITE_PLAN_TOOLS = [
  'course_create_plan',
  'course_update_plan',
  'course_status_plan',
  'chapter_create_plan',
  'chapter_update_plan',
  'chapter_delete_plan',
  'chapter_sort_plan',
  'lesson_create_plan',
  'lesson_update_plan',
  'lesson_sort_plan',
  'lesson_status_plan',
  'course_resource_attach_plan',
  'course_resource_remove_plan',
  'teaching_class_create_plan',
  'teaching_class_update_plan',
  'teaching_class_status_plan',
  'class_student_add_plan',
  'class_student_remove_plan',
  'class_student_status_plan',
  'homework_create_plan',
  'homework_update_plan',
  'homework_delete_plan',
  'homework_submission_grade_plan',
  'notice_create_plan',
  'notice_update_plan',
  'notice_status_plan',
  'exam_session_create_plan',
  'exam_session_close_plan',
  'exam_answer_grade_plan',
  'exam_paper_create_plan',
  'exam_paper_update_plan',
  'exam_paper_delete_plan',
  'exam_paper_question_add_plan',
  'exam_paper_question_remove_plan',
  'exam_paper_question_update_plan',
  'question_create_plan',
  'question_update_plan',
  'question_delete_plan',
  'grade_record_create_plan',
  'grade_record_update_plan',
  'grade_record_delete_plan'
]

const SAMPLE_CASES = [
  {
    id: 'course-create',
    group: 'course',
    actionCode: 'course.create',
    prompt: '新增一门课，叫 Java 入门，放到计算机分类，简介写适合零基础学习。',
    expectNoMissing: true,
    expectParams: { title: 'Java 入门', categoryName: '计算机' }
  },
  {
    id: 'course-update',
    group: 'course',
    actionCode: 'course.update',
    prompt: '把 Java 入门 这门课改名成 Java 零基础入门。',
    expectNoMissing: true,
    expectParams: { originalTitle: 'Java 入门', title: 'Java 零基础入门' }
  },
  {
    id: 'course-status-down',
    group: 'course',
    actionCode: 'course.status',
    prompt: '把 Java 入门 下架一下，原因是内容要重新整理。',
    expectNoMissing: true,
    expectParams: { title: 'Java 入门', status: 0 }
  },
  {
    id: 'chapter-create',
    group: 'chapter',
    actionCode: 'chapter.create',
    prompt: '给 Java 入门 创建一个章节，标题叫人工输入测试章节，排在第一。',
    expectNoMissing: true,
    expectParams: { courseTitle: 'Java 入门', title: '人工输入测试章节' }
  },
  {
    id: 'chapter-update',
    group: 'chapter',
    actionCode: 'chapter.update',
    prompt: '把 Java 入门 里的人工输入测试章节改名成人工输入验收章节。',
    expectNoMissing: true,
    expectParams: { courseTitle: 'Java 入门', chapterTitle: '人工输入测试章节', title: '人工输入验收章节' }
  },
  {
    id: 'chapter-delete',
    group: 'chapter',
    actionCode: 'chapter.delete',
    prompt: '删除 Java 入门 里的人工输入测试章节，原因是测试完成。',
    expectNoMissing: true,
    expectParams: { courseTitle: 'Java 入门', chapterTitle: '人工输入测试章节' }
  },
  {
    id: 'lesson-create',
    group: 'lesson',
    actionCode: 'lesson.create',
    prompt: '在 Java 入门 的第一章下面加一个课时，叫环境搭建。',
    expectNoMissing: true,
    expectParams: { courseTitle: 'Java 入门', chapterTitle: '第一章', title: '环境搭建' }
  },
  {
    id: 'lesson-resource-attach',
    group: 'lesson',
    actionCode: 'course.resource.attach',
    prompt: '把环境搭建这个课时挂上视频链接 https://example.com/java-start.mp4。',
    expectNoMissing: true,
    expectParams: { lessonTitle: '环境搭建', videoUrl: 'https://example.com/java-start.mp4' }
  },
  {
    id: 'lesson-status-down',
    group: 'lesson',
    actionCode: 'lesson.status',
    prompt: '先把环境搭建这个课时下线，原因是视频还没审完。',
    expectNoMissing: true,
    expectParams: { lessonTitle: '环境搭建', status: 0 }
  },
  {
    id: 'class-create',
    group: 'class',
    actionCode: 'teaching.class.create',
    prompt: '给 Java 入门 开一个教学班，叫 Java 入门 2026 春季班，老师用张老师。',
    expectNoMissing: true,
    expectParams: { courseTitle: 'Java 入门', className: 'Java 入门 2026 春季班', teacherName: '张老师' }
  },
  {
    id: 'class-update',
    group: 'class',
    actionCode: 'teaching.class.update',
    prompt: '把 Java 入门 2026 春季班 的老师换成李老师。',
    expectNoMissing: true,
    expectParams: { originalClassName: 'Java 入门 2026 春季班', teacherName: '李老师' }
  },
  {
    id: 'class-status-down',
    group: 'class',
    actionCode: 'teaching.class.status',
    prompt: '停用 Java 入门 2026 春季班，原因是人数不足。',
    expectNoMissing: true,
    expectParams: { className: 'Java 入门 2026 春季班', status: 0 }
  },
  {
    id: 'class-student-add',
    group: 'class-student',
    actionCode: 'class.student.add',
    prompt: '把王小明加入 Java 入门 2026 春季班。',
    expectNoMissing: true,
    expectParams: { studentName: '王小明', className: 'Java 入门 2026 春季班' }
  },
  {
    id: 'class-student-remove',
    group: 'class-student',
    actionCode: 'class.student.remove',
    prompt: '把王小明从 Java 入门 2026 春季班移出去，原因是学生退课。',
    expectNoMissing: true,
    expectParams: { studentName: '王小明', className: 'Java 入门 2026 春季班' }
  },
  {
    id: 'homework-create',
    group: 'homework',
    actionCode: 'homework.create',
    prompt: '给 Java 入门 2026 春季班 发个作业，标题是第一次练习，内容是完成 HelloWorld，满分 100，截止到 2026-07-20 晚上 11 点。',
    expectNoMissing: true,
    expectParams: { className: 'Java 入门 2026 春季班', title: '第一次练习', fullScore: 100 }
  },
  {
    id: 'homework-update',
    group: 'homework',
    actionCode: 'homework.update',
    prompt: '把第一次练习的截止时间改到 2026-07-22 晚上 11 点。',
    expectNoMissing: true,
    expectParams: { homeworkTitle: '第一次练习' }
  },
  {
    id: 'homework-delete',
    group: 'homework',
    actionCode: 'homework.delete',
    prompt: '删除第一次练习，原因是作业内容发错了。',
    expectNoMissing: true,
    expectParams: { homeworkTitle: '第一次练习' }
  },
  {
    id: 'homework-grade',
    group: 'homework',
    actionCode: 'homework.submission.grade',
    prompt: '给作业提交 12 打 88 分，评语写完成度不错，但命名还要规范。',
    expectNoMissing: true,
    expectParams: { homeworkSubmitId: 12, score: 88 }
  },
  {
    id: 'notice-create',
    group: 'notice',
    actionCode: 'notice.create',
    prompt: '发个通知，标题叫上课提醒，内容是明天上午九点准时上课，发给 Java 入门 2026 春季班。',
    expectNoMissing: true,
    expectParams: { title: '上课提醒', content: '明天上午九点准时上课', className: 'Java 入门 2026 春季班' }
  },
  {
    id: 'notice-update',
    group: 'notice',
    actionCode: 'notice.update',
    prompt: '把上课提醒这个通知的内容改成明天上午十点准时上课。',
    expectNoMissing: true,
    expectParams: { originalTitle: '上课提醒', content: '明天上午十点准时上课' }
  },
  {
    id: 'notice-withdraw',
    group: 'notice',
    actionCode: 'notice.status',
    prompt: '撤回上课提醒这个通知，原因是时间还没确定。',
    expectNoMissing: true,
    expectParams: { title: '上课提醒', status: 0 }
  },
  {
    id: 'notice-restore',
    group: 'notice',
    actionCode: 'notice.status',
    prompt: '恢复上课提醒这个通知。',
    expectNoMissing: true,
    expectParams: { title: '上课提醒', status: 1 }
  },
  {
    id: 'paper-create',
    group: 'exam',
    actionCode: 'exam.paper.create',
    prompt: '给 Java 入门 建一张试卷，叫 Java 入门期中卷，满分 100，考试时长 90 分钟。',
    expectNoMissing: true,
    expectParams: { courseTitle: 'Java 入门', title: 'Java 入门期中卷', totalScore: 100, durationMinutes: 90 }
  },
  {
    id: 'question-create',
    group: 'exam',
    actionCode: 'question.create',
    prompt: '在 Java 入门 课程下加一道单选题，题干是 Java 程序的入口方法是什么，选项是 A main、B start、C run、D init，答案是 A，分值 5 分。',
    expectNoMissing: true,
    expectParams: { courseTitle: 'Java 入门', type: 'single', content: 'Java 程序的入口方法是什么', score: 5 }
  },
  {
    id: 'paper-question-add',
    group: 'exam',
    actionCode: 'exam.paper.question.add',
    prompt: '把 Java 程序的入口方法是什么 这道题加入 Java 入门期中卷，分值 5 分，排第一题。',
    expectNoMissing: true,
    expectParams: { paperTitle: 'Java 入门期中卷', questionTitle: 'Java 程序的入口方法是什么', score: 5 }
  },
  {
    id: 'paper-question-update',
    group: 'exam',
    actionCode: 'exam.paper.question.update',
    prompt: '把 Java 入门期中卷 里 Java 程序的入口方法是什么 这道题分值改成 10 分。',
    expectNoMissing: true,
    expectParams: { paperTitle: 'Java 入门期中卷', questionTitle: 'Java 程序的入口方法是什么', score: 10 }
  },
  {
    id: 'exam-session-create',
    group: 'exam',
    actionCode: 'exam.session.create',
    prompt: '给 Java 入门 2026 春季班 安排一场考试，叫第一次阶段测试，用 Java 入门期中卷，2026-07-10 上午 9 点开始，10 点半结束，时长 90 分钟。',
    expectNoMissing: true,
    expectParams: { className: 'Java 入门 2026 春季班', paperTitle: 'Java 入门期中卷', name: '第一次阶段测试', durationMinutes: 90 }
  },
  {
    id: 'exam-session-close',
    group: 'exam',
    actionCode: 'exam.session.close',
    prompt: '关闭第一次阶段测试，原因是考试已经结束。',
    expectNoMissing: true,
    expectParams: { examSessionTitle: '第一次阶段测试' }
  },
  {
    id: 'exam-answer-grade',
    group: 'exam',
    actionCode: 'exam.answer.grade',
    prompt: '给考试提交 8 里的答题记录 21 打 6 分，评语写思路基本正确。',
    expectNoMissing: true,
    expectParams: { submissionId: 8, submissionAnswerId: 21, score: 6 }
  },
  {
    id: 'grade-create',
    group: 'grade',
    actionCode: 'grade.record.create',
    prompt: '给王小明在 Java 入门 2026 春季班的 Java 入门课程录入一次成绩，名称叫第一次测验，88 分，备注写表现稳定。',
    expectNoMissing: true,
    expectParams: { studentName: '王小明', className: 'Java 入门 2026 春季班', courseTitle: 'Java 入门', gradeName: '第一次测验', score: 88 }
  },
  {
    id: 'grade-update',
    group: 'grade',
    actionCode: 'grade.record.update',
    prompt: '把成绩记录 15 改成 92 分，备注写复核后加分。',
    expectNoMissing: true,
    expectParams: { gradeRecordId: 15, score: 92 }
  },
  {
    id: 'grade-delete',
    group: 'grade',
    actionCode: 'grade.record.delete',
    prompt: '删除成绩记录 15，原因是录错学生了。',
    expectNoMissing: true,
    expectParams: { gradeRecordId: 15 }
  },
  {
    id: 'missing-course-category',
    group: 'negative',
    actionCode: 'course.create',
    prompt: '新增一门课，名字叫 Java 入门。',
    expectMissingIncludes: ['categoryId 或 categoryName']
  },
  {
    id: 'missing-chapter-title',
    group: 'negative',
    actionCode: 'chapter.create',
    prompt: '给 Java 入门 创建一个章节。',
    expectMissingIncludes: ['title']
  },
  {
    id: 'course-status-unspecified-course',
    group: 'negative',
    actionCode: 'course.status',
    prompt: '把某课程下架一下。',
    note: '占位词“某课程”可能被模型当成课程名；真正消歧应在确认执行前测。'
  },
  {
    id: 'course-status-ambiguous-name',
    group: 'negative',
    actionCode: 'course.status',
    prompt: '把 Java 下架一下。',
    note: '名称多匹配需要依赖测试库数据，确认执行前实体解析会提示补充 ID。'
  },
  {
    id: 'missing-notice-content',
    group: 'negative',
    actionCode: 'notice.create',
    prompt: '发个通知，标题叫停课通知。',
    expectMissingIncludes: ['content']
  },
  {
    id: 'missing-homework-title',
    group: 'negative',
    actionCode: 'homework.create',
    prompt: '给 Java 入门 2026 春季班 发个作业。',
    expectMissingIncludes: ['title']
  },
  {
    id: 'chapter-nonexistent-course',
    group: 'negative',
    actionCode: 'chapter.create',
    prompt: '给不存在的课程创建一个章节，标题叫测试章节。',
    note: '课程不存在需要在确认执行前实体解析阶段验证。'
  },
  {
    id: 'notice-status-ambiguous-title',
    group: 'negative',
    actionCode: 'notice.status',
    prompt: '把上课提醒撤回。',
    note: '同名通知消歧需要依赖测试库数据，确认执行前实体解析会提示补充通知 ID 或班级。'
  }
]

const args = parseArgs(process.argv.slice(2))

const config = {
  baseUrl: valueOf('base-url', 'AGENT_BASE_URL', DEFAULT_BASE_URL).replace(/\/$/, ''),
  username: valueOf('username', 'AGENT_TEST_USERNAME', 'admin'),
  password: valueOf('password', 'AGENT_TEST_PASSWORD', '123456'),
  agentId: numberValue('agent-id', 'AGENT_TEST_AGENT_ID'),
  skipHealth: boolValue('skip-health', 'AGENT_TEST_SKIP_HEALTH'),
  requireModel: boolValue('require-model', 'AGENT_TEST_REQUIRE_MODEL'),
  strictParams: boolValue('strict-params', 'AGENT_NL_TEST_STRICT_PARAMS'),
  failFast: boolValue('fail-fast', 'AGENT_NL_TEST_FAIL_FAST'),
  filter: valueOf('case', 'AGENT_NL_TEST_CASE', ''),
  timeoutMs: numberValue('timeout-ms', 'AGENT_NL_TEST_TIMEOUT_MS') || 120000
}

if (args.help || args.h) {
  printHelp()
  process.exit(0)
}

if (args.list) {
  for (const sample of SAMPLE_CASES) {
    console.log(`${sample.id}\t${sample.group}\t${sample.actionCode}\t${sample.prompt}`)
  }
  process.exit(0)
}

main().catch((error) => {
  console.error(`\n[FAIL] ${error.message}`)
  if (error.details) {
    console.error(format(error.details))
  }
  process.exit(1)
})

async function main() {
  assertFetch()
  const selectedCases = selectCases(config.filter)
  if (!selectedCases.length) {
    throw new Error(`没有匹配的样例：${config.filter}`)
  }

  console.log(`[1/5] 登录 ${config.baseUrl}，账号：${config.username}`)
  const login = await postJson('/auth/login', {
    username: config.username,
    password: config.password
  })
  const loginData = unwrapData(login)
  const token = loginData?.token
  if (!token) {
    throw new Error('登录成功响应中没有 data.token')
  }
  console.log(`[OK] 登录成功：userId=${loginData.userId}, role=${loginData.roleCode}`)

  if (!config.skipHealth) {
    console.log('\n[2/5] 检查模型健康状态')
    const health = unwrapData(await getJson('/api/agent/model/health', token))
    console.log(format(health))
    if (!health?.healthy && config.requireModel) {
      throw new Error(`模型健康检查失败：${health?.message || 'unknown'}`)
    }
  } else {
    console.log('\n[2/5] 已跳过模型健康检查')
  }

  let agentId = config.agentId
  if (!agentId) {
    console.log(`\n[3/5] 确保自然语言样例测试 Agent 存在：${TEST_AGENT_CODE}`)
    const agent = await ensureSampleAgent(token)
    agentId = agent.id
    console.log(`[OK] 使用测试 Agent：id=${agentId}, code=${agent.code}`)
  } else {
    console.log(`\n[3/5] 使用指定 Agent：id=${agentId}`)
  }

  console.log('\n[4/5] 创建 Agent 会话')
  const session = unwrapData(await postJson('/api/agent/sessions', {
    agentId,
    title: 'Agent natural language samples'
  }, token))
  const sessionId = session?.id
  if (!sessionId) {
    throw new Error('创建会话响应中没有 data.id')
  }
  console.log(`[OK] 会话已创建：sessionId=${sessionId}`)

  console.log(`\n[5/5] 开始执行自然语言样例：${selectedCases.length}/${SAMPLE_CASES.length}`)
  const results = []
  for (let index = 0; index < selectedCases.length; index += 1) {
    const sample = selectedCases[index]
    process.stdout.write(`\n[${index + 1}/${selectedCases.length}] ${sample.id} -> ${sample.actionCode}\n`)
    try {
      const result = await assertSample(sessionId, token, sample)
      results.push(result)
      console.log(`[OK] ${sample.id} actionId=${result.actionId}`)
      if (result.warnings.length) {
        for (const warning of result.warnings) {
          console.log(`[WARN] ${warning}`)
        }
      }
      if (sample.note) {
        console.log(`[NOTE] ${sample.note}`)
      }
    } catch (error) {
      results.push({ id: sample.id, ok: false, error: error.message })
      console.error(`[FAIL] ${sample.id}: ${error.message}`)
      if (error.details) {
        console.error(format(error.details))
      }
      if (config.failFast) {
        throw error
      }
    }
  }

  const passed = results.filter((item) => item.ok).length
  const failed = results.length - passed
  const warnings = results.reduce((sum, item) => sum + (item.warnings?.length || 0), 0)

  console.log(`\n[SUMMARY] passed=${passed}, failed=${failed}, warnings=${warnings}`)
  if (failed > 0) {
    throw withDetails(new Error('自然语言样例自动测试未全部通过'), results.filter((item) => !item.ok))
  }
  console.log('[PASS] 自然语言样例自动测试完成；默认只生成待确认计划，没有确认执行写操作。')
}

async function assertSample(sessionId, token, sample) {
  const reply = await chatAgent(sessionId, sample.prompt, token)
  if (reply.error) {
    throw withDetails(new Error('Agent 非流式接口返回错误'), reply.error)
  }
  const pendingAction = reply.complete?.pendingAction || null
  const actionId = pendingAction?.actionId || extractActionId(reply.content)
  if (!actionId) {
    throw withDetails(new Error('没有生成 pendingAction'), {
      preview: preview(reply.content),
      complete: reply.complete
    })
  }

  const detail = unwrapData(await getJson(`/api/agent/actions/pending/${encodeURIComponent(actionId)}`, token))
  const actionCode = detail?.actionCode || pendingAction?.actionCode || ''
  if (actionCode !== sample.actionCode) {
    throw withDetails(new Error(`actionCode 不匹配：期望 ${sample.actionCode}，实际 ${actionCode || '空'}`), {
      preview: preview(reply.content),
      pendingAction,
      detail
    })
  }

  const parameters = detail?.parameters || pendingAction?.parameters || {}
  const missing = listValue(parameters.missingRequiredFields)
  if (sample.expectNoMissing && missing.length > 0) {
    throw withDetails(new Error(`不应缺参，但实际缺失：${missing.join(', ')}`), {
      parameters,
      preview: preview(reply.content)
    })
  }
  if (sample.expectMissingIncludes) {
    for (const expected of sample.expectMissingIncludes) {
      if (!includesText(missing, expected)) {
        throw withDetails(new Error(`缺失字段不匹配：应包含 ${expected}，实际 ${JSON.stringify(missing)}`), {
          parameters,
          preview: preview(reply.content)
        })
      }
    }
  }

  const warnings = []
  if (sample.expectParams) {
    for (const [key, expectedValue] of Object.entries(sample.expectParams)) {
      const actualValue = parameters[key]
      if (!looselyEquals(actualValue, expectedValue)) {
        const message = `参数 ${key} 期望 ${expectedValue}，实际 ${actualValue ?? '空'}`
        if (config.strictParams) {
          throw withDetails(new Error(message), {
            parameters,
            preview: preview(reply.content)
          })
        }
        warnings.push(message)
      }
    }
  }

  return {
    id: sample.id,
    ok: true,
    actionId,
    actionCode,
    missing,
    warnings
  }
}

async function ensureSampleAgent(token) {
  const list = await getJson(`/api/agent/definitions?keyword=${encodeURIComponent(TEST_AGENT_CODE)}`, token)
  const agents = Array.isArray(unwrapData(list)) ? unwrapData(list) : []
  const existing = agents.find((agent) => agent.code === TEST_AGENT_CODE)
  if (existing) {
    if (existing.status === 1 && includesAllTools(existing.enabledTools, WRITE_PLAN_TOOLS)) {
      return existing
    }
    const updated = await putJson(`/api/agent/definitions/${existing.id}`, sampleAgentPayload(), token)
    return unwrapData(updated)
  }

  const created = await postJson('/api/agent/definitions', sampleAgentPayload(), token)
  return unwrapData(created)
}

function sampleAgentPayload() {
  return {
    code: TEST_AGENT_CODE,
    name: 'Agent 自然语言样例测试助手',
    description: '用于本地验证自然语言写操作能生成 schema-first 待确认计划。',
    systemPrompt: '你是 AI 智学平台的自然语言样例测试助手。所有创建、修改、删除、状态变更、批改、录入等写操作必须先生成待确认计划，不得直接执行。',
    temperature: 0.1,
    knowledgeBaseIds: '[]',
    enabledTools: JSON.stringify(WRITE_PLAN_TOOLS),
    status: 1
  }
}

function selectCases(filterText) {
  if (!filterText) {
    return SAMPLE_CASES
  }
  const filters = String(filterText)
    .split(',')
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean)
  if (!filters.length) {
    return SAMPLE_CASES
  }
  return SAMPLE_CASES.filter((sample) => filters.some((filter) =>
    sample.id.toLowerCase().includes(filter)
    || sample.group.toLowerCase().includes(filter)
    || sample.actionCode.toLowerCase().includes(filter)
  ))
}

function includesAllTools(value, expectedTools) {
  try {
    const tools = typeof value === 'string' ? JSON.parse(value) : value
    return Array.isArray(tools) && expectedTools.every((tool) => tools.includes(tool))
  } catch {
    return false
  }
}

async function chatAgent(sessionId, content, token) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), config.timeoutMs)
  try {
    const response = await fetch(`${config.baseUrl}/api/agent/sessions/${sessionId}/chat`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json;charset=utf-8'
      },
      body: JSON.stringify({ content }),
      signal: controller.signal
    })

    const text = await response.text()
    const payload = parseMaybeJson(text)
    if (!response.ok || (payload?.code && String(payload.code) !== '200')) {
      return { content: '', complete: null, error: payload || `HTTP ${response.status} ${text}` }
    }

    const complete = unwrapData(payload)
    return { content: complete?.content || '', complete, error: null }
  } finally {
    clearTimeout(timer)
  }
}

async function getJson(path, token) {
  return requestJson('GET', path, undefined, token)
}

async function postJson(path, body, token) {
  return requestJson('POST', path, body, token)
}

async function putJson(path, body, token) {
  return requestJson('PUT', path, body, token)
}

async function requestJson(method, path, body, token) {
  const response = await fetch(`${config.baseUrl}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      'Content-Type': 'application/json;charset=utf-8'
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await response.text()
  const payload = parseMaybeJson(text)
  if (!response.ok) {
    throw withDetails(new Error(`${method} ${path} 失败：HTTP ${response.status}`), payload)
  }
  if (payload?.code && String(payload.code) !== '200') {
    throw withDetails(new Error(`${method} ${path} 失败：${payload.msg || payload.message || payload.code}`), payload)
  }
  return payload
}

function extractActionId(text) {
  return (text || '').match(/actionId\s*[=：:]\s*([A-Za-z0-9_-]+)/i)?.[1]
    || (text || '').match(/确认ID\s*[=：:]\s*([A-Za-z0-9_-]+)/i)?.[1]
    || ''
}

function unwrapData(payload) {
  let data = payload
  while (data && typeof data === 'object' && !Array.isArray(data) && data.data !== undefined) {
    data = data.data
  }
  return data
}

function parseMaybeJson(value) {
  if (!value || typeof value !== 'string') return value
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

function listValue(value) {
  if (Array.isArray(value)) {
    return value.map((item) => String(item))
  }
  return []
}

function includesText(values, expected) {
  const normalizedExpected = normalizeText(expected)
  return values.some((value) => normalizeText(value) === normalizedExpected)
}

function looselyEquals(actual, expected) {
  if (actual === expected) return true
  if (actual === undefined || actual === null) return false
  return normalizeText(actual) === normalizeText(expected)
}

function normalizeText(value) {
  return String(value ?? '')
    .replace(/\s+/g, '')
    .replace(/[，,。；;：:]/g, '')
    .trim()
}

function preview(value) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  return text.length > 500 ? `${text.slice(0, 500)}...` : text
}

function format(value) {
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2)
}

function withDetails(error, details) {
  error.details = details
  return error
}

function parseArgs(argv) {
  const result = {}
  for (let index = 0; index < argv.length; index += 1) {
    const item = argv[index]
    if (!item.startsWith('--')) continue
    const raw = item.slice(2)
    const equalIndex = raw.indexOf('=')
    if (equalIndex !== -1) {
      result[raw.slice(0, equalIndex)] = raw.slice(equalIndex + 1)
      continue
    }
    const next = argv[index + 1]
    if (!next || next.startsWith('--')) {
      result[raw] = true
    } else {
      result[raw] = next
      index += 1
    }
  }
  return result
}

function valueOf(name, envName, fallback) {
  return args[name] ?? process.env[envName] ?? fallback
}

function numberValue(name, envName) {
  const value = valueOf(name, envName, '')
  if (value === '' || value === undefined || value === null) return null
  const number = Number(value)
  if (!Number.isFinite(number) || number <= 0) {
    throw new Error(`${name} 必须是正整数`)
  }
  return Math.trunc(number)
}

function boolValue(name, envName) {
  const value = valueOf(name, envName, false)
  return value === true || value === 'true' || value === '1' || value === 'yes'
}

function assertFetch() {
  if (typeof fetch !== 'function') {
    throw new Error('当前 Node 版本不支持 fetch，请使用 Node.js 18 或更高版本')
  }
}

function printHelp() {
  console.log(`Agent natural language sample test

Usage:
  node scripts/agent-natural-language-samples.mjs [options]

Options:
  --base-url <url>        Backend base URL. Default: ${DEFAULT_BASE_URL}
  --username <name>       Login username. Default: admin
  --password <password>   Login password. Default: 123456
  --agent-id <id>         Use an existing agent instead of creating ${TEST_AGENT_CODE}
  --skip-health          Skip GET /api/agent/model/health
  --require-model        Fail when model health fails
  --case <filter>        Run matching case id/group/actionCode. Comma separated.
  --list                 Print all sample cases and exit
  --strict-params        Fail when extracted parameter values differ from expected values
  --fail-fast            Stop on first failed sample
  --timeout-ms <number>  Timeout per stream request. Default: 120000

Examples:
  node scripts/agent-natural-language-samples.mjs
  node scripts/agent-natural-language-samples.mjs --case course
  node scripts/agent-natural-language-samples.mjs --case chapter-create,notice-create --strict-params
`)
}
