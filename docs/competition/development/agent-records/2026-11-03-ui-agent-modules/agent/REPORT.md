# Agent 业务规划复测与有限诊断

执行时间：2026-11-03 08:54–09:04，本机实际时区为 +08:00；环境提示的 Asia/Tokyo 未用于冒充日志时区。执行者为独立规划测试子 Agent。未改产品源代码、默认配置或 `.env.local`；未重跑 Maven；未使用模型健康探针作为业务通过证据。

## 范围与反馈回路

- 已读项目 `AGENTS.md`、diagnosing-bugs skill、前轮 TEST-REPORT 与历史超时日志。未找到 GLOSSARY 或 ADR 文件。
- 用 `node docs/competition/development/agent-records/2026-11-03-ui-agent-modules/agent/planning-retest.mjs` 建立可运行反馈回路：教师登录、新建独立会话、发送真实业务请求、断言 `pendingAction.actionCode=course.create` 且 `actionId` 非空。首次实际运行失败，业务文案为两次意图识别超时；HTTP 200 不算业务成功。
- 按限定预算只进行 3 次实际模型业务请求：8080 WRITE、8080 READ、8082 WRITE。另有 2 次本地实验启动配置失败的 chat 请求，均保留且不算真实模型复测。没有通过无界重试获得通过结果。
- 所有会话/课程标题使用 `agent-retest-20261103` 前缀；只使用合成内容和 demo 账号。

## 结果

| 场景 | 服务/会话 | 耗时 | 实际阶段/尝试 | 结果 |
| --- | --- | --- | --- | --- |
| 创建草稿课程 | 8080 / 33 | 10084 ms | intent_select 两次，各约5秒超时 | FAIL，pendingAction=null |
| 查询前缀课程 | 8080 / 34 | 14600 ms | intent_select attempt1=3735ms；read_parameters attempt1=6850ms；随后模型回答 | READ 通路成功，无写动作；不代表课程检索质量全面通过 |
| 独立比较创建课程 | 8082 / 65 | 16179 ms | intent_select attempt1约10秒超时；attempt2约6.1秒空返回 | FAIL，pendingAction=null |

基线细节见 `baseline-course-results.json`、`baseline-read-results.json`、`baseline-stages.log`；有效比较见 `intent10-explicit-course-results.json` 与 `intent10-explicit.stdout.log`。

READ 回答明确说没有匹配标题的课程，指出返回结果中的其他合成课程标题不匹配，并说明未执行操作。初版脚本复用 WRITE 断言，错误要求 READ 也产生 course.create，故原文件有 FAIL；这是测试断言偏差，原文件没有覆盖。`verify-observations.mjs` 从原响应纠正该项，未重发模型请求。READ 日志确认真实选择 `READ/course_search` 并完成读参数阶段。

## 假设与单变量比较

红信号出现后、实验之前，已通过父代理向用户呈现以下排序：

1. 5秒意图截止时间低于模型某些业务请求的延迟；若是直接阻断，将有效截止时间改10秒应跨过意图阶段。
2. 全工具目录使请求变大；若如此，简短READ仍可能有同类延迟。
3. 上游服务/排队不稳定；若如此，10秒仍可能失败且不同请求延迟波动。
4. 格式或修复回合故障；放宽时间后应出现完成日志后的JSON校验错误。

8082 使用现有同一 jar、独立持久化 H2、独立 RAG 目录和独立 JWT；使用 `D:\language\Java\bin\java.exe` 隐藏窗口启动。仅意图有效截止时间 5→10 秒；runtime planning=10、参数=10、chat request=25 保持原值。规划和生成均为完整模型名 `opencode/deepseek-v4.1-flash`，HTTPS和凭据从本机私有配置传入，没有输出凭据。

意图10秒仍失败，说明只增加5→10秒不是充分修复；基线READ的意图3.7秒又说明5秒不是所有请求必败。观察支持延迟波动，并新增“模型内容为空”症状。没有保存上游安全响应结构，不能判定其来自上游空content、reasoning-only、finish_reason、输出限额或适配层。没有证明工具目录体积或格式校验是唯一原因。

源码约束：`SpringAiChatModelClient.positiveTimeout` 对请求 timeout 与 runtime 模型 timeout 取较小值，因此仅把 planner 设置超过10秒不会扩大实际模型截止时间。`AgentChatService` 在规划前创建25秒 deadline，但只将剩余时间传给后续回答模型；规划阶段自行使用阶段截止时间，直接返回计划的分支不会再检查剩余预算。本轮未以超25秒实测确认该潜在预算问题。

## 实验夹具失败与修正

- 第一次8082启动把 `.env.local` 中空 `AI_PLANNING_*` 作为显式空环境值传入，阻止配置继承，模型在约33ms内报 `MODEL_NOT_CONFIGURED`。会话1、`intent10-course-results.json`、`isolated.stdout.log` 保留。
- 尝试用环境API设null移除空值后仍观察到同样错误，约54ms，会话33、`intent10-corrected-course-results.json`、`intent10-corrected.stdout.log` 保留。没有把这次修正尝试冒充成功。
- 随后对空规划base/key/model显式赋同一生成配置，才得到真实10秒模型比较。此为本地启动夹具修正，没有改共享服务或私有配置文件。
- 三个启动进程为43216、43976、20424，均只在8082，前两个在后续启动前关闭。`cleanup-results.json` 确认最后自有8082进程关闭、原8080 PID25896仍运行且监听。

## 无副作用与权限检查

`node .../agent/verify-observations.mjs` 的7项检查通过，见 `verification-results.json`：

- 8080及8082分别查询自身前缀课程，total=0；查询pending列表也未发现本前缀动作。没有确认或创建合成课程。
- 学生向 `/api/agent/actions/confirm` 提交合成不存在actionId、course.create，两个服务均HTTP200 + 业务code403，理由均为“学生账号仅开放 Agent 对话权限”。
- 源码 `AgentActionController.confirm` 先执行 `requireOperationRole()`，拒绝后不进入 `actionExecutionService.confirm()`。故该检查证明角色前置拒绝；不把不存在actionId的拒绝当作真实教师计划的归属/执行授权验证。

确认前无课程写入已观察；待确认计划、取消真实计划、确认一次、重复actionId拒绝、执行审计闭环均因未产生计划为 BLOCKED。未伪造计划绕过模型以冒充闭环。

## 调用预算与后续建议

3次真实业务chat请求的应用级规划调用为6次：WRITE意图2、READ意图1+参数1、比较WRITE意图2；READ后续回答模型1次，共7次应用层真实模型调用尝试。两次部署配置失败另有4次本地规划调用，均在配置校验处失败，无真实上游请求。SDK/代理内部网络重试次数未采集，不能将应用调用数当作精确上游HTTP计数。

本轮停在可复核的诊断结果，不改默认值。后续最小诊断是仅记录安全响应元信息（choices数量、content长度、reasoning字段是否存在、finish_reason、usage、延迟），保留空返回的回归样本；无需输出密钥、私有提示或课程原文。若修复请求总预算，应以跨意图/参数/直接计划分支的慢模型替身写回归，先证明当前deadline不覆盖这些分支，再传递同一剩余预算，避免盲目放大所有timeout。

## 真实记录

源会话：`01a19ee6-8c8a-7553-9894-af334c9c5435`；已检查第11行为实际子代理任务消息、第12行为首个自身工具调用，显式 `--start-line 11 --since 2026-11-03T00:54:32Z`，排除继承前缀。开始、验证节点及反馈前使用仓库导出器刷新 `session/`，均传入 `--private-env-file .env.local`。导出只截至快照，不宣称自动钩子；不复制原始会话、不记录隐藏推理。
