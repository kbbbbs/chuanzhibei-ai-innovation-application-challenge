# WeKnora 集成包

将 WeKnora（腾讯开源的 LLM 知识库 RAG 框架）集成到任意 Java SpringBoot + Vue3 项目的开箱即用模块。

## 前置条件

1. **WeKnora 已部署并运行** —— 参考 [WeKnora 官方文档](https://github.com/Tencent/WeKnora)
2. **已获取 API Key** —— WeKnora 前端界面 → 设置 → API Key（格式 `sk-xxx`）
3. **避免端口冲突** —— 本项目后端默认 8080，WeKnora 也是 8080，建议将 WeKnora 改为 8081：

```bash
# 在 WeKnora 目录的 .env 中添加
APP_PORT=8081
```

## 项目结构

```
weknora-integration/
├── backend/                      # Java SpringBoot 模块
│   └── src/main/java/com/weknora/
│       ├── common/R.java                  # 统一响应封装
│       ├── config/WeKnoraConfig.java      # 配置类
│       ├── controller/WeKnoraController.java
│       ├── dto/
│       │   ├── WeKnoraChatRequest.java
│       │   ├── WeKnoraSearchRequest.java
│       │   └── WeKnoraSessionRequest.java
│       └── service/
│           ├── WeKnoraService.java
│           └── impl/WeKnoraServiceImpl.java
├── frontend/                     # Vue3 模块
│   └── src/
│       ├── api/weknora.js               # API 模块（axios + SSE）
│       └── composables/useWeKnora.js    # Vue3 Composable
└── README.md
```

---

## 一、后端集成

### 1. 复制文件

将 `backend/src/main/java/com/weknora/` 整个目录复制到你的 SpringBoot 项目的 `src/main/java/` 下。

### 2. 添加依赖（如果项目中还没有）

`pom.xml` 需要以下依赖：

```xml
<!-- OkHttp（WeKnora 通信） -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>

<!-- fastjson2（JSON 解析） -->
<dependency>
    <groupId>com.alibaba.fastjson2</groupId>
    <artifactId>fastjson2</artifactId>
    <version>2.0.53</version>
</dependency>

<!-- Lombok（可选，去除则需手动写 getter/setter） -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

Spring Boot 3.x 已自带 `spring-boot-starter-web`，无需额外添加。

### 3. 配置 application.yml

```yaml
weknora:
  base-url: http://localhost:8081    # WeKnora 服务地址
  api-key: <YOUR_WEKNORA_API_KEY>   # 在 WeKnora 前端获取
```

如果 WeKnora 和你项目都在同一台机器，直接用 `localhost`。

### 4. 处理 Spring 组件扫描

确保你的主启动类能扫描到 `com.weknora` 包。如果你的启动类在 `com.xxx` 下，可以加上：

```java
@SpringBootApplication(scanBasePackages = {"com.xxx", "com.weknora"})
public class YourApplication {
    // ...
}
```

或者把 `com.weknora` 下的文件移动到你自己项目的包路径下（推荐）。

### 5. 自定义响应格式

`R.java` 是自带的响应封装类，格式为 `{ code: 200, message: "success", data: ... }`。  
如果你的项目已有类似的 `Result` 类，批量替换即可：  
把所有 Java 文件中的 `com.weknora.common.R` 替换为你的 Result 类，`R.success()` → `Result.success()`。

### 6. JWT 认证兼容

如果项目有 JWT 拦截器过滤 `/api/**`，确保 `/api/weknora/**` 不在排除列表中（即需要登录才能调用 WeKnora 接口）。

---

## 二、前端集成

### 1. 复制文件

将 `frontend/src/api/weknora.js` 和 `frontend/src/composables/useWeKnora.js` 复制到你 Vue3 项目的对应位置。

### 2. 安装依赖

```bash
npm install axios   # 如果还没装
```

### 3. 配置 Vite 代理

在 `vite.config.js` 中把 `/api` 代理到 Spring Boot 后端：

```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

### 4. 调整 API 基础路径

如果你的后端在别的地址，打开 `weknora.js`，修改第一行：

```js
const API_BASE = 'http://localhost:8080/api'   // 改为你的后端地址
```

---

## 三、使用示例

### 后端调用（在你自己项目的 Service 中注入 WeKnoraService）

```java
@Autowired
private WeKnoraService weKnoraService;

// 获取知识库列表
R<?> kbs = weKnoraService.listKnowledgeBases();

// 知识检索
WeKnoraSearchRequest req = new WeKnoraSearchRequest();
req.setKnowledgeBaseIds(List.of("kb-xxx"));
req.setQuery("什么是机器学习");
R<?> result = weKnoraService.search(req);

// 创建会话
WeKnoraSessionRequest sessionReq = new WeKnoraSessionRequest();
sessionReq.setKnowledgeBaseId("kb-xxx");
sessionReq.setTitle("学习对话");
R<?> session = weKnoraService.createSession(sessionReq);

// SSE 流式问答（在 Controller 中返回给前端）
SseEmitter emitter = weKnoraService.knowledgeChat("session-id", "用户问题");
return emitter;
```

### 前端 — 方式 1：直接用 API 函数

```vue
<script setup>
import { listKnowledgeBases, weKnoraStreamChat, createSession } from '@/api/weknora.js'
import { ref } from 'vue'

const kbList = ref([])
const fullReply = ref('')
const streaming = ref(false)

// 加载知识库
async function load() {
  const res = await listKnowledgeBases()
  kbList.value = res.data?.data || res.data || []
}

// 流式问答
function ask(sessionId, query) {
  streaming.value = true
  fullReply.value = ''

  weKnoraStreamChat('knowledge', sessionId, query, {
    onMessage(data) {
      fullReply.value += data.content || data.answer || ''
    },
    onDone() {
      streaming.value = false
    },
    onError(err) {
      streaming.value = false
      console.error(err)
    }
  })
}
</script>
```

### 前端 — 方式 2：用 Composable

```vue
<script setup>
import { useWeKnora } from '@/composables/useWeKnora.js'
import { onMounted } from 'vue'

const {
  messages, loading, knowledgeBases, currentSessionId,
  loadKnowledgeBases, createNewSession, sendMessage
} = useWeKnora()

onMounted(async () => {
  await loadKnowledgeBases()
  await createNewSession(knowledgeBases.value[0]?.id, '新对话')
})

async function handleSend(text) {
  const reply = await sendMessage('knowledge', text)
  console.log('回答完成:', reply)
}
</script>

<template>
  <div v-for="msg in messages" :key="msg.id">
    <b>{{ msg.role === 'user' ? '我' : 'AI' }}:</b>
    <span>{{ msg.content }}</span>
    <em v-if="msg.streaming">...</em>
  </div>
</template>
```

---

## 四、调用链路

```
Vue3 前端                    Spring Boot 后端                WeKnora
─────────                    ────────────────                ───────
POST /api/weknora/search ──→ WeKnoraController ──→ POST /api/v1/knowledge-search (X-API-Key)
        ←── JSON 响应 ←──────────   解析转发    ←──────── JSON 响应

POST /api/weknora/knowledge-chat/:id?query=xxx
        ──→ SseEmitter ──→ POST /api/v1/knowledge-chat/:id (SSE)
        ←── SSE 流 ←──────    逐行转发     ←──────── SSE 流
```

- 前端 → 后端：JWT Bearer Token（项目的用户认证）
- 后端 → WeKnora：X-API-Key（WeKnora 的 API 密钥）
- 前端不直接调用 WeKnora，更安全

---

## 五、依赖清单

| 层 | 依赖 | 说明 |
|---|---|---|
| 后端 | `okhttp 4.12+` | HTTP 客户端 |
| 后端 | `fastjson2 2.0+` | JSON 解析 |
| 后端 | `lombok` | 可选，减少样板代码 |
| 后端 | `spring-boot-starter-web` | Spring Boot 3.x 自带 |
| 前端 | `axios ^1.x` | HTTP 请求 |
| 前端 | `vue ^3.x` | 框架 |

---

## 六、常见问题

**Q: 响应格式对不上我的项目？**  
A: 把 `com.weknora.common.R` 替换为你的 Result 类，`R.success(x)` → `Result.success(x)`。

**Q: WeKnora 服务连不上？**  
A: 检查 `weknora.base-url` 和 `weknora.api-key` 配置是否正确，确保 WeKnora 已启动。

**Q: SSE 流式没反应？**  
A: 检查是否有 Nginx 代理缓冲 SSE，需要在 Nginx 配置中加 `proxy_buffering off;`。

**Q: CORS 报错？**  
A: 不要直接从浏览器调 WeKnora，走 Spring Boot 后端代理，你的后端 CORS 配置正常就行。
