package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.entity.PaperUserMark;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.PaperUserMarkMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PaperMarkGenerationService {

    private final CourseMapper courseMapper;
    private final PaperUserMarkMapper paperUserMarkMapper;
    private final UserMapper userMapper;
    private final AuthUtils authUtils;

    public PaperMarkGenerationService(CourseMapper courseMapper,
                                       PaperUserMarkMapper paperUserMarkMapper,
                                       UserMapper userMapper,
                                       AuthUtils authUtils) {
        this.courseMapper = courseMapper;
        this.paperUserMarkMapper = paperUserMarkMapper;
        this.userMapper = userMapper;
        this.authUtils = authUtils;
    }

    private record PaperTemplate(String paperId, String title, String source, int year,
                                  String venue, int citationCount, String noteMd) {}

    private static final Map<String, List<PaperTemplate>> TOPIC_PAPERS = new LinkedHashMap<>();
    static {
        TOPIC_PAPERS.put("java", List.of(
            tp("java-001", "Java Virtual Machine: Advanced Memory Management and Garbage Collection Strategies", 2024, "ACM Computing Surveys", 156,
                "## 阅读笔记\n\n### 与课程关联\n深入探讨JVM内存模型，与课程中面向对象与内存管理章节直接相关。\n\n### 关键发现\n- G1GC在大型堆场景下比CMS性能提升约30%\n- ZGC将停顿时间控制在10ms以内\n\n### 待深入\n- [ ] Shenandoah GC实际应用场景\n- [ ] 与多线程章节的关联点"),
            tp("java-002", "Effective Concurrency Patterns in Modern Java Applications", 2023, "IEEE Software", 89,
                "## 阅读笔记\n\n### 课程关联\n对应课程中多线程与并发编程章节。\n\n### 核心要点\n- Virtual Threads (Project Loom) 简化并发编程模型\n- CompletableFuture链式调用优于传统Future模式\n\n### 实践建议\n课后线程池实验可改用Virtual Threads重构。"),
            tp("java-003", "A Systematic Review of Static Analysis Tools for Java", 2024, "Journal of Systems and Software", 42,
                "## 阅读笔记\n\n### 学习心得\n适合代码质量与测试章节的拓展阅读。\n\n| 工具 | 误报率 | 适用阶段 |\n|------|--------|----------|\n| SpotBugs | 15% | 开发期 |\n| Checkstyle | 5% | CI阶段 |\n| SonarQube | 12% | 全流程 |")
        ));
        TOPIC_PAPERS.put("python", List.of(
            tp("py-001", "Python Type Hints: Adoption Patterns and Performance Implications", 2024, "ACM SIGPLAN Notices", 67,
                "## 阅读笔记\n\n### 学习要点\n类型注解提升可读性，在PyPy下触发JIT优化，性能提升5-15%。\n\n### 扩展阅读\nPEP 484, PEP 526, PEP 544"),
            tp("py-002", "Asynchronous Programming in Python: A Comprehensive Empirical Study", 2023, "Empirical Software Engineering", 134,
                "## 阅读笔记\n\n### 核心发现\n- asyncio在I/O密集型任务中吞吐量高2-3倍\n- FastAPI底层基于Starlette + asyncio\n\n### 实践\n将同步爬虫改写为异步版本，速度提升明显。"),
            tp("py-003", "Large-Scale Data Processing with Python: Pandas, Spark, and Beyond", 2024, "IEEE Big Data", 210,
                "## 阅读笔记\n\n### 技术要点\n- Pandas适合GB级，Dask可扩展到TB级\n- PySpark DataFrame API与Pandas有90%相似度")
        ));
        TOPIC_PAPERS.put("机器学习", List.of(
            tp("ml-001", "Attention Is All You Need", 2017, "NeurIPS", 95000,
                "## 阅读笔记\n\n### 里程碑论文\nTransformer架构改变了NLP领域，是GPT、BERT等大模型的基础。\n\n### 核心机制\n- Self-Attention：Q、K、V三个矩阵的巧妙设计\n- Multi-Head Attention：关注不同子空间\n- Positional Encoding：弥补位置不敏感缺陷\n\n### 学习建议\n配合课程深度学习章节学习，理解RNN到Transformer的演进。"),
            tp("ml-002", "Deep Residual Learning for Image Recognition", 2015, "CVPR", 120000,
                "## 阅读笔记\n\n### 为什么重要\nResNet解决深度网络退化问题，让152层网络训练成为可能。\n\n### 关键洞察\n- 残差连接学习\"扰动\"而非完整映射\n- BatchNorm + ResBlock 成为CV标配"),
            tp("ml-003", "BERT: Pre-training of Deep Bidirectional Transformers", 2019, "NAACL", 85000,
                "## 阅读笔记\n\n### NLP核心参考文献\n- MLM预训练任务\n- Fine-tuning范式替代传统任务特定架构\n\n### 学习建议\n先用HuggingFace transformers跑通示例，再深入源码。"),
            tp("ml-004", "LoRA: Low-Rank Adaptation of Large Language Models", 2021, "ICLR", 15000,
                "## 阅读笔记\n\n### 为什么关注\nLoRA让普通研究者也能在单卡上微调大模型。\n\n### 核心思想\n冻结预训练权重，只训练低秩分解矩阵，参数量减少10,000倍。")
        ));
        TOPIC_PAPERS.put("深度学习", List.of(
            tp("dl-001", "Generative Adversarial Networks", 2014, "NeurIPS", 60000,
                "## 阅读笔记\n\n### 经典之作\nGAN开创生成模型新范式，对抗训练思想影响深远。\n\n### 数学理解\n- Generator: 从噪声z到数据分布G(z)\n- Discriminator: 区分真实与生成数据\n- min_G max_D V(D,G) 本质是JS散度最小化"),
            tp("dl-002", "Dropout: A Simple Way to Prevent Neural Networks from Overfitting", 2014, "JMLR", 45000,
                "## 阅读笔记\n\n### 经典正则化\n- 训练时随机丢弃神经元(p=0.5)\n- 推理时权重乘以(1-p)\n- 等价于训练指数级子网络的近似平均\n\n### 实验\nDropout vs L2正则化对比，Dropout测试集高出1.5%。")
        ));
        TOPIC_PAPERS.put("数据库", List.of(
            tp("db-001", "A Comparative Analysis of NoSQL Database Systems", 2023, "VLDB Journal", 178,
                "## 阅读笔记\n\n### 选型参考\n| 场景 | 推荐 | 原因 |\n|------|------|------|\n| 图遍历 | Neo4j | 性能最优 |\n| 实时分析 | Cassandra | 写入吞吐最高 |\n| 文档存储 | MongoDB | 灵活Schema |"),
            tp("db-002", "MySQL Query Optimization: From Index Selection to Join Order", 2024, "SIGMOD Record", 56,
                "## 阅读笔记\n\n### 优化要点\n1. EXPLAIN分析执行计划\n2. 联合索引最左前缀原则\n3. 覆盖索引避免回表\n\n### 实操\n对慢查询TOP 5优化后，平均响应时间从2.3s降至80ms。"),
            tp("db-003", "Transaction Isolation Levels: Theory and Practice in Modern RDBMS", 2022, "ACM TODS", 312,
                "## 阅读笔记\n\n### 隔离级别速查\n- READ UNCOMMITTED: 脏读\n- READ COMMITTED: 不可重复读 (PG默认)\n- REPEATABLE READ: 幻读 (MySQL默认)\n- SERIALIZABLE: 无并发异常\n\nMVCC机制是理解隔离级别的关键。")
        ));
        TOPIC_PAPERS.put("web", List.of(
            tp("web-001", "RESTful API Design: Best Practices and Common Pitfalls", 2023, "IEEE Internet Computing", 145,
                "## 阅读笔记\n\n### 设计原则\n- 资源导向URL：/users/{id}/orders\n- 正确使用HTTP方法语义\n- HATEOAS实现超媒体驱动"),
            tp("web-002", "Progressive Web Apps: A Systematic Mapping Study", 2024, "WWW Conference", 89,
                "## 阅读笔记\n\n### PWA核心技术\n- Service Worker：离线缓存\n- Web App Manifest：添加到主屏幕\n- IndexedDB：客户端存储\n\n### 实践\n从Vue CLI PWA插件开始。"),
            tp("web-003", "Micro-frontends: Architectural Patterns and Implementation Strategies", 2024, "IEEE Software", 34,
                "## 阅读笔记\n\n### 实现方案\n- Module Federation (Webpack 5)\n- qiankun (阿里)\n- Web Components (框架无关)")
        ));
        TOPIC_PAPERS.put("算法", List.of(
            tp("algo-001", "Neural Algorithmic Reasoning: Learning to Execute", 2023, "Nature Machine Intelligence", 267,
                "## 阅读笔记\n\n### 前沿交叉\nGNN与经典算法结合，让模型学会执行算法而非仅记忆模式。\n\n### 课程价值\n数据结构与算法课程的拓展方向。"),
            tp("algo-002", "A Survey on Graph Neural Networks", 2024, "IEEE TPAMI", 890,
                "## 阅读笔记\n\n### GNN分类\n1. 谱域：GCN (图傅里叶变换)\n2. 空域：GAT (注意力聚合邻居)\n3. 消息传递：MPNN统一视角")
        ));
        TOPIC_PAPERS.put("人工智能", List.of(
            tp("ai-edu-001", "Artificial Intelligence in Education: Promises and Implications", 2024, "Computers & Education", 450,
                "## 阅读笔记\n\n### 核心观点\nAI应增强教师的个性化教学能力，而非替代教师。\n\n### 关键应用\n- 智能辅导系统 (ITS)\n- 自动作文评分 (AES)\n- 自适应学习路径规划"),
            tp("ai-edu-002", "Knowledge Tracing: A Survey of Deep Learning Approaches", 2023, "IEEE TLT", 320,
                "## 阅读笔记\n\n### 知识追踪\n根据学生答题序列预测知识点掌握程度。\n\n### 主流模型\n- DKT: LSTM建模\n- DKVMN: 记忆增强网络\n- AKT: 注意力+单调性"),
            tp("ai-edu-003", "Large Language Models for Automated Question Generation", 2024, "arXiv", 178,
                "## 阅读笔记\n\n### 研究问题\nLLM能否替代人工出题？\n- GPT-4选择题接近人类水平\n- 开放问答题仍有事实性错误风险\n- 结合RAG可提升质量")
        ));
        TOPIC_PAPERS.put("知识图谱", List.of(
            tp("kg-001", "Knowledge Graphs: A Comprehensive Survey", 2023, "ACM Computing Surveys", 560,
                "## 阅读笔记\n\n### 知识图谱构建流程\n1. 知识抽取 (实体+关系)\n2. 知识融合 (实体对齐)\n3. 知识推理 (规则/嵌入)\n\n### 课程关联\n平台知识图谱模块的核心参考。"),
            tp("kg-002", "Knowledge Graph Embedding: A Survey of Approaches and Applications", 2022, "IEEE TKDE", 1200,
                "## 阅读笔记\n\n### 主流方法\n- TransE: 简单高效，h+r≈t\n- RotatE: 复数空间建模对称/反对称\n- ConvE: 卷积捕获局部特征")
        ));
    }

    private static PaperTemplate tp(String paperId, String title, int year, String venue, int citations, String noteMd) {
        return new PaperTemplate(paperId, title, "arxiv", year, venue, citations, noteMd);
    }

    @Transactional
    public Map<String, Object> generateForUser(Integer userId, String roleCode) {
        List<Course> courses;
        if ("student".equals(roleCode)) {
            courses = courseMapper.selectCoursesByStudentId(userId);
        } else if ("teacher".equals(roleCode)) {
            courses = courseMapper.selectCoursesByTeacherId(userId);
        } else {
            courses = new ArrayList<>();
        }

        if (courses.isEmpty()) {
            return Map.of("generated", 0, "message", "该账号暂无注册课程，请先加入课程后再生成。");
        }

        Set<String> existingPaperIds = getExistingPaperIds(userId);
        int totalGenerated = 0;
        Set<String> matchedTopics = new LinkedHashSet<>();
        LocalDateTime now = LocalDateTime.now();

        for (Course course : courses) {
            String title = (course.getTitle() != null ? course.getTitle() : "").toLowerCase();
            String desc = (course.getDescription() != null ? course.getDescription() : "").toLowerCase();
            String combined = title + " " + desc;

            boolean courseHasMatches = false;
            for (Map.Entry<String, List<PaperTemplate>> entry : TOPIC_PAPERS.entrySet()) {
                if (combined.contains(entry.getKey().toLowerCase())) {
                    matchedTopics.add(entry.getKey());
                    courseHasMatches = true;
                    for (PaperTemplate pt : entry.getValue()) {
                        if (existingPaperIds.contains(pt.paperId)) continue;
                        insertMark(userId, pt, now);
                        existingPaperIds.add(pt.paperId);
                        totalGenerated++;
                    }
                }
            }

            if (!courseHasMatches) {
                for (Map.Entry<String, List<PaperTemplate>> entry : TOPIC_PAPERS.entrySet()) {
                    if (matchedTopics.contains(entry.getKey())) continue;
                    PaperTemplate pt = entry.getValue().get(0);
                    if (existingPaperIds.contains(pt.paperId)) continue;
                    insertMark(userId, pt, now);
                    existingPaperIds.add(pt.paperId);
                    totalGenerated++;
                    if (totalGenerated >= 5) break;
                }
            }
        }

        return Map.of(
            "generated", totalGenerated,
            "courses", courses.stream().map(Course::getTitle).toList(),
            "matchedTopics", new ArrayList<>(matchedTopics),
            "message", "成功为 " + courses.size() + " 门课程生成了 " + totalGenerated + " 条论文资料"
        );
    }

    private void insertMark(Integer userId, PaperTemplate pt, LocalDateTime now) {
        PaperUserMark mark = new PaperUserMark();
        mark.setUserId(userId);
        mark.setPaperId(pt.paperId);
        mark.setTitle(pt.title);
        mark.setSource(pt.source);
        mark.setFavorite(Math.random() > 0.4);
        mark.setReadStatus(Math.random() > 0.5 ? "READ" : "UNREAD");
        mark.setNoteMd(pt.noteMd);
        mark.setCreatedAt(now);
        mark.setUpdatedAt(now);
        paperUserMarkMapper.insert(mark);
    }

    private Set<String> getExistingPaperIds(Integer userId) {
        Set<String> ids = new HashSet<>();
        List<PaperUserMark> marks = paperUserMarkMapper.selectList(
            new QueryWrapper<PaperUserMark>().eq("user_id", userId));
        for (PaperUserMark m : marks) {
            if (m.getPaperId() != null) ids.add(m.getPaperId());
        }
        return ids;
    }

    @Transactional
    public Map<String, Object> batchGenerateAllUsers() {
        List<User> users = userMapper.selectList(new QueryWrapper<User>().gt("id", 0));
        int totalUsers = 0;
        int totalGenerated = 0;
        int skippedUsers = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (User user : users) {
            Integer userId = user.getId();
            String roleCode = authUtils.findRoleCode(userId);
            totalUsers++;

            Map<String, Object> result = generateForUser(userId, roleCode);
            int generated = (int) result.getOrDefault("generated", 0);

            if (generated == 0) {
                skippedUsers++;
                continue;
            }

            totalGenerated += generated;
            details.add(Map.of(
                "userId", userId,
                "nickname", user.getNickname() != null ? user.getNickname() : user.getUsername(),
                "role", roleCode,
                "generated", generated,
                "courses", result.getOrDefault("courses", List.of()),
                "topics", result.getOrDefault("matchedTopics", List.of())
            ));
        }

        return Map.of(
            "totalUsers", totalUsers,
            "generatedUsers", details.size(),
            "skippedUsers", skippedUsers,
            "totalPapers", totalGenerated,
            "details", details,
            "message", "共处理 " + totalUsers + " 个用户，为 " + details.size() + " 个用户生成了 " + totalGenerated + " 条论文资料"
        );
    }
}
