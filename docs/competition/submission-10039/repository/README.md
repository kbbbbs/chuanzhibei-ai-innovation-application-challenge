# 仓库演进材料索引

- [仓库访问说明](REPOSITORY_ACCESS.md)说明仓库链接、评审分支、在线权限及离线核查。
- [来源与合并说明](HISTORY_PROVENANCE.md)说明小学期团队基座、本次AI增量和安全合并口径。
- history-merge-result.json 记录实际谱系合并与状态比对。
- original-to-sanitized-commit-map.json 保留112条原SHA到脱敏SHA的映射。
- sanitized-original-security-scan.json 记录导入当前仓库前的复扫。
- removed-original-paths.json 列出归档时过滤路径与原因。
- history-index.json 保存本轮开始时五个本地仓库/副本的真实元数据。
- history-security-scan.json 记录合并前当前5次安全快照历史扫描。
- working-tree-stat.txt 与 working-tree-status.json 记录本轮初始未提交差异，不把它们伪称为已有提交。

最终提交SHA、统一bundle及源码ZIP的对应关系以根任务最终封包清单为准。
