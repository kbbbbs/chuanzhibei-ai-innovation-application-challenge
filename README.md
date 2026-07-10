# AI智学平台

这是一个课程学习平台项目，前端使用 Vue 3 和 Vite，后端使用 Spring Boot。

## 项目结构

```text
ai-learning-backend/    后端服务
ai-learning-frontend/   前端页面
database/               数据库脚本
backend-api.md          后端接口简表
```

## 数据库

仅执行 `database/ai_academy_export.sql` 重建 MySQL 数据库。后端默认连接本机 `ai_academy` 数据库，也可以通过环境变量调整：

```bash
DB_URL=jdbc:mysql://localhost:3306/ai_academy?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=root
DB_PASSWORD=[REDACTED]
JWT_SECRET=[REDACTED]
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
