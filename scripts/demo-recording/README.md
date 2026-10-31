# 三角色录屏演示数据

这套脚本为学生端、教师端和管理员端建立一条独立、可重复、可重置的录屏主线。它不会修改现有的 `course=109` 或 `lesson=900`；当二者仍是预期的“互动/梯度下降”内容时，只复制其互动 JSON 到一门 `DEMO_` 课程中。

## 一键使用

在仓库根目录执行：

```powershell
# 首次写入或再次校准（幂等）
.\scripts\demo-recording\apply.ps1

# 录制/彩排后：定向清理并重建标准基线
.\scripts\demo-recording\reset.ps1

# 只读查看账号、动态 ID、路由、考试和岗位状态
.\scripts\demo-recording\status.ps1
```

脚本默认读取项目的本地开发配置：`localhost:3306/ai_academy`、`root`、密码 `111111`。可用环境变量 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`、`MYSQL_PATH` 覆盖，也可直接传参：

```powershell
.\scripts\demo-recording\apply.ps1 `
  -HostName localhost -Port 3306 -Database ai_academy `
  -Username root -Password '<your-password>' `
  -MySqlPath 'D:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
```

密码通过临时 `MYSQL_PWD` 环境变量交给客户端，不会拼进进程参数；脚本结束后会恢复调用前的环境。

## 演示账号

| 角色 | 用户名 | 密码 | 演示姓名 |
| --- | --- | --- | --- |
| 管理员 | `demo_admin` | `123456` | 演示管理员 |
| 教师 | `demo_teacher` | `123456` | 陈老师（演示） |
| 学生 | `demo_student` | `123456` | 林同学（演示） |

`status.ps1` 会打印当前数据库中的动态自增 ID。不要把某次运行产生的课程、教学班、课时或考试 ID 写死到录屏脚本中；以状态输出中的 `student_learning_route` 为准。

## 标准数据故事

- 1 门 `DEMO_互动式机器学习实验`，包含 1 章和 1 个互动课时；优先复制校验通过的 `course=109 / lesson=900` 内容，缺失时使用内置的最小互动 JSON。
- 1 个进行中的教学班、1 个行政班，`demo_student` 同时是成员；学习进度固定为 72%，含两条学习行为。
- 1 张 100 分试卷（60 分单选 + 40 分简答），配 4 场考试：
  - 可立即参加：无历史答卷；
  - 两场成绩已发布：78 分和 88 分；
  - 一场待人工批改：客观题 60 分，简答题已作答。
- 1 个新版 `PUBLISHED` 课后作业，复用上述两题；开始/截止时间有效，`demo_student` 尚无提交，可在学生端展示“待完成”，教师端展示“已发布”。
- 2 个开放岗位；学生已申请其中一个且状态为待审核，另一个可现场申请。
- 1 条首页人工推荐。由于学生已加入该课程的活动教学班，推荐服务可生成学习路由。
- 1 条待审核教师入驻申请、1 条班级公告和 3 条演示操作日志。

## 重置边界

`reset.ps1` 先执行 `reset_demo_recording.sql`，再重新执行主 SQL。清理条件仅包含：

- 三个专用 `demo_` 账号产生的活动；
- `DEMO_` 前缀的课程、章节、课时、班级、考试、题目、岗位、申请、公告和日志；
- `demo_teacher` 拥有的课程/教学对象，以及 `demo_student` 的专用学习、答题和岗位申请记录。

它不会 `DROP DATABASE`、不会清空整张业务表，也不会删除三个演示账号（保留账号 ID 可使多次重置更稳定）。主 SQL会重新校准账号密码、启用状态和唯一主角色。

## 录制前检查

运行 `status.ps1`，首张表必须显示 `READY`。同时确认：

- 三个账号各自只有 `admin`、`teacher`、`student` 一个主角色；
- `interactive_content_source` 是 `copied_from_lesson_900` 或 `fallback_json`；
- 4 场考试分别有 2 条 `GRADED`、1 条 `PENDING_MANUAL_REVIEW`、1 条无答卷的 `PUBLISHED`；
- 新版作业为 `PUBLISHED`、2 题共 100 分、`submission_count=0`；
- 汇总数为：3 账号、1 课程、1 教学班、1 成员、1 行政班、4 考试、3 答卷、1 作业、0 作业答卷、2 岗位、1 推荐、1 待审核教师申请。

主数据定义位于 [`database/demo_recording.sql`](../../database/demo_recording.sql)。
