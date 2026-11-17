# 传智杯 · AI创新应用挑战赛（10039）

本仓库为传智杯 **AI创新应用挑战赛** 的公开评审仓库。赛道详情中的开发主题为 Vibe Coding，作品选题方向为“场景化轻量 RAG 知识库”。作品是 **AI智学平台——面向课程资料的 RAG 学习助手**。

## 评审入口

| 材料 | 入口 |
| --- | --- |
| 可编辑 Word 技术文档 | [填写身份与编辑正文](docs/competition/submission-10039/TECHNICAL_REPORT.docx) |
| 正式技术 PDF | [16页技术文档](docs/competition/submission-10039/TECHNICAL_REPORT.pdf) |
| 逐句视频旁白 | [时间轴、画面、操作与每句旁白](docs/competition/submission-10039/VIDEO_OUTLINE.md) |
| 录制账号与入口 | [预置入班关系、发布步骤与页面入口](docs/competition/submission-10039/recording/DEMO_READY.md) |
| 录屏取材 | [真实反馈、事件日期和来源卡片](docs/competition/submission-10039/recording/录屏取材说明.md) |
| 核心功能逐句稿 | [从零建课、班级发布、八份PDF问答与检索](docs/competition/submission-10039/recording/CORE_DEMO_0-335.md) |
| 三条核心 Prompt 链 | [原提问、AI建议、修复与验证](docs/competition/submission-10039/prompt-chains/README.md) |
| 复现指南 | [环境、依赖、配置、账号和业务步骤](docs/competition/submission-10039/ENVIRONMENT.md) |
| 实测结果 | [固定20题检索报告](docs/competition/submission-10039/retrieval/REPORT.md) |
| 提交核对表 | [逐项要求与材料对应](docs/competition/submission-10039/COMPLIANCE_CHECKLIST.md) |
| 历史与来源 | [原基座、脱敏映射和提交谱系](docs/competition/submission-10039/repository/HISTORY_PROVENANCE.md) |

## 作品流程

教师上传课程资料并授权阅读。学生在课时正文或笔记中选中文字，结合课程已授权资料提问，查看回答与出处。没有命中资料且有选文时，页面显示“仅依据选中文本”。

核心源码位于 `ai-learning-backend/`、`ai-learning-frontend/`，数据库结构在 `database/`，启动和验证工具在 `scripts/competition/`。配置模板为根目录 `.env.example`，按复现指南填写本机私有模型服务配置。

## 实测与版本

2026-11-17固定20题测量的Top5已标注来源命中率为混合检索17/20（85%）、关键词对照18/20（90%）。36页资料中32页形成有效内容，4页失败仍保留在题目分母中。该指标衡量来源页命中。

本机最新工作区后端回归439通过、19跳过、0失败，前端受控浏览器51项通过、公式展示8项通过。真实教师与学生课时问答、来源读取及撤权处理已联测。本轮另通过学生课程可见性浏览器测试，真实教师启用课程后，预先入班的学生刷新可见。本轮另通过学生课程可见性浏览器测试，真实教师启用课程后，预先入班的学生刷新可见。各结果的实际日期、环境与覆盖范围保存在对应报告中。

历史包含112条小学期管理系统提交、5条后续协作提交和2条实际归档提交。原作者、时间和信息保留，旧凭据及私有路径脱敏后导入，原SHA映射随材料提供。发布提交在上述119条归档历史后追加公开评审文档及首页整理。原始AI迭代中未保存的中间提交按真实Prompt快照补充说明。

姓名、学校、专业年级和报名组别按参赛者要求留空。录制视频时采用7分30秒大纲，其中AI协作展示3分钟。提交截止按参赛者确认的北京时间2026年11月18日15:00执行。

[官方赛道页面](https://www.boxuegu.com/matchTrack/detail/?id=10039)

当前比赛录制入口为本机24678/8080。专用学生预先加入人工智能一班，教师从零创建人工智能基础课程，再关联班级并启用。图谱55秒片段可独立剪除，成片可为7分30秒或6分35秒。
