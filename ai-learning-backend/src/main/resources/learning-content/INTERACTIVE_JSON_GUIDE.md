# 互动课本 JSON 编写指南

本文档说明 Amazing Teaching 中 `interactive_json_builtin` 和 `interactive_json_upload` 课时内容的 JSON 写法。适用于教师编写互动课本页面，也适用于开发者新增内置模板。

## 放置位置

内置模板放在：

- `templates/index.json`：模板索引，教师端下拉列表从这里读取。
- `templates/<subject>/<name>.json`：具体互动课本页面。

教师上传或保存后的 JSON 会写入 `t_lesson.content`。课时的 `resource_type` 应为：

- `interactive_json_builtin`：从内置模板选择。
- `interactive_json_upload`：上传自定义 JSON。

## 最小要求

JSON 必须能被解析，并且包含非空 `blocks` 数组。

```json
{
  "schemaVersion": 1,
  "title": "示例互动课",
  "summary": "一句话说明本节学习目标。",
  "blocks": [
    {
      "type": "markdown",
      "title": "核心讲解",
      "content": "## 本节内容\n\n这里写 Markdown 正文。"
    }
  ]
}
```

如果 `blocks` 为空，教师端会拒绝保存。未知的 `type` 不会导致保存失败，但学生端只能按兜底内容显示。

## 页面级字段

| 字段 | 类型 | 必填 | 含义 |
| --- | --- | --- | --- |
| `schemaVersion` | number | 建议 | 结构版本，目前写 `1`。 |
| `key` | string | 内置模板建议 | 模板唯一标识，例如 `ml.gradient-descent`。应和 `templates/index.json` 中的 `key` 一致。 |
| `title` | string | 建议 | 页面标题。缺失时会使用课时标题。 |
| `subject` | string | 可选 | 学科或课程方向，例如 `机器学习`、`数据库`。 |
| `summary` | string | 建议 | 页面摘要，显示在学生端互动课本顶部。缺失时系统会尝试从 Markdown 正文生成。 |
| `estimatedMinutes` | number | 可选 | 预计学习时长。缺失时前端按内容长度估算。 |
| `blocks` | array | 必填 | 内容块数组。 |

## 模板索引字段

新增内置模板时，需要在 `templates/index.json` 加一项：

```json
{
  "key": "database.join-builder",
  "title": "SQL JOIN：把两张表拼成答案",
  "subject": "数据库",
  "summary": "用学生表和提交表观察 JOIN 结果行如何生成。",
  "path": "database/join-builder.json",
  "tags": ["数据库", "SQL", "JOIN"],
  "estimatedMinutes": 24
}
```

| 字段 | 含义 |
| --- | --- |
| `key` | 模板唯一标识，教师端根据它加载模板详情。 |
| `title` | 教师端下拉列表显示的标题。 |
| `subject` | 学科分类。 |
| `summary` | 模板简介。 |
| `path` | 相对 `templates/` 的 JSON 文件路径。 |
| `tags` | 标签数组，用于教师识别模板用途。 |
| `estimatedMinutes` | 默认预计学习时长。 |

## block 通用字段

每个 `blocks` 元素都是一个内容块。

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `id` | string | 可选。内容块稳定标识；缺失时前端会按位置生成。 |
| `type` | string | 内容块类型。缺失时按 `markdown` 处理。 |
| `title` | string | 内容块标题，会出现在页面导航和块标题中。 |
| `description` | string | 部分类型使用的说明文案。 |

## markdown 正文块

用于讲解、示例、表格、代码片段。

```json
{
  "type": "markdown",
  "title": "学习目标",
  "content": "## 这节课要解决什么问题\n\n- 理解接口测试的目的。\n- 能用 Postman 发送请求。"
}
```

| 字段 | 含义 |
| --- | --- |
| `content` | Markdown 正文。支持标题、列表、表格、代码块等常见 Markdown。 |

## experiment 互动实验块

用于显示内置可视化实验。

```json
{
  "type": "experiment",
  "title": "拖动学习率，观察下降轨迹",
  "description": "学习率越大，每次移动越远。",
  "kind": "gradient",
  "defaults": {
    "learningRate": 0.12,
    "startX": -4
  }
}
```

| 字段 | 含义 |
| --- | --- |
| `kind` | 实验类型。当前支持 `gradient`、`classification`、`state`、`counter`、`pipeline`、`join`、`structure`、`maze`、`cards`。 |
| `defaults` | 实验默认配置。 |
| `config` | `defaults` 的别名；如果没有 `defaults`，后端会读取 `config`。 |

当前各 `kind` 的实际行为：

| kind | 作用 | 主要配置 |
| --- | --- | --- |
| `gradient` | 学习率滑杆与梯度下降曲线。 | 当前前端固定使用滑杆状态；`defaults.learningRate`、`defaults.startX` 主要作为语义配置。 |
| `classification` | 二分类边界滑杆。 | 当前点位固定。 |
| `state` / `counter` | 计数器状态实验。 | 当前初始值固定。 |
| `pipeline` | 输入数字列表，展示 map、filter、average 流水线。 | 当前默认输入固定，可在界面修改。 |
| `join` | 展示两张表 JOIN 成结果。 | 当前表数据固定。 |
| `structure` | 栈/队列操作实验。 | 当前初始节点固定。 |
| `maze` | BFS/DFS 路径访问顺序实验。 | 当前迷宫固定。 |
| `cards` | 卡片式展示。 | 读取 `defaults.pairs`、`defaults.values` 或 `defaults.items`。 |

`cards` 示例：

```json
{
  "type": "experiment",
  "title": "接口测试三要素",
  "description": "把一次接口测试拆成几个关键部分。",
  "kind": "cards",
  "defaults": {
    "items": ["请求方法", "请求地址", "请求参数", "断言结果"]
  }
}
```

## ide 在线代码块

用于显示在线 IDE。学生点击运行后，前端会调用后端 `/code-lab/run`。

```json
{
  "type": "ide",
  "title": "JavaScript：模拟接口响应断言",
  "description": "修改 statusCode，观察测试是否通过。",
  "language": "javascript",
  "starterCode": "const response = { statusCode: 200, body: { ok: true } };\nconsole.log(response.statusCode === 200 ? 'PASS' : 'FAIL');",
  "expectedOutputHint": "应该输出 PASS。"
}
```

| 字段 | 含义 |
| --- | --- |
| `language` | 运行语言。当前支持 `java`、`python`、`javascript`、`js`。 |
| `starterCode` | 默认代码。 |
| `expectedOutputHint` | 输出提示。 |
| `hint` | `expectedOutputHint` 的备用字段。 |

运行限制：

- 代码长度最多 12000 字符。
- 后端执行超时为 6 秒。
- Java 代码必须提供 `public class Main`，后端固定编译 `Main.java` 并运行 `Main`。
- 后端依赖本机 `python`、`node`、`javac/java` 命令。公开部署时应改为隔离容器沙盒。

## checkpoint 完成检查块

用于学生自查。

```json
{
  "type": "checkpoint",
  "title": "完成检查",
  "items": [
    "我能说明 GET 和 POST 的区别。",
    "我能在 Postman 中配置请求头。",
    "我能根据响应码判断接口是否成功。"
  ]
}
```

| 字段 | 含义 |
| --- | --- |
| `items` | 复选项数组。学生端会显示勾选进度。 |

## challenge 闯关题块

用于简单问答或思考题。

```json
{
  "type": "challenge",
  "title": "判断题：为什么断言不能只看 200？",
  "prompt": "接口返回 200 是否一定代表业务成功？请说明原因。",
  "answer": "不一定。HTTP 200 只表示请求被服务器正常处理，业务是否成功还要看响应体中的业务码、字段和数据内容。"
}
```

| 字段 | 含义 |
| --- | --- |
| `prompt` | 题干。 |
| `answer` | 参考答案，学生点击后显示。 |

## resource 资料块

用于外链、文件、PPT、参考资料。

```json
{
  "type": "resource",
  "title": "Postman 官方文档",
  "description": "阅读请求、集合和断言相关章节。",
  "url": "https://learning.postman.com/docs/",
  "resourceType": "doc"
}
```

| 字段 | 含义 |
| --- | --- |
| `url` | 资料地址。 |
| `resourceType` | 资料类型说明，例如 `doc`、`pdf`、`ppt`、`link`。 |
| `description` | 资料说明。 |

## html 互动页面块

用于嵌入一段独立 HTML。前端会用沙盒 iframe 渲染。

```json
{
  "type": "html",
  "title": "状态码小实验",
  "html": "<button onclick=\"document.body.insertAdjacentHTML('beforeend','<p>HTTP 200</p>')\">发送请求</button>"
}
```

| 字段 | 含义 |
| --- | --- |
| `html` | iframe `srcdoc` 内容。 |
| `content` | `html` 的备用字段。 |

注意：iframe 当前允许脚本和表单，适合教学演示；不要放不可信 HTML。

## 完整示例

```json
{
  "schemaVersion": 1,
  "key": "testing.postman-homework-review",
  "title": "Postman 接口测试：作业讲评",
  "subject": "接口测试",
  "summary": "通过一次作业讲评复盘请求配置、响应断言和常见错误。",
  "estimatedMinutes": 20,
  "blocks": [
    {
      "type": "markdown",
      "title": "讲评目标",
      "content": "## 本节目标\n\n本节复盘接口测试作业中的三个问题：请求参数、鉴权请求头、响应断言。"
    },
    {
      "type": "experiment",
      "title": "接口测试检查卡",
      "description": "运行接口前先检查这些项目。",
      "kind": "cards",
      "defaults": {
        "items": ["Method", "URL", "Headers", "Body", "Status Code", "Response Body"]
      }
    },
    {
      "type": "ide",
      "title": "JavaScript：响应断言",
      "description": "模拟 Postman Tests 脚本，判断业务码是否成功。",
      "language": "javascript",
      "starterCode": "const response = { status: 200, body: { code: '200', data: { id: 1 } } };\n\nconsole.log(response.status === 200 ? 'HTTP PASS' : 'HTTP FAIL');\nconsole.log(response.body.code === '200' ? 'BUSINESS PASS' : 'BUSINESS FAIL');",
      "expectedOutputHint": "应该看到 HTTP PASS 和 BUSINESS PASS。"
    },
    {
      "type": "challenge",
      "title": "思考题",
      "prompt": "为什么接口测试既要检查 HTTP 状态码，也要检查响应体业务码？",
      "answer": "因为 HTTP 状态码只说明传输层请求是否成功，业务码才说明应用逻辑是否成功。"
    },
    {
      "type": "checkpoint",
      "title": "完成检查",
      "items": [
        "我能说出请求方法、URL、Headers、Body 的作用。",
        "我能写出至少一个响应断言。",
        "我能区分 HTTP 状态码和业务码。"
      ]
    }
  ]
}
```

## 编写建议

- 一个课时建议 4 到 7 个 block：正文、实验、代码、挑战、检查即可。
- `title` 尽量短，学生端左侧导航会显示每个 block 的标题。
- `summary` 控制在 120 字以内。
- 代码实验要能在 6 秒内运行完，不要写网络请求、长循环或依赖外部包。
- 如果只是展示概念卡片，优先使用 `experiment.kind = "cards"`，兼容性最好。
- 公开部署前，不要把不可信用户代码直接交给当前本机执行器运行，应接入容器沙盒。
