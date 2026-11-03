# Sol real-browser teaching workflow, 2026-11-02

User authorization relayed by the coordinator: "那就使用6.1sol继续测试". This subagent resumed the unfinished Terra workflow against the existing contest-demo H2 data at frontend 127.0.0.1:5173 and backend 127.0.0.1:8080. Product source and configuration were not modified. Services and synthetic data remain running/intact.

## Scope and evidence

Eight functional checks passed in a real browser against real HTTP services. These are this subagent's observations, not the earlier backend regression or Terra mocked-page counts. No API fixture was needed, no Vue internal state was changed, and the submitted paper was submitted only once.

The fresh browser initially had no application session. Teacher and student logged in through the login form. Ordinary Playwright click waited 30 seconds for stability on the landing/login buttons; force click authenticated but later force/mouse clicks did not open the publish dialog. DOM `dispatchEvent('click')` on visible UI controls did open it. The remaining button/option/radio actions used that DOM event mechanism; text/date inputs used Playwright fill and Tab. These are event-assisted browser checks, not proof that physical mouse interaction works across this browser relay. No application method or private component state was invoked.

| Check | Observed result |
| --- | --- |
| Teacher existing lesson | Course 1, expanded first chapter: `terra-20261102 课时`, Markdown lesson, visible. Screenshot `sol-teacher-lesson.png`. |
| Student existing lesson | From My Classes -> Continue Learning, URL contains `classId=1&lessonId=1`; lesson heading and "人工智能基础 / 这是一次真实教学流程验收。" render. Screenshots `sol-student-lesson.png`, `sol-student-lesson-1440.png`. |
| Publish homework | No homework existed initially. Selected original paper 1 and teaching class 1. Created homework 1, `sol-20261102-teaching-loop`, through the three-step dialog. Open `2026-11-02 18:35:04`, close `2026-11-03 18:33:34` as browser-displayed local wall time. Date inputs filled `YYYY-MM-DD HH:mm:ss` and committed with Tab. Save HTTP 200 and list showed ID 1 / not started / success message. |
| Draft answers | Student started homework after opening, wrote a short answer and chose A (2) for 1+1. Save Draft showed "答案已暂存"; selected radio checked. |
| Submit once | Confirmation submitted submission 1. Real response from `homework-submissions/1/submit`: `PENDING_MANUAL_REVIEW`, objective 50, subjective 0, total 50. Student Completed tab showed 50 / 批改中. Screenshot `sol-student-pending.png`. |
| Teacher manual grading | In homework 1 grading dialog, scored short answer 40/50 and added a comment. `homework-submissions/1/grade-subjective` HTTP 200: `GRADED`, objective 50, subjective 40, total 90. Teacher reload retained scores. Screenshot `sol-teacher-graded.png`. |
| Student final result | After student logged in again, Completed tab showed 90 / 已批改. View showed total 90, objective 50, subjective 40, original answers and teacher comment. Screenshot `sol-student-final-90.png`. |
| Result reload | Direct reload of `exam-result/1?assessmentType=HOMEWORK` retained total 90, component scores and comment. This is a browser reload persistence read, not a backend restart test. |

## Observations and limits

- The initial direct test navigation to `/course-learn/1` omitted required `classId`; the page reported "请求参数错误". Navigating from My Classes supplied the class ID and rendered the lesson. The missing query in the test is an operation error, not evidence that the normal course workflow fails.
- At the initial screenshot width of 1083 pixels, the lesson notes/AI notes sidebar was squeezed, with titles wrapping almost character by character and the knowledge selector appearing clipped. At the separately checked 1440 x 1000 viewport, those controls were laid out normally. This is a limited responsive usability observation, not an all-viewport failure. The coordinator independently inspected both screenshots.
- Homework grading and result views still say "考试批改", "考试结果", and "考试名称". The screenshots and browser text show the terminology mismatch; it did not prevent grading.
- Duplicate chapter titles already existed from the earlier Terra session. They were not recreated here. Earlier chapter save targeting mistakes and unfinished publish attempts are not reclassified as product defects.
- Agent planner timeout, model probes, OCR/Embedding absence, 30 mocked-page checks and earlier build/regression totals remain prior-agent/coordinator evidence. This subagent did not rerun them. Agent confirmation/cancel/execute/audit remains blocked by the previously observed planning timeout.

## Authentic session snapshot

Own source: `rollout-2026-11-02T18-26-41-01a19bcc-06bc-79b0-9d26-eae74d87afd1.jsonl`. Metadata/content were inspected to verify that source line **11** is the actual `NEW_TASK` assignment, and line 12 is this subagent's first tool call. Export uses explicit `--start-line 11 --since 2026-11-02T10:26:41Z --private-env-file .env.local`, omitting setup/fork prefix and hidden reasoning.

The first export was made at close-out, rather than at task start; this timing deviation is recorded. The original visible source was still available. `sol-session/manifest.json` records the bounded source snapshot, line range and hashes. Raw session and private configuration were not copied. The exported snapshot cannot include subsequent messages or its own later export result until refreshed.

Screenshots and `ui-results.json` are final artifacts. `sol-artifact-hashes.json` records their SHA256 values. No standalone replay script was created; exact browser calls and their outputs are in the redacted session snapshot.
