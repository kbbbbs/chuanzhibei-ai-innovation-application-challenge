# 后端全量接口文档

本文档覆盖当前 `dev-integrated` 分支全部后端 Controller 接口。默认服务地址：

```text
http://localhost:8080
```

## 响应规范

主线接口返回 `Result`：

```json
{
  "code": "200",
  "msg": "请求成功",
  "data": {}
}
```

兼容考试分支 `/api/*` 接口返回 `ApiResponse`：

```json
{
  "success": true,
  "data": {},
  "message": "OK"
}
```

常用业务码：

| code | 含义 |
| --- | --- |
| `200` | 请求成功 |
| `400` | 参数错误、请求体错误、业务规则不满足 |
| `401` | 未登录或登录状态失效 |
| `403` | 无权限 |
| `404` | 资源不存在 |
| `409` | 唯一约束、外键约束或关联数据冲突 |
| `500` | 服务器内部错误或登录失败等业务失败 |

鉴权请求头：

```text
Authorization: Bearer <token>
```

## 分页规范

主线列表接口通常支持：

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `pageNum` | integer | `1` | 页码，从 1 开始 |
| `pageSize` | integer | `10` 或业务默认 | 每页数量 |

分页返回通常为 MyBatis Plus `Page`：

```json
{
  "records": [],
  "total": 0,
  "size": 10,
  "current": 1,
  "pages": 0
}
```

## 常用枚举

| 字段 | 可用值 |
| --- | --- |
| `roleCode` | `admin`、`teacher`、`student` |
| `difficulty` | 主线：`beginner/intermediate/advanced`；兼容考试接口：`EASY/MEDIUM/HARD` |
| `resourceType` | `video_link`、`article`、`file_link`、`video_file` |
| `questionType` | `single`、`multiple`、`judge`、`blank`、`short_answer` |
| `/api` 题型 `type` | `SINGLE_CHOICE`、`MULTIPLE_CHOICE`、`TRUE_FALSE`、`FILL_BLANK`、`SHORT_ANSWER` |
| `sourceType` | `homework`、`exam`、`manual`、`import` |
| `noticeType` | `system`、`course`、`homework`、`exam`、`grade` |
| `status` | 多数主线表使用数字状态，常见 `0/1/2/3`，以表字段注释为准 |
| `/api` 试卷状态 | `DRAFT`、`PUBLISHED`、`ARCHIVED` |
| `/api` 考试状态 | `DRAFT`、`PUBLISHED`、`CLOSED` |
| `/api` 答卷状态 | `IN_PROGRESS`、`SUBMITTED`、`PENDING_MANUAL_REVIEW`、`GRADED` |
| `/api` 批改状态 | `NOT_ANSWERED`、`ANSWERED`、`AUTO_CORRECT`、`PENDING_MANUAL_REVIEW`、`MANUALLY_GRADED` |

## Auth

| 方法 | 路径 | 说明 | 请求参数/请求体 |
| --- | --- | --- | --- |
| `POST` | `/auth/register` | 注册用户 | JSON：`username,password,nickname,email,phone,avatar` |
| `POST` | `/auth/login` | 登录并返回 token | JSON：`username,password` |
| `GET` | `/auth/info` | 获取当前登录用户 | Header：`Authorization` |

登录响应示例：

```json
{
  "code": "200",
  "msg": "请求成功",
  "data": {
    "token": "jwt-token",
    "user": {
      "id": 1,
      "username": "admin",
      "roleCode": "admin"
    }
  }
}
```

## 角色 Roles

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/roles` | 分页查询角色 | Query：`roleName,pageNum,pageSize` |
| `GET` | `/roles/{id}` | 查询角色详情 | Path：`id` |
| `POST` | `/roles` | 新增角色 | JSON：`roleName,roleCode` |
| `PUT` | `/roles/{id}` | 修改角色 | JSON：`roleName,roleCode` |
| `DELETE` | `/roles/{id}` | 删除角色 | 有用户关联时返回 `409` |

## 用户 Users

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/users` | 分页查询用户 | Query：`username,status,pageNum,pageSize` |
| `GET` | `/users/{id}` | 查询用户详情 | Path：`id` |
| `POST` | `/users` | 新增用户 | JSON：`username,password,nickname,email,phone,avatar,status,roleId` |
| `PUT` | `/users/{id}` | 修改用户 | JSON：`username,password,nickname,email,phone,avatar,status,roleId` |
| `DELETE` | `/users/{id}` | 删除用户 | 用户被课程引用时返回 `409` |

## 课程分类 Categories

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/categories` | 查询分类列表 | 无 |
| `GET` | `/categories/{categoryId}/courses` | 分页查询分类下课程 | Query：`status,pageNum,pageSize` |
| `POST` | `/categories` | 新增分类 | JSON：`name,description,sortOrder` |
| `PUT` | `/categories/{id}` | 修改分类 | JSON：`name,description,sortOrder` |
| `DELETE` | `/categories/{id}` | 删除分类 | Query：`cascade=true` 可级联删除分类下课程；否则有关联课程时返回 `409` |

## 课程 Courses

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/courses` | 分页查询课程 | Query：`title,categoryId,teacherId,pageNum,pageSize` |
| `GET` | `/courses/my` | 分页查询当前登录用户拥有的课程 | Header：`Authorization`；Query：`pageNum,pageSize` |
| `GET` | `/courses/{id}` | 查询课程详情 | Path：`id` |
| `POST` | `/courses` | 新增课程 | JSON：`categoryId,teacherId,title,description,cover,coverUrl,teacher,difficulty,status` |
| `PUT` | `/courses/{id}` | 修改课程 | JSON：`categoryId,teacherId,title,description,cover,coverUrl,teacher,difficulty,status` |
| `DELETE` | `/courses/{id}` | 删除课程 | Path：`id` |
| `PUT` | `/courses/{id}/view` | 课程浏览量加一 | Path：`id` |
| `GET` | `/courses/{courseId}/my-grade-records` | 分页查询当前用户在课程下可见的成绩 | Header：`Authorization`；Query：`sourceType,pageNum,pageSize` |
| `GET` | `/courses/{courseId}/exam-papers` | 分页查询课程下试卷 | Query：`status,pageNum,pageSize` |
| `GET` | `/courses/{courseId}/my-learning-progress` | 查询当前学生在课程下的学习进度 | Header：`Authorization`；Query：`classId` |
| `GET` | `/courses/{id}/resource-tree` | 查询课程资源树 | 返回课程、章节、课时嵌套结构 |
| `PUT` | `/courses/{courseId}/chapters/sort` | 批量调整课程章节排序 | Header：`Authorization`；JSON Array：`id,sortOrder` |
| `GET` | `/courses/{courseId}/chapters` | 查询课程章节 | Path：`courseId` |

排序请求体：

```json
[
  { "id": 1, "sortOrder": 1 },
  { "id": 2, "sortOrder": 2 }
]
```

## 章节 Chapters

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/chapters` | 分页查询章节 | Query：`courseId,title,pageNum,pageSize` |
| `GET` | `/chapters/{id}` | 查询章节详情 | Path：`id` |
| `POST` | `/chapters` | 新增章节 | JSON：`courseId,title,description,sortOrder` |
| `PUT` | `/chapters/{id}` | 修改章节 | JSON：`courseId,title,description,sortOrder` |
| `DELETE` | `/chapters/{id}` | 删除章节 | Path：`id` |
| `GET` | `/chapters/{chapterId}/lessons` | 查询章节课时 | Path：`chapterId` |
| `PUT` | `/chapters/{chapterId}/lessons/sort` | 批量调整课时排序 | Header：`Authorization`；JSON Array：`id,sortOrder` |

## 课时 Lessons

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/lessons` | 分页查询课时 | Query：`chapterId,title,pageNum,pageSize` |
| `GET` | `/lessons/{id}` | 查询课时详情 | Path：`id` |
| `GET` | `/lessons/{lessonId}/my-progress` | 查询当前用户可见的课时学习进度 | Header：`Authorization`；Query：`classId,pageNum,pageSize` |
| `POST` | `/lessons` | 新增课时 | JSON：`chapterId,title,resourceType,resourceUrl,videoUrl,content,duration,sortOrder,status` |
| `PUT` | `/lessons/{id}` | 修改课时 | JSON：`chapterId,title,resourceType,resourceUrl,videoUrl,content,duration,sortOrder,status` |
| `DELETE` | `/lessons/{id}` | 删除课时 | Path：`id` |
| `PUT` | `/lessons/{lessonId}/progress` | 学生更新课时进度 | Header：`Authorization`；JSON：`classId,progress,learnedSeconds,lastPosition` |

学习进度更新请求体：

```json
{
  "classId": 1,
  "progress": 80,
  "learnedSeconds": 1200,
  "lastPosition": 600
}
```

## 教学班 Teaching Classes

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/teaching-classes` | 分页查询教学班 | Query：`courseId,teacherId,className,pageNum,pageSize` |
| `GET` | `/teaching-classes/my` | 分页查询当前登录用户所属或所教教学班 | Header：`Authorization`；Query：`status,pageNum,pageSize` |
| `GET` | `/teaching-classes/{id}` | 查询教学班详情 | Path：`id` |
| `GET` | `/teaching-classes/{classId}/students` | 查询教学班学生 | Path：`classId` |
| `POST` | `/teaching-classes` | 新增教学班 | JSON：`courseId,teacherId,className,semester,description,status` |
| `PUT` | `/teaching-classes/{id}` | 修改教学班 | JSON：`courseId,teacherId,className,semester,description,status` |
| `DELETE` | `/teaching-classes/{id}` | 删除教学班 | Path：`id` |

## 班级学生 Class Students

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/class-students` | 分页查询班级学生 | Query：`classId,studentId,pageNum,pageSize` |
| `GET` | `/class-students/{id}` | 查询班级学生详情 | Path：`id` |
| `POST` | `/class-students` | 学生加入教学班 | JSON：`classId,studentId,status`；重复加入返回 `409` |
| `PUT` | `/class-students/{id}` | 修改班级学生状态 | JSON：`classId,studentId,status` |
| `DELETE` | `/class-students/{id}` | 删除班级学生 | Path：`id` |

## 学习进度 Learning Progress

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/learning-progress` | 分页查询学习进度 | Query：`classId,studentId,lessonId,pageNum,pageSize` |
| `GET` | `/learning-progress/{id}` | 查询学习进度详情 | Path：`id` |
| `POST` | `/learning-progress` | 新增学习进度 | JSON：`classId,studentId,lessonId,progress,learnedSeconds,lastPosition,lastLearnTime` |
| `PUT` | `/learning-progress/{id}` | 修改学习进度 | JSON：`classId,studentId,lessonId,progress,learnedSeconds,lastPosition,lastLearnTime` |
| `DELETE` | `/learning-progress/{id}` | 删除学习进度 | Path：`id` |

## 作业 Homeworks

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/homeworks` | 分页查询作业 | Query：`classId,teacherId,title,pageNum,pageSize` |
| `GET` | `/homeworks/{id}` | 查询作业详情 | Path：`id` |
| `GET` | `/homeworks/{homeworkId}/submits` | 查询作业提交记录 | Path：`homeworkId` |
| `POST` | `/homeworks` | 新增作业 | JSON：`classId,teacherId,title,content,attachmentUrl,fullScore,startTime,deadline,status` |
| `PUT` | `/homeworks/{id}` | 修改作业 | JSON：`classId,teacherId,title,content,attachmentUrl,fullScore,startTime,deadline,status` |
| `DELETE` | `/homeworks/{id}` | 删除作业 | Path：`id` |

## 作业提交 Homework Submits

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/homework-submits` | 分页查询作业提交 | Query：`homeworkId,studentId,pageNum,pageSize` |
| `GET` | `/homework-submits/{id}` | 查询提交详情 | Path：`id` |
| `POST` | `/homework-submits` | 新增提交或批改记录 | JSON：`homeworkId,studentId,submitContent,attachmentUrl,score,feedback,gradeStatus,graderId,gradeTime` |
| `PUT` | `/homework-submits/{id}` | 修改提交或批改记录 | JSON：`homeworkId,studentId,submitContent,attachmentUrl,score,feedback,gradeStatus,graderId,gradeTime` |
| `DELETE` | `/homework-submits/{id}` | 删除提交记录 | Path：`id` |

## 题库 Questions

主线题库接口直接使用 `t_question` 表模型。

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/questions` | 分页查询题目 | Query：`courseId,teacherId,questionType,pageNum,pageSize` |
| `GET` | `/questions/{id}` | 查询题目详情 | Path：`id` |
| `POST` | `/questions` | 新增题目 | JSON：`courseId,teacherId,questionType,title,options,answer,analysis,difficulty,score,status` |
| `PUT` | `/questions/{id}` | 修改题目 | JSON：`courseId,teacherId,questionType,title,options,answer,analysis,difficulty,score,status` |
| `DELETE` | `/questions/{id}` | 删除题目 | Path：`id` |

## 试卷 Exam Papers

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/exam-papers` | 分页查询试卷 | Query：`courseId,teacherId,title,pageNum,pageSize` |
| `GET` | `/exam-papers/{id}` | 查询试卷详情 | Path：`id` |
| `GET` | `/exam-papers/{paperId}/questions` | 查询试卷题目 | Path：`paperId` |
| `POST` | `/exam-papers` | 新增试卷 | JSON：`courseId,teacherId,title,description,totalScore,durationMinutes,status` |
| `PUT` | `/exam-papers/{id}` | 修改试卷 | JSON：`courseId,teacherId,title,description,totalScore,durationMinutes,status` |
| `DELETE` | `/exam-papers/{id}` | 删除试卷 | Path：`id` |

## 试卷题目 Exam Paper Questions

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/exam-paper-questions` | 分页查询试卷题目 | Query：`paperId,questionId,pageNum,pageSize` |
| `GET` | `/exam-paper-questions/{id}` | 查询试卷题目详情 | Path：`id` |
| `POST` | `/exam-paper-questions` | 新增试卷题目 | JSON：`paperId,questionId,score,sortOrder,requiredAnswer` |
| `PUT` | `/exam-paper-questions/{id}` | 修改试卷题目 | JSON：`paperId,questionId,score,sortOrder,requiredAnswer` |
| `DELETE` | `/exam-paper-questions/{id}` | 删除试卷题目 | Path：`id` |

## 考试安排 Exam Sessions

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/exam-sessions` | 分页查询考试安排 | Query：`classId,paperId,teacherId,title,pageNum,pageSize` |
| `GET` | `/exam-sessions/{id}` | 查询考试安排详情 | Path：`id` |
| `POST` | `/exam-sessions` | 新增考试安排 | JSON：`classId,paperId,teacherId,title,description,startTime,endTime,duration,status` |
| `PUT` | `/exam-sessions/{id}` | 修改考试安排 | JSON：`classId,paperId,teacherId,title,description,startTime,endTime,duration,status` |
| `DELETE` | `/exam-sessions/{id}` | 删除考试安排 | Path：`id` |
| `POST` | `/exam-sessions/{id}/publish` | 发布考试 | 仅草稿状态可发布 |
| `POST` | `/exam-sessions/{id}/close` | 关闭考试 | 仅已发布状态可关闭 |
| `POST` | `/exam-sessions/{id}/records/start` | 开始考试 | Query：`candidateId` 或 `studentId` |

## 考试记录 Exam Records

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/exam-records` | 分页查询考试记录 | Query：`examSessionId,studentId,pageNum,pageSize` |
| `GET` | `/exam-records/{id}` | 查询考试记录详情 | Path：`id` |
| `POST` | `/exam-records` | 新增考试记录 | JSON：`examSessionId,studentId,candidateId,startTime,submitTime,totalScore,objectiveScore,subjectiveScore,status,comment` |
| `PUT` | `/exam-records/{id}` | 修改考试记录 | JSON：`examSessionId,studentId,candidateId,startTime,submitTime,totalScore,objectiveScore,subjectiveScore,status,comment` |
| `DELETE` | `/exam-records/{id}` | 删除考试记录 | Path：`id` |
| `POST` | `/exam-records/{id}/answers` | 保存考试答案 | JSON：见“考试流程请求体” |
| `POST` | `/exam-records/{id}/submit` | 交卷并自动批改客观题 | Path：`id` |
| `GET` | `/exam-records/{id}/student` | 查询学生答题视图 | Path：`id` |
| `POST` | `/exam-records/{id}/grade-subjective` | 人工批改主观题 | JSON：见“考试流程请求体” |

## 考试答题 Exam Answers

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/exam-answers` | 分页查询考试答题 | Query：`examRecordId,questionId,pageNum,pageSize` |
| `GET` | `/exam-answers/{id}` | 查询考试答题详情 | Path：`id` |
| `POST` | `/exam-answers` | 新增考试答题 | JSON：`examRecordId,questionId,paperQuestionId,studentAnswer,correctAnswer,score,isCorrect,gradeStatus,graderId,gradeTime,comment` |
| `PUT` | `/exam-answers/{id}` | 修改考试答题 | JSON：`examRecordId,questionId,paperQuestionId,studentAnswer,correctAnswer,score,isCorrect,gradeStatus,graderId,gradeTime,comment` |
| `DELETE` | `/exam-answers/{id}` | 删除考试答题 | Path：`id` |

## 成绩 Grade Records

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/grade-records` | 分页查询成绩 | Query：`studentId,classId,courseId,sourceType,pageNum,pageSize` |
| `GET` | `/grade-records/{id}` | 查询成绩详情 | Path：`id` |
| `POST` | `/grade-records` | 新增成绩 | JSON：`studentId,classId,courseId,teacherId,gradeName,sourceType,sourceId,score,fullScore,comment` |
| `PUT` | `/grade-records/{id}` | 修改成绩 | JSON：`studentId,classId,courseId,teacherId,gradeName,sourceType,sourceId,score,fullScore,comment` |
| `DELETE` | `/grade-records/{id}` | 删除成绩 | Path：`id` |

## 成绩导入日志 Grade Import Logs

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/grade-import-logs` | 分页查询导入日志 | Query：`teacherId,classId,pageNum,pageSize` |
| `GET` | `/grade-import-logs/{id}` | 查询导入日志详情 | Path：`id` |
| `POST` | `/grade-import-logs` | 新增导入日志 | JSON：`teacherId,classId,fileName,fileUrl,totalCount,successCount,failCount,errorMessage` |
| `PUT` | `/grade-import-logs/{id}` | 修改导入日志 | JSON：`teacherId,classId,fileName,fileUrl,totalCount,successCount,failCount,errorMessage` |
| `DELETE` | `/grade-import-logs/{id}` | 删除导入日志 | Path：`id` |

## 通知 Notices

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/notices` | 分页查询通知 | Query：`publisherId,classId,noticeType,pageNum,pageSize` |
| `GET` | `/notices/{id}` | 查询通知详情 | Path：`id` |
| `POST` | `/notices` | 新增通知 | JSON：`publisherId,classId,title,content,noticeType,status` |
| `PUT` | `/notices/{id}` | 修改通知 | JSON：`publisherId,classId,title,content,noticeType,status` |
| `DELETE` | `/notices/{id}` | 删除通知 | Path：`id` |

## 操作日志 Operation Logs

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/operation-logs` | 分页查询操作日志 | Query：`userId,operation,pageNum,pageSize` |
| `GET` | `/operation-logs/{id}` | 查询操作日志详情 | Path：`id` |
| `POST` | `/operation-logs` | 新增操作日志 | JSON：`userId,operation,method,url,params,ip` |
| `PUT` | `/operation-logs/{id}` | 修改操作日志 | JSON：`userId,operation,method,url,params,ip` |
| `DELETE` | `/operation-logs/{id}` | 删除操作日志 | Path：`id` |

## 文件 Files

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `POST` | `/files/upload` | 上传文件 | Header：`Authorization`；`multipart/form-data`：`file`；Query：`bizType` |
| `DELETE` | `/files` | 删除上传文件 | Header：`Authorization`；Query：`url` |

`bizType` 可用值：

```text
course-cover, lesson-resource, homework, grade, common
```

允许上传扩展名：

```text
jpg,jpeg,png,gif,webp,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,md,zip,rar,7z,mp4,webm,mov,avi,mkv
```

上传响应：

```json
{
  "code": "200",
  "msg": "请求成功",
  "data": {
    "url": "/uploads/course-cover/20260703/xxx.png",
    "fileName": "xxx.png",
    "originalName": "cover.png",
    "contentType": "image/png",
    "size": 12345
  }
}
```

## 论文检索 Paper Search

所有接口以 `/api/paper` 开头。

### 搜索论文

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| `GET` | `/api/paper/search` | 多源论文搜索 | Query：`keyword,source,page,size` |

参数说明：

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | **必填** | 搜索关键词 |
| `source` | string | `local` | 数据源：`local`（本地库）、`arxiv`、`openalex`、`semantic_scholar` |
| `page` | integer | `1` | 页码 |
| `size` | integer | `10` | 每页数量 |

搜索响应示例：

```json
{
  "code": "200",
  "msg": "请求成功",
  "data": {
    "total": 42,
    "data": [
      {
        "paperId": "2301.12345",
        "title": "Attention Is All You Need",
        "year": 2023,
        "authors": [
          { "authorId": null, "name": "John Doe" }
        ],
        "abstractText": "We propose a novel...",
        "url": "https://arxiv.org/abs/2301.12345",
        "openAccessPdf": { "url": "https://arxiv.org/pdf/2301.12345.pdf", "status": "OA" },
        "venue": "NeurIPS 2023",
        "citationCount": 150,
        "source": "arxiv"
      }
    ]
  }
}
```

### 论文详情

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| `GET` | `/api/paper/detail/{paperId}` | 查询本地论文详情 | Path：`paperId`（arXiv ID） |

### 导入论文

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| `POST` | `/api/paper/import` | 从 arXiv 批量导入论文到本地库 | Query：`keyword,maxResults` |

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | **必填** | 搜索关键词 |
| `maxResults` | integer | `20` | 最大导入数量，上限 100，已存在则跳过 |

导入响应示例：

```json
{
  "code": "200",
  "msg": "请求成功",
  "data": 15
}
```

业务说明：

- 本地搜索优先使用 MySQL 全文索引（`MATCH ... AGAINST`），无结果时回退为 `LIKE` 模糊查询。
- arXiv 和 OpenAlex 为实时调用外部 API，Semantic Scholar 需配置 `academic.semantic-scholar.api-key`。
- 导入时会根据 `arxiv_id` 去重，已存在的论文自动跳过。

## WeKnora 集成

所有接口以 `/api/weknora` 开头，用于对接 WeKnora 知识库管理系统。

前置配置（`application.yml`）：

```yaml
weknora:
  base-url: "http://localhost:8081"
  api-key: "sk-your-api-key"
```

### 知识库

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/weknora/knowledge-bases` | 获取知识库列表 |
| `POST` | `/api/weknora/knowledge-bases/{kbId}/upload` | 上传文件到知识库 |

文件上传参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `kbId` | string（Path） | 知识库 ID |
| `fileName` | string（Query） | 文件名 |
| `fileBytes` | byte[]（Body） | 文件二进制内容 |

### 知识检索

| 方法 | 路径 | 说明 | 请求体 |
| --- | --- | --- | --- |
| `POST` | `/api/weknora/search` | 知识库检索 | JSON：`knowledgeBaseIds,query` |

请求体示例：

```json
{
  "knowledgeBaseIds": ["kb-abc123"],
  "query": "什么是 Transformer 架构"
}
```

### 会话管理

| 方法 | 路径 | 说明 | 请求体/参数 |
| --- | --- | --- | --- |
| `POST` | `/api/weknora/sessions` | 创建会话 | JSON：`knowledgeBaseId,title,agentId,strategy` |
| `GET` | `/api/weknora/sessions` | 获取会话列表 | 无 |
| `DELETE` | `/api/weknora/sessions/{sessionId}` | 删除会话 | Path：`sessionId` |
| `POST` | `/api/weknora/sessions/{sessionId}/stop` | 停止会话 | Path：`sessionId` |

创建会话请求体：

```json
{
  "knowledgeBaseId": "kb-abc123",
  "title": "论文研读",
  "agentId": "agent-001",
  "strategy": { "topK": 5 }
}
```

### SSE 流式问答

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| `POST` | `/api/weknora/knowledge-chat/{sessionId}` | 知识问答（SSE 流） | Path：`sessionId`；Query：`query` |
| `POST` | `/api/weknora/agent-chat/{sessionId}` | Agent 问答（SSE 流） | Path：`sessionId`；Query：`query,agentId` |

SSE 事件格式：

```text
data: {"answer": "Transformer 架构由..."}
data: [DONE]
```

- 事件名 `message`：推送回答片段。
- 事件名 `done`：流结束。
- 事件名 `error`：发生错误。

业务说明：

- 所有接口均通过 `X-API-Key` 请求头透传至 WeKnora 服务端。
- SSE 接口超时时间为 5 分钟（300 秒）。
- 上游 WeKnora 服务不可用时返回 `500` 错误。

## 兼容考试分支：题库 `/api/questions`

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/api/questions` | 分页查询结构化题目 | Query：`type,difficulty,status,page,size` |
| `GET` | `/api/questions/{id}` | 查询结构化题目详情 | Path：`id` |
| `POST` | `/api/questions` | 创建结构化题目 | JSON：`courseId,teacherId,content,type,difficulty,score,knowledgePoint,analysis,status,options,answers` |
| `PUT` | `/api/questions/{id}` | 修改结构化题目 | JSON：同创建 |
| `DELETE` | `/api/questions/{id}` | 删除结构化题目 | Path：`id` |

题目请求示例：

```json
{
  "courseId": 1,
  "teacherId": 2,
  "content": "Java 的编译结果是？",
  "type": "SINGLE_CHOICE",
  "difficulty": "EASY",
  "score": 5,
  "knowledgePoint": "Java 基础",
  "analysis": "编译后生成字节码",
  "status": "ACTIVE",
  "options": [
    { "optionKey": "A", "optionContent": "字节码", "sortOrder": 1 },
    { "optionKey": "B", "optionContent": "机器码", "sortOrder": 2 }
  ],
  "answers": [
    { "answerContent": "A", "sortOrder": 1 }
  ]
}
```

## 兼容考试分支：试卷 `/api/papers`

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/api/papers` | 查询试卷列表 | 无 |
| `GET` | `/api/papers/{id}` | 查询试卷详情和题目 | Path：`id` |
| `POST` | `/api/papers` | 创建试卷并组题 | JSON：`courseId,teacherId,name,description,durationMinutes,status,questions` |
| `PUT` | `/api/papers/{id}` | 修改草稿试卷 | 已发布或已归档试卷不可修改 |
| `DELETE` | `/api/papers/{id}/questions/{paperQuestionId}` | 从试卷移除题目 | 已发布/已归档/已被答卷使用时不可删除 |

试卷请求示例：

```json
{
  "courseId": 1,
  "teacherId": 2,
  "name": "Java 期末试卷",
  "description": "期末考试",
  "durationMinutes": 120,
  "status": "PUBLISHED",
  "questions": [
    { "questionId": 1, "sortOrder": 1, "score": 5, "requiredAnswer": true },
    { "questionId": 2, "sortOrder": 2, "score": 10, "requiredAnswer": true }
  ]
}
```

## 兼容考试分支：考试 `/api/exams`

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/api/exams` | 查询考试列表 | 无 |
| `GET` | `/api/exams/{id}` | 查询考试详情 | Path：`id` |
| `POST` | `/api/exams` | 创建考试 | 只能使用已发布试卷 |
| `PUT` | `/api/exams/{id}` | 修改草稿考试 | 已发布或已关闭考试不可修改 |
| `POST` | `/api/exams/{id}/publish` | 发布考试 | 仅草稿考试可发布 |
| `POST` | `/api/exams/{id}/close` | 关闭考试 | 仅已发布考试可关闭 |
| `POST` | `/api/exams/{id}/submissions/start` | 开始考试 | Query：`candidateId` |

考试请求示例：

```json
{
  "classId": 1,
  "teacherId": 2,
  "name": "Java 期末考试",
  "paperId": 1,
  "description": "第一场",
  "startTime": "2026-07-03T10:00:00",
  "endTime": "2026-07-03T12:00:00",
  "durationMinutes": 120,
  "status": "DRAFT"
}
```

## 兼容考试分支：答卷 `/api/submissions`

| 方法 | 路径 | 说明 | 参数/请求体 |
| --- | --- | --- | --- |
| `GET` | `/api/submissions` | 查询答卷列表 | Query：`examId,candidateId` |
| `GET` | `/api/submissions/{id}` | 查询答卷和批改结果 | Path：`id` |
| `GET` | `/api/submissions/{id}/student` | 查询学生答题视图 | Path：`id` |
| `POST` | `/api/submissions/{id}/answers` | 保存答案 | JSON：`answers` |
| `POST` | `/api/submissions/{id}/submit` | 交卷 | 客观题自动判分，主观题进入待人工批改 |
| `POST` | `/api/submissions/{id}/grade-subjective` | 人工批改主观题 | JSON：`graderId,grades` |

## 考试流程请求体

保存答案：

```json
{
  "answers": [
    {
      "paperQuestionId": 1,
      "answerContent": "A",
      "selectedOptionKeys": ["A"]
    }
  ]
}
```

说明：

- 单选、判断、多选可用 `selectedOptionKeys`。
- 填空、简答可用 `answerContent`。
- 多选答案会排序后以逗号拼接判分。

人工批改：

```json
{
  "graderId": 2,
  "grades": [
    {
      "submissionAnswerId": 1,
      "score": 9,
      "comment": "回答完整"
    }
  ]
}
```

业务规则：

- 考试未发布、未到开始时间、已超过结束时间时不能开始答题。
- 同一 `candidateId` 或 `studentId` 已提交/已批改后不能重复参加。
- 进行中的答卷再次开始时返回原答卷，允许继续作答。
- 保存答案时会校验答卷必须处于进行中且未超时。
- 交卷后客观题自动判分；简答题进入待人工批改。
- 主观题人工分数不能超过题目分值。

## Apifox 全量测试报告

最近一次全量接口测试：

| 项 | 值 |
| --- | --- |
| 生成时间 | `2026-07-03T15:52:35` |
| Project ID | `8527143` |
| Environment ID | `47031800` |
| Base URL | `http://localhost:8080` |
| 场景数 | `4` |
| Custom HTTP 步骤数 | `176` |
| 结果 | 全部通过 |

报告文件保存在：

| 场景 | HTML | JSON | JUnit XML | CLI Log |
| --- | --- | --- | --- | --- |
| 全量API-正常情况 | `.apifox-run/reports/全量API-正常情况.html` | `.apifox-run/reports/全量API-正常情况.json` | `.apifox-run/reports/全量API-正常情况.xml` | `.apifox-run/reports/全量API-正常情况.cli.txt` |
| 全量API-空值情况 | `.apifox-run/reports/全量API-空值情况.html` | `.apifox-run/reports/全量API-空值情况.json` | `.apifox-run/reports/全量API-空值情况.xml` | `.apifox-run/reports/全量API-空值情况.cli.txt` |
| 全量API-输入不合法 | `.apifox-run/reports/全量API-输入不合法.html` | `.apifox-run/reports/全量API-输入不合法.json` | `.apifox-run/reports/全量API-输入不合法.xml` | `.apifox-run/reports/全量API-输入不合法.cli.txt` |
| 全量API-完整性约束 | `.apifox-run/reports/全量API-完整性约束.html` | `.apifox-run/reports/全量API-完整性约束.json` | `.apifox-run/reports/全量API-完整性约束.xml` | `.apifox-run/reports/全量API-完整性约束.cli.txt` |

运行摘要：

```text
.apifox-run/run-summary.json
```
