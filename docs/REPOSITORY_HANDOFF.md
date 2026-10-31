# 新协作仓库的来源与边界

本次把当前本地项目的安全文件快照发布到 `https://github.com/kbbbbs/ai-learning-collaboration`，默认私有。仓库访问者需由所有者在 GitHub Settings → Collaborators 中授权；尚未指定协作者名单，因此没有擅自发出邀请。

原本地工作区的 `origin` 是 `https://github.com/QingYingZero/ai-learning.git`，基线 HEAD 为 `8a59b50`（本次发布前已有大量未提交改动）。新仓库从当前文件建立独立根提交，不把这些改动假装为已经存在的历史提交，也不补造细粒度开发历史。原工作区、Git 历史、暂存区和 origin 保留，不执行重置、强推或历史重写。

后续协作请重新克隆新仓库，从 `main` 创建 `codex/<任务>` 或团队约定的功能分支，用 PR 合入。不要把原工作区的旧 `dev` 历史推到新仓库。当前进展、人工结果模板及迭代要求见 [协作与验收](COLLABORATION_AND_VALIDATION.md)。

本机另有独立协作副本 `D:\ai-learning-collaboration`，其 origin 指向新仓库；它来自已与远端完整提交树核对相同的安全提交。原 `D:\ai-learning` 继续保留原Git归属。首次检查的网络代理/克隆中断及替代核验方式见[发布验证](competition/development/GitHub-publication-20261031/verification.json)，没有关闭TLS证书校验或修改全局代理。

## 上传范围

上传当前源码、测试、锁文件、数据库结构及最小演示种子、配置模板、项目文档/图示和已脱敏开发证据。保留原有项目材料及来源说明，不擅自为原项目添加新的开源许可证。

下列内容保留本机，不进入新仓库：

- `.env.local`、个人密钥、令牌及其他私有配置。
- 旧 Git 历史：已确认含历史凭证，不能随本次快照上传。
- `outputs/**/chrome-profile*/` 浏览器身份/登录缓存。
- `ai-learning-backend/uploads/`、根 `uploads/` 的上传原件，即使原项目已跟踪其中部分文件也排除。
- `database/ai_academy_export.sql`、`database/ai_academy_content_seed.sql` 两份本机实例数据，不能证实其中用户身份/学习记录全部为合成数据。
- 数据库文件、完整课程 OCR/向量、构建产物、依赖及 `.codex_tmp/` 临时工作文件。

数据库初次搭建可使用默认隔离 H2 演示库，或在空 MySQL 实例使用 `database/ai_academy_schema.sql` 的最小示例账号。详细说明见 [环境配置](CONFIGURATION.md)；不要用重建脚本覆盖已有业务数据。未提供的课程材料需由验证者合法取得后导入，不凭空生成“真实”验证来源。

## 记录边界

开发记录为显式脱敏快照，包含来源行号、时间、真实失败与修正，不包含隐藏推理、平台指令、加密载荷或原始私人会话文件。快照之后的最终上传/回复可能尚未归档；不能称已部署自动平台钩子。代码上传不是人工验收完成，更不改变 K03 真实质量仍未达标的事实。

发布预检对已有脱敏记录做了只删除载荷的清理，没有重新读取私人原会话补充内容或改变实际事件时间。部分协作工具参数在平台记录中仅为加密传输串，已用占位符删减；真实委派文字以对应子会话第一条明文信封为依据。[迁移清单](competition/development/GitHub-publication-20261031/evidence-redaction-migration.json)记录脱敏前后文件散列，解释历史验证快照的散列与发布副本的差异。为使新仓库的可达历史也不含这些载荷，本轮刚创建的初始快照在最终发布时被安全替换；不改原项目的任何历史。
