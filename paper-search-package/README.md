# 论文检索功能 - 集成包

多数据源学术论文搜索模块，支持 **本地数据库全文检索**、**Semantic Scholar**、**arXiv**、**OpenAlex** 四种数据源。

---

## 1. 包内容结构

```
paper-search-package/
├── backend/
│   ├── entity/
│   │   ├── Paper.java                  # 论文实体（对应 t_paper 表）
│   │   └── PaperAuthor.java           # 作者实体（对应 t_paper_author 表）
│   ├── dto/
│   │   └── PaperSearchResponse.java   # 统一搜索结果 DTO
│   ├── mapper/
│   │   ├── PaperMapper.java           # MyBatis-Plus Mapper
│   │   └── PaperAuthorMapper.java     # MyBatis-Plus Mapper
│   ├── service/
│   │   ├── PaperSearchService.java    # Semantic Scholar 接口
│   │   ├── ArxivSearchService.java    # arXiv 接口
│   │   ├── OpenAlexSearchService.java # OpenAlex 接口
│   │   ├── PaperLocalService.java     # 本地数据库接口
│   │   └── impl/
│   │       ├── PaperSearchServiceImpl.java    # Semantic Scholar 实现
│   │       ├── ArxivSearchServiceImpl.java    # arXiv Atom XML 解析实现
│   │       ├── OpenAlexSearchServiceImpl.java # OpenAlex REST 实现
│   │       └── PaperLocalServiceImpl.java     # 本地数据库 + arXiv导入实现
│   ├── controller/
│   │   └── PaperSearchController.java # REST API 控制器
│   └── common/
│       └── Result.java                # 统一响应包装类
├── frontend/
│   ├── views/
│   │   └── PaperSearch.vue            # 论文搜索页面组件
│   ├── api/
│   │   └── paperSearch.js             # 前端 API 调用函数
│   └── router/
│       └── paperSearchRoute.js        # 路由配置片段
├── database/
│   └── paper_tables.sql               # MySQL 建表 DDL
├── config/
│   ├── application-paper.yml          # 配置属性
│   └── pom-dependencies.xml           # Maven 依赖补充
└── README.md                          # 本文件
```

---

## 2. 后端集成步骤

### 2.1 复制 Java 文件

将 `backend/` 下的所有 `.java` 文件按包路径复制到你的 Spring Boot 项目中：

| 文件 | 目标路径 |
|------|----------|
| `Paper.java` | `项目基础包/entity/Paper.java` |
| `PaperAuthor.java` | `项目基础包/entity/PaperAuthor.java` |
| `PaperSearchResponse.java` | `项目基础包/dto/PaperSearchResponse.java` |
| `PaperMapper.java` | `项目基础包/mapper/PaperMapper.java` |
| `PaperAuthorMapper.java` | `项目基础包/mapper/PaperAuthorMapper.java` |
| `PaperSearchService.java` | `项目基础包/service/PaperSearchService.java` |
| `ArxivSearchService.java` | `项目基础包/service/ArxivSearchService.java` |
| `OpenAlexSearchService.java` | `项目基础包/service/OpenAlexSearchService.java` |
| `PaperLocalService.java` | `项目基础包/service/PaperLocalService.java` |
| `*ServiceImpl.java` (4个) | `项目基础包/service/impl/` |
| `PaperSearchController.java` | `项目基础包/controller/PaperSearchController.java` |
| `Result.java` | `项目基础包/common/Result.java` |

> **注意**：如果项目中已有 `Result.java` 统一响应类，可以跳过，但需确保有 `Result.success()` 和 `Result.error()` 静态方法。

### 2.2 检查 Maven 依赖

检查 `pom.xml` 中是否已有以下依赖（Spring Boot 项目通常已有大部分）：

```xml
<!-- 参考 config/pom-dependencies.xml，核心依赖: -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.9</version>
</dependency>
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>
<dependency>
    <groupId>com.alibaba.fastjson2</groupId>
    <artifactId>fastjson2</artifactId>
    <version>2.0.53</version>
</dependency>
```

### 2.3 创建数据库表

执行 `database/paper_tables.sql` 中的 DDL，创建 `t_paper` 和 `t_paper_author` 两张表。

### 2.4 添加配置

将 `config/application-paper.yml` 中的配置合并到你的 `application.yml`：

```yaml
academic:
  semantic-scholar:
    api-key: "你的API Key（免费注册: https://api.semanticscholar.org/）"
  openalex:
    email: "你的机构邮箱（用于OpenAlex礼貌池）"
```

---

## 3. 前端集成步骤

### 3.1 复制 Vue 组件

将 `frontend/views/PaperSearch.vue` 放入 `src/views/` 目录。

### 3.2 添加路由

在 `src/router/index.js` 的 `routes` 数组中添加：

```javascript
{ path: '/paper-search', name: 'PaperSearch', component: () => import('../views/PaperSearch.vue'), meta: { title: '学术论文搜索' } }
```

### 3.3 导出 API 函数

在 `src/api/modules.js`（或新建 `src/api/paperSearch.js`）中添加：

```javascript
export const searchPapers = (params) => api.get('/paper/search', { params })
export const getPaperDetail = (paperId) => api.get(`/paper/detail/${paperId}`)
export const importPapers = (keyword, maxResults = 20) =>
  api.post('/paper/import', null, { params: { keyword, maxResults } })
```

### 3.4 添加导航入口

在导航组件（如 `App.vue`）中添加菜单项：

```html
<el-menu-item index="/paper-search">论文搜索</el-menu-item>
```

---

## 4. API 接口说明

### 4.1 搜索论文

```
GET /api/paper/search?keyword=xxx&source=local&page=1&size=10
```

| 参数 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| keyword | 是 | - | 搜索关键词 |
| source | 否 | local | 数据源：`local` / `arxiv` / `semantic_scholar` / `openalex` |
| page | 否 | 1 | 页码 |
| size | 否 | 10 | 每页数量 |

### 4.2 论文详情

```
GET /api/paper/detail/{paperId}
```

### 4.3 导入论文（从 arXiv 导入到本地库）

```
POST /api/paper/import?keyword=xxx&maxResults=20
```

---

## 5. 四种数据源对比

| 数据源 | 覆盖范围 | 优势 | 是否需要 API Key |
|--------|---------|------|-----------------|
| **本地数据库** | 已导入论文 | 全文检索、离线、快速 | 否 |
| **Semantic Scholar** | 2亿+ 论文 | 引用数据最丰富 | 免费注册后速率更高 |
| **arXiv** | 预印本（CS/数学/物理） | 无速率限制 | 否 |
| **OpenAlex** | 2.5亿+ 学术作品 | 覆盖最广 | 否（建议提供邮箱） |

---

## 6. 依赖清单

| 层级 | 技术 | 版本要求 |
|------|------|----------|
| 后端框架 | Spring Boot | 3.x |
| ORM | MyBatis-Plus | 3.5.x |
| 数据库 | MySQL | 8.0+ |
| HTTP 客户端 | OkHttp | 4.x |
| JSON 解析 | FastJSON2 | 2.x |
| 前端框架 | Vue | 3.x |
| UI 库 | Element Plus | 2.x |
| HTTP 客户端 | Axios | 1.x |
