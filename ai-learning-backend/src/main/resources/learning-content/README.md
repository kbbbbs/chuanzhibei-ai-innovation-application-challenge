# Learning content structure

Amazing Teaching 的课程内容不写在 Java 源码里，统一放在本地内容库和数据库中。

## 文件

- `templates/index.json`：内置互动课本模板索引，供教师在添加课时时选择。
- `templates/<subject>/<name>.json`：可交互课本页面。一个 JSON 页面由多个 block 组成，block 可以是 Markdown、实验、在线 IDE、检查点、挑战题或资料链接。
- `catalog.json`：课程画像，用于课程概览和学习地图文案。

## 课时页面 JSON

教师创建 Markdown 课时时，系统会把 Markdown 保存成一个只有 `markdown` block 的 JSON。教师选择内置模板或上传 JSON 时，保存同一套结构。

```json
{
  "schemaVersion": 1,
  "title": "梯度下降：学习率与收敛",
  "subject": "机器学习",
  "summary": "用曲线、滑杆和 Python 循环理解学习率。",
  "estimatedMinutes": 28,
  "blocks": [
    {
      "type": "markdown",
      "title": "核心讲解",
      "content": "## 学习率是什么\n\n学习率决定每一步走多远。"
    },
    {
      "type": "experiment",
      "title": "拖动学习率",
      "description": "观察曲线如何收敛或震荡。",
      "kind": "gradient",
      "defaults": { "learningRate": 0.12, "startX": -4 }
    },
    {
      "type": "ide",
      "title": "Python：最小梯度下降",
      "language": "python",
      "starterCode": "x = -4.0\nlr = 0.12\nprint(x)",
      "expectedOutputHint": "修改 lr 后比较输出。"
    },
    {
      "type": "checkpoint",
      "title": "完成检查",
      "items": ["我能解释学习率", "我运行过代码"]
    }
  ]
}
```

## 支持的 block 类型

- `markdown`：正文、例子、表格、代码片段。
- `experiment`：互动实验。当前支持 `gradient`、`classification`、`state`、`pipeline`、`join`、`structure`、`maze`、`cards`。
- `ide`：在线 IDE。字段包括 `language`、`starterCode`、`expectedOutputHint`。
- `checkpoint`：学生自查清单。
- `challenge`：闯关题，可包含 `prompt` 与 `answer`。
- `resource`：资料链接，字段包括 `url`、`resourceType`、`description`。
- `html`：独立 HTML 子页面，前端用沙盒 iframe 渲染。

## 数据落点

- 教师发布的课时仍写入 `t_lesson`。
- JSON 页面保存在 `t_lesson.content`。
- 视频、PPT、资料文件继续使用现有 `resource_type`、`resource_url`、`video_url`、`video_resource_id` 字段。
