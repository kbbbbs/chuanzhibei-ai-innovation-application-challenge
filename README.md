# AI智学平台

这是一个课程学习平台项目，前端使用 Vue 3 和 Vite，后端使用 Spring Boot。当前提交为最基础的脚手架

## 项目结构

```text
ai-learning-backend/    后端服务
ai-learning-frontend/   前端页面
```

## 本地运行

后端：

```bash
cd ai-learning-backend
mvn spring-boot:run
```

前端：

```bash
cd ai-learning-frontend
npm install
npm run dev
```

## 编译检查

```bash
cd ai-learning-backend
mvn -DskipTests package
```

```bash
cd ai-learning-frontend
npm run build
```
