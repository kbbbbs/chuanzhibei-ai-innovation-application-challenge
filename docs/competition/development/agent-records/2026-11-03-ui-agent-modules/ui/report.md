# 课时响应式布局与作业文案修复

实际开发日期：2026-11-03。本文用户展示时间采用北京时间（Asia/Shanghai，UTC+08:00），原始日志时间以各自时间戳为准。范围：现有 Vue 课时页布局、笔记工具栏、复用考试页面的作业文案；保留现有品牌。

## 故事与验收

- 学生在 375/768/1080/1440 CSS px 下可阅读正文、展开收起章节目录、使用笔记和问答；标题不逐字换行、知识库选择器完整、页面无横向溢出。
- 作业批改、结果、名称和空态使用“作业”，考试保留“考试”；相同提交 ID 切换 assessmentType 查询参数时重新请求对应领域 API。
- 不改写真实已提交作业、考试或成绩。

## 测试与修正

1. `red.log` 初次执行 6 项，5 失败、1 通过。其中 EXAM 失败来自测试登录初始化在 reload 时恢复 teacher，属于测试错误，随后修正；不计为产品缺陷。
2. `red-reviewed.log` 6 项，4 失败、2 通过：375px 笔记宽 85.875px、768px 宽 191.15625px、1080px AI 标题高 105px、作业批改仍显示考试。
3. 为独立捕获结果文案拆开测试，`red-result.log` 2 项，作业结果失败、考试结果通过。
4. 使用局部 CSS grid：1350px 以下工具区位于正文下方，900px 以下单列；列可缩小、工具栏可换行，保留已有目录折叠行为。按 assessmentType 派生文案，修改相关学生列表开始时间提示及答题到时提示。
5. `green.log` 8/8；`green-final.log` 9/9（增加同 ID 查询参数切换 API 断言）。
6. 真实手机截图发现 LessonPageRenderer 的 `.page-shell` 受到全局 min-height 影响。`red-mobile.log` 1 项失败，导航与正文间空白 297.046875px。局部设置 min-height:0、align-content:start，并令内容列可缩小；该组件仅 CourseLearn 使用。
7. `green-complete.log` 最终 10/10。`regression.log` 已有 native-rag-ui 与 k03-retrieval-ui 15/15；`formula.log` Node 公式测试 8/8；`build-final.log` 构建通过。构建有既有 VueUse PURE annotation 和大 chunk 警告。

受控 API Playwright 测试使用合成响应，验证浏览器表现和 API 选择契约；不声明真实后端或模型质量。

## 真实 Chrome 验证（无 mock）

使用已运行的 5173/8080 与 README 演示登录。只读课时/结果/批改页，未保存笔记、完成课时、提交作业、提交考试或批改成绩。AI 组件可能按自身现有初始化逻辑创建空对话；未发送模型消息。

- `live-final-375.png`、`live-final-768.png`、`live-final-1080.png`、`live-final-1440.png`：最终展开目录的完整页面；正文与本节导航可读。
- `live-375-tools.png`、`live-1080-tools.png`：滚至笔记区；AI 标题保持单行，选择器 130px，控件完整。手机标题高 23.333px；手机导航/正文间距 16px。各视口 document.scrollWidth 等于 innerWidth。
- `live-homework-result.png`：真实 homework submission 1 显示作业结果、作业名称、90 分。
- `live-exam-result.png`：真实 exam submission 1 显示考试结果、考试名称、95 分。
- `live-homework-grading.png`、`live-exam-grading.png`：对应批改标题与领域正确。
- 已操作目录收起/展开、笔记/问答切换。受控测试使用正常 Playwright 点击；真实 browser relay 的稳定性检查在 scrollIntoViewIfNeeded/部分点击超时后，采用 DOM scrollIntoView 与 dispatchEvent('click') 辅助。未修改 Vue 内部状态。

`live-*.png` 中未带 final 的四张早期全页截图及 `live-375-content.png` 保留为发现手机空白时的证据，最终以 live-final-* 为准。无视频课时额外实播和 AI 生成测试，因改动范围为布局与文案。

## 记录

本子 Agent 实际会话文件：`rollout-2026-11-03T08-53-57-01a19ee6-0642-7690-a5ab-86a78e37d691.jsonl`。实际任务调用记录从第 11 行 agent_message 开始，显式 `--start-line 11` 导出至 session/，无继承父会话执行内容。初次曾传 7，实际可见导出仍起于第 11 行；后续均更正为 11。导出只含可见内容且使用私有环境文件脱敏，不复制原始会话或私有环境文件。

最终散列保存在 hashes.json。未提交 Git；保留既有未跟踪历史证据。快照仅截至对应导出时刻，非平台自动记录钩子。
