# 传智杯 · AI创新应用挑战赛（10039）

本仓库为“AI智学——面向课程资料的RAG学习助手”的公开评审源码与材料。当前源码采用最新课程工作台、统一助手与课程内资料流程，来源版本和工作区快照见[当前源码说明](docs/competition/submission-10039/repository/CURRENT_SOURCE_SNAPSHOT.json)。

## 录制与评审入口

| 材料 | 入口 |
| --- | --- |
| 新版账号与录制准备 | [当前平台、预置班级和操作步骤](docs/competition/submission-10039/recording/DEMO_READY.md) |
| 核心功能逐句稿 | [从零建课、批量PDF、发布到班级、学生问答与教师检索](docs/competition/submission-10039/recording/CORE_DEMO_0-335.md) |
| 完整视频旁白 | [7分30秒与可剪图谱版本](docs/competition/submission-10039/VIDEO_OUTLINE.md) |
| 可编辑Word技术文档 | [填写身份与编辑正文](docs/competition/submission-10039/TECHNICAL_REPORT.docx) |
| 技术PDF | [技术说明与已归档实测](docs/competition/submission-10039/TECHNICAL_REPORT.pdf) |
| 三条真实Prompt链 | [原始反馈、AI建议、修改与验证](docs/competition/submission-10039/prompt-chains/README.md) |
| 环境指南 | [启动配置与依赖](docs/competition/submission-10039/ENVIRONMENT.md) |
| 历史检索测量 | [固定20题、来源标注与当时结果](docs/competition/submission-10039/retrieval/REPORT.md) |
| 提交核对表 | [比赛要求与材料对应](docs/competition/submission-10039/COMPLIANCE_CHECKLIST.md) |

## 当前流程

教师保存基本信息后自动建立课程专属资料库，一次多选上传课件，设置学生访问并准备课时，再从发布面板选择教学班。学生进入课程后使用“课时快问”，教师可在同一课程的“课程资料”中检索来源，按实际生成结果选录课程图谱。

本机当前演示入口为24679/8082，使用ai-learning-github-latest现有实例。默认开发端口配置和Docker配置随源码提供。模型与数据库私有值按配置模板提供。

新版班级发布相关71项测试通过，真实向导、自动课程库、班级发布、资料开放、教师检索与学生助手已联测。本轮运行联测使用1份合成短资料，正式演示素材为用户指定的8份人工智能PDF。历史报告中的测试和检索数字保留各自真实版本与日期。

原小学期基座及先前AI协作的归档历史保留。当前源码以实际最新工作区冻结快照追加，文件校验使用PUBLIC_FILE_MANIFEST中的Git文件字节。历史检索测量对应源码可从公开历史提交82e53e40a45b4427cd4beb4cbc49a7e2b041c53d复核。

姓名、学校、专业年级和报名组别按参赛者要求留空。提交截止按参赛者确认的北京时间2026年11月18日15:00执行。

[官方赛道页面](https://www.boxuegu.com/matchTrack/detail/?id=10039)
