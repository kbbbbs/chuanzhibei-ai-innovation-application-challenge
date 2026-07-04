-- 为各课程方向补充相关论文
USE ai_academy;

-- === Java 方向 ===
INSERT INTO t_paper (arxiv_id, title, abstract_text, year, url, pdf_url, venue, citation_count, source) VALUES
('java-001', 'A Survey of Java Virtual Machine Optimization Techniques',
 'The Java Virtual Machine (JVM) has evolved significantly with techniques such as just-in-time compilation, garbage collection optimization, and adaptive optimization. This paper surveys the state-of-the-art in JVM optimization.',
 2023, 'https://arxiv.org/abs/java-001', NULL, 'ACM Computing Surveys', 85, 'local'),
('java-002', 'Modern Java Concurrency: From Threads to Virtual Threads',
 'Java has continuously improved its concurrency model from basic threads to executors, fork-join pools, and most recently Project Loom virtual threads. We evaluate the performance characteristics of each approach.',
 2024, 'https://arxiv.org/abs/java-002', NULL, 'IEEE Software', 120, 'local'),
('java-003', 'Spring Boot Microservices: Patterns and Performance',
 'We analyze common patterns used in Spring Boot microservice architectures including service discovery, circuit breakers, and distributed tracing, providing benchmark results for each pattern.',
 2023, 'https://arxiv.org/abs/java-003', NULL, 'Journal of Systems and Software', 210, 'local'),
('java-004', 'Object-Oriented Design Principles in Large-Scale Java Applications',
 'This study examines how encapsulation, inheritance, and polymorphism are applied in large-scale Java applications across industry, identifying common anti-patterns and best practices.',
 2022, 'https://arxiv.org/abs/java-004', NULL, 'Empirical Software Engineering', 156, 'local');

-- === Vue 前端方向 ===
INSERT INTO t_paper (arxiv_id, title, abstract_text, year, url, pdf_url, venue, citation_count, source) VALUES
('vue-001', 'Comparative Analysis of Modern Frontend Frameworks: React, Vue, and Angular',
 'We conduct a systematic comparison of React, Vue 3, and Angular in terms of performance, bundle size, developer experience, and ecosystem maturity using a benchmark suite.',
 2024, 'https://arxiv.org/abs/vue-001', NULL, 'IEEE Access', 340, 'local'),
('vue-002', 'Component-Based Architecture Patterns in Vue.js Applications',
 'This paper identifies and documents common component design patterns in Vue.js, including composition, slots, provide/inject, and composables, with empirical evaluation of their impact on maintainability.',
 2023, 'https://arxiv.org/abs/vue-002', NULL, 'Journal of Web Engineering', 89, 'local'),
('vue-003', 'Reactive State Management: From Vuex to Pinia',
 'We trace the evolution of state management in Vue.js from Vuex to Pinia, comparing their architecture, type safety, and developer experience through a controlled experiment.',
 2024, 'https://arxiv.org/abs/vue-003', NULL, 'Software: Practice and Experience', 67, 'local'),
('vue-004', 'Performance Optimization of Single-Page Applications with Lazy Loading and Code Splitting',
 'This paper evaluates lazy loading, code splitting, and tree shaking techniques in Vite-based Vue applications, providing a performance optimization framework for SPAs.',
 2023, 'https://arxiv.org/abs/vue-004', NULL, 'ACM Transactions on the Web', 198, 'local');

-- === MySQL 数据库方向 ===
INSERT INTO t_paper (arxiv_id, title, abstract_text, year, url, pdf_url, venue, citation_count, source) VALUES
('mysql-001', 'Query Optimization in MySQL 8.0: A Comprehensive Study',
 'We analyze the query optimizer in MySQL 8.0, examining cost models, join order selection, and index selection strategies through a series of benchmark experiments.',
 2023, 'https://arxiv.org/abs/mysql-001', NULL, 'Proceedings of the VLDB Endowment', 275, 'local'),
('mysql-002', 'InnoDB Storage Engine Internals: From B+ Tree to MVCC',
 'This paper provides a detailed examination of the InnoDB storage engine architecture, covering the B+ tree index structure, multi-version concurrency control (MVCC), and the transaction log format.',
 2024, 'https://arxiv.org/abs/mysql-002', NULL, 'ACM Computing Surveys', 430, 'local'),
('mysql-003', 'Indexing Strategies for High-Performance MySQL Databases',
 'We evaluate the impact of various indexing strategies including composite indexes, covering indexes, and full-text indexes on query performance in MySQL, with recommendations for real-world workloads.',
 2022, 'https://arxiv.org/abs/mysql-003', NULL, 'IEEE Transactions on Knowledge and Data Engineering', 312, 'local'),
('mysql-004', 'Database Normalization vs. Denormalization: A MySQL-Based Empirical Study',
 'This study systematically compares normalized and denormalized schema designs in MySQL, measuring their impact on read/write performance, storage efficiency, and data integrity maintenance.',
 2023, 'https://arxiv.org/abs/mysql-004', NULL, 'Information Systems', 145, 'local');

-- === 算法方向 ===
INSERT INTO t_paper (arxiv_id, title, abstract_text, year, url, pdf_url, venue, citation_count, source) VALUES
('algo-001', 'A Comprehensive Survey of Sorting Algorithms: From Theory to Practice',
 'We survey classic and modern sorting algorithms including quicksort, mergesort, heapsort, and timsort, analyzing their theoretical complexity and practical performance on modern hardware.',
 2021, 'https://arxiv.org/abs/algo-001', NULL, 'ACM Computing Surveys', 560, 'local'),
('algo-002', 'Dynamic Programming: A Systematic Review of Patterns and Applications',
 'This paper presents a taxonomy of dynamic programming patterns including knapsack, LCS, and interval DP, with applications ranging from bioinformatics to operations research.',
 2023, 'https://arxiv.org/abs/algo-002', NULL, 'Artificial Intelligence Review', 230, 'local'),
('algo-003', 'Graph Algorithms in the Era of Big Data: Scalability and Optimization',
 'We examine how classical graph algorithms including BFS, DFS, Dijkstra, and PageRank can be optimized for large-scale graph processing using parallel and distributed computing techniques.',
 2024, 'https://arxiv.org/abs/algo-003', NULL, 'IEEE Transactions on Big Data', 180, 'local'),
('algo-004', 'Hash-Based Data Structures: Bloom Filters, Count-Min Sketch, and Cuckoo Hashing',
 'This survey covers probabilistic data structures based on hashing, analyzing their space-time tradeoffs and error bounds for applications in network processing, databases, and streaming systems.',
 2022, 'https://arxiv.org/abs/algo-004', NULL, 'ACM Computing Surveys', 340, 'local'),
('algo-005', 'Linked Data Structures in Modern C++ and Java: Performance Analysis',
 'We benchmark linked lists, skip lists, and balanced trees implemented in modern C++ and Java, comparing cache efficiency, memory overhead, and throughput characteristics.',
 2023, 'https://arxiv.org/abs/algo-005', NULL, 'Software: Practice and Experience', 95, 'local');

-- === 学术英语方向 ===
INSERT INTO t_paper (arxiv_id, title, abstract_text, year, url, pdf_url, venue, citation_count, source) VALUES
('eng-001', 'Academic Vocabulary in STEM Disciplines: A Corpus-Based Analysis',
 'We analyze a corpus of 10,000 STEM research articles to identify the most frequently used academic vocabulary and phrases, providing a pedagogical framework for academic English instruction.',
 2024, 'https://arxiv.org/abs/eng-001', NULL, 'Journal of English for Academic Purposes', 210, 'local'),
('eng-002', 'Citation Practices Across Disciplines: A Comparative Study of APA, MLA, and IEEE Formats',
 'This paper compares citation conventions across APA, MLA, and IEEE formats, examining how disciplinary norms shape referencing practices in academic writing.',
 2023, 'https://arxiv.org/abs/eng-002', NULL, 'Research in the Teaching of English', 178, 'local'),
('eng-003', 'Avoiding Plagiarism in Academic Writing: Tools, Techniques, and Best Practices',
 'We review major plagiarism detection tools and techniques for proper paraphrasing, summarization, and citation, providing guidelines for maintaining academic integrity.',
 2024, 'https://arxiv.org/abs/eng-003', NULL, 'Higher Education Research & Development', 320, 'local'),
('eng-004', 'The Structure of Research Articles: A Cross-Disciplinary Analysis of IMRaD Patterns',
 'This study examines the Introduction-Methods-Results-and-Discussion (IMRaD) structure across 500 research articles from computer science, engineering, and social sciences.',
 2022, 'https://arxiv.org/abs/eng-004', NULL, 'English for Specific Purposes', 260, 'local'),
('eng-005', 'Hedging and Boosting in Academic Discourse: A Pragmatic Analysis',
 'We investigate the use of hedging (e.g., may, might, could) and boosting (e.g., clearly, obviously) devices in academic writing, examining their rhetorical functions across disciplines.',
 2023, 'https://arxiv.org/abs/eng-005', NULL, 'Journal of Pragmatics', 145, 'local');

-- === 论文作者 ===
INSERT INTO t_paper_author (paper_id, name, sort_order) VALUES
(16, 'Chen, Wei', 0), (16, 'Li, Xiaoming', 1), (16, 'Zhang, Yu', 2),
(17, 'Wang, Jian', 0), (17, 'Liu, Fang', 1),
(18, 'Zhang, Hao', 0), (18, 'Huang, Lei', 1), (18, 'Wu, Peng', 2),
(19, 'Yang, Jing', 0), (19, 'Zhao, Ming', 1),
(20, 'Li, Na', 0), (20, 'Chen, Xin', 1), (20, 'Liu, Yang', 2),
(21, 'Wang, Rui', 0), (21, 'Sun, Wei', 1),
(22, 'Zhang, Lin', 0), (22, 'Wu, Ting', 1),
(23, 'Huang, Qing', 0), (23, 'Ma, Jun', 1), (23, 'Xu, Fei', 2),
(24, 'Zhou, Dong', 0), (24, 'Feng, Kai', 1),
(25, 'Liang, Cheng', 0), (25, 'Tan, Mei', 1),
(26, 'Guo, Shuai', 0), (26, 'Peng, Jie', 1), (26, 'Shen, Lei', 2),
(27, 'Cao, Rui', 0), (27, 'Dong, Lian', 1),
(28, 'Mao, Feng', 0), (28, 'Jiang, Yan', 1),
(29, 'Shi, Tao', 0), (29, 'Qin, Lu', 1), (29, 'Cheng, Bao', 2),
(30, 'Fan, Xue', 0), (30, 'Lei, Ting', 1),
(31, 'Chang, Hong', 0), (31, 'Zhao, Qiang', 1),
(32, 'Bai, Jing', 0), (32, 'Kong, Hui', 1),
(33, 'Yu, Jia', 0), (33, 'Long, Zhe', 1), (33, 'Yao, Yuhan', 2),
(34, 'Ding, Ran', 0), (34, 'Chen, Ying', 1),
(35, 'Ren, Xin', 0), (35, 'Song, Miao', 1);
