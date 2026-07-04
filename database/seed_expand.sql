-- ============================================
-- 全量数据扩充脚本
-- 基于现有种子数据，追加更多用户、题目、论文、考试记录等
-- ============================================

USE ai_academy;

-- ============================================
-- 1. 更多学生用户 (新增12人，ID 7-18)
-- ============================================
INSERT INTO t_user (username, password, nickname, email, phone, status) VALUES
('student7',  'e10adc3949ba59abbe56e057f20f883e', '孙同学', 'sun@example.com',   '13800000007', 1),
('student8',  'e10adc3949ba59abbe56e057f20f883e', '周同学', 'zhou@example.com',  '13800000008', 1),
('student9',  'e10adc3949ba59abbe56e057f20f883e', '吴同学', 'wu@example.com',    '13800000009', 1),
('student10', 'e10adc3949ba59abbe56e057f20f883e', '郑同学', 'zheng@example.com', '13800000010', 1),
('student11', 'e10adc3949ba59abbe56e057f20f883e', '钱同学', 'qian@example.com',  '13800000011', 1),
('student12', 'e10adc3949ba59abbe56e057f20f883e', '赵同学', 'zhao@example.com',  '13800000014', 1),
('student13', 'e10adc3949ba59abbe56e057f20f883e', '冯同学', 'feng@example.com',  '13800000015', 1),
('student14', 'e10adc3949ba59abbe56e057f20f883e', '蒋同学', 'jiang@example.com', '13800000016', 1),
('student15', 'e10adc3949ba59abbe56e057f20f883e', '韩同学', 'han@example.com',   '13800000017', 1),
('student16', 'e10adc3949ba59abbe56e057f20f883e', '杨同学', 'yang@example.com',  '13800000018', 1),
('student17', 'e10adc3949ba59abbe56e057f20f883e', '朱同学', 'zhu@example.com',   '13800000019', 1),
('student18', 'e10adc3949ba59abbe56e057f20f883e', '马同学', 'ma@example.com',    '13800000020', 1);

INSERT INTO t_user_role (user_id, role_id) VALUES
(7, 1), (8, 1), (9, 1), (10, 1), (11, 1), (12, 1),
(13, 1), (14, 1), (15, 1), (16, 1), (17, 1), (18, 1);

-- ============================================
-- 2. 更多教学班 (新增3个)
-- ============================================
INSERT INTO t_teaching_class (course_id, teacher_id, class_name, semester, description, status) VALUES
(3, 2, '软工2班 MySQL',   '2026春季', '面向软工2班的数据库教学班。',   1),
(5, 3, '英语1班 学术写作', '2026春季', '学术英语写作教学班。',         1),
(4, 3, '计科1班 算法',    '2026春季', '计科专业数据结构与算法教学班。', 1);

-- ============================================
-- 3. 新学生分配到教学班
-- ============================================
INSERT INTO t_class_student (class_id, student_id, status) VALUES
(1, 7, 1), (1, 8, 1), (1, 9, 1),
(2, 7, 1), (2, 8, 1), (2, 10, 1), (2, 11, 1),
(3, 9, 1), (3, 10, 1), (3, 11, 1), (3, 12, 1),
(4, 10, 1), (4, 11, 1), (4, 12, 1),
(5, 12, 1), (5, 13, 1), (5, 14, 1), (5, 15, 1),
(6, 13, 1), (6, 14, 1), (6, 15, 1), (6, 16, 1), (6, 17, 1),
(7, 16, 1), (7, 17, 1), (7, 18, 1);

-- ============================================
-- 4. 更多学习进度记录（新学生的学习进度）
-- ============================================
INSERT INTO t_learning_progress (class_id, student_id, lesson_id, progress, learned_seconds, last_position, last_learn_time) VALUES
(1, 7, 1, 100, 1200, 1200, NOW()),
(1, 7, 2, 80,  1200, 1200, NOW()),
(1, 7, 3, 40,  520,  520,  NOW()),
(1, 8, 1, 100, 1200, 1200, NOW()),
(1, 8, 2, 100, 1500, 1500, NOW()),
(1, 9, 1, 65,  780,  780,  NOW()),
(2, 7, 6, 90,  1260, 1260, NOW()),
(2, 7, 7, 60,  960,  960,  NOW()),
(2, 8, 6, 100, 1400, 1400, NOW()),
(2, 10, 6, 40,  560,  560,  NOW()),
(3, 9, 8, 100, 1600, 1600, NOW()),
(3, 9, 9, 85,  1275, 1275, NOW()),
(3, 10, 8, 70,  1120, 1120, NOW()),
(4, 10, 10, 50,  650,  650,  NOW()),
(4, 11, 10, 100, 1300, 1300, NOW()),
(4, 12, 10, 30,  390,  390,  NOW()),
(5, 13, 11, 100, 1100, 1100, NOW()),
(5, 14, 11, 75,  825,  825,  NOW()),
(5, 15, 11, 50,  550,  550,  NOW());

-- ============================================
-- 5. 更多作业 (新增5个)
-- ============================================
INSERT INTO t_homework (class_id, teacher_id, title, content, attachment_url, full_score, start_time, deadline, status) VALUES
(1, 2, '面向对象编程练习', '完成封装、继承、多态相关练习题，提交源代码。', '/uploads/homework/java-oop.docx', 100.00, NOW(), DATE_ADD(NOW(), INTERVAL 10 DAY), 1),
(1, 2, 'Java综合项目', '设计一个学生管理系统，包含增删改查功能。', NULL, 100.00, NOW(), DATE_ADD(NOW(), INTERVAL 21 DAY), 1),
(2, 2, 'Vue路由练习', '完成多页面路由配置和导航守卫练习。', '/uploads/homework/vue-router.docx', 100.00, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 1),
(4, 3, '链表操作练习', '实现单链表的反转、合并和去重操作。', NULL, 100.00, NOW(), DATE_ADD(NOW(), INTERVAL 14 DAY), 1),
(5, 3, '学术摘要写作', '阅读一篇英文论文并撰写200词英文摘要。', '/uploads/homework/abstract-sample.pdf', 100.00, DATE_ADD(NOW(), INTERVAL -5 DAY), DATE_ADD(NOW(), INTERVAL 10 DAY), 1),
(6, 3, '排序算法对比分析', '实现三种排序算法并对比其性能。', NULL, 100.00, NOW(), DATE_ADD(NOW(), INTERVAL 14 DAY), 1),
(7, 2, 'SQL综合查询练习', '完成10道复杂查询题，含多表JOIN和子查询。', '/uploads/homework/sql-advanced.docx', 100.00, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 1);

-- ============================================
-- 6. 更多作业提交 (涉及新老学生)
-- ============================================
INSERT INTO t_homework_submit (homework_id, student_id, submit_content, submit_time, score, feedback, grade_status, grader_id, grade_time) VALUES
(1, 6, '基础语法练习已完成，附代码。',          DATE_ADD(NOW(), INTERVAL -2 DAY), 78.00, '总体完成，分支语句部分有待加强。',       1, 2, NOW()),
(1, 7, 'Java基础语法练习答案。',               DATE_ADD(NOW(), INTERVAL -1 DAY), 95.00, '完成得非常出色，代码规范。',            1, 2, NOW()),
(1, 8, '基础语法作业提交。',                    NOW(),                          NULL,   NULL,                                       0, NULL, NULL),
(1, 9, '已完成基础练习题，见附件。',             DATE_ADD(NOW(), INTERVAL -3 DAY), 88.00, '整体不错，循环部分需要多练习。',         1, 2, NOW()),

(2, 6, 'Vue组件示例已编写完成。',               DATE_ADD(NOW(), INTERVAL -1 DAY), 90.00, '组件设计合理，props使用正确。',          1, 2, NOW()),
(2, 7, '已完成Vue组件练习。',                    NOW(),                          NULL,   NULL,                                       0, NULL, NULL),
(2, 10, 'Vue组件作业，实现了父子通信。',         DATE_ADD(NOW(), INTERVAL -2 DAY), 82.00, '功能实现正确，样式可进一步优化。',        1, 2, NOW()),

(3, 6, 'SQL查询练习已完成。',                    DATE_ADD(NOW(), INTERVAL -1 DAY), 96.00, 'SQL功底扎实，JOIN查询掌握很好。',         1, 2, NOW()),

(4, 7, '面向对象练习代码已提交。',                NOW(),                          NULL,   NULL,                                       0, NULL, NULL),
(4, 8, '封装继承多态练习题，代码见附件。',        DATE_ADD(NOW(), INTERVAL -1 DAY), 91.00, '面向对象概念理解透彻。',                  1, 2, NOW()),
(4, 9, 'OOP练习完成。',                           NOW(),                          NULL,   NULL,                                       0, NULL, NULL),

(5, 7, 'Vue路由练习完成，含导航守卫。',           DATE_ADD(NOW(), INTERVAL -2 DAY), 88.00, '路由配置正确，守卫逻辑清晰。',            1, 2, NOW()),
(5, 8, 'Vue路由作业已提交。',                     NOW(),                          NULL,   NULL,                                       0, NULL, NULL),

(6, 8, '链表操作代码，含反转和合并。',            NOW(),                          NULL,   NULL,                                       0, NULL, NULL),

(7, 10, '排序算法对比分析报告。',                 DATE_ADD(NOW(), INTERVAL -3 DAY), 87.00, '分析到位，数据对比清晰。',                1, 3, NOW()),

(8, 14, '学术摘要写作作业。',                     DATE_ADD(NOW(), INTERVAL -1 DAY), 92.00, '摘要结构完整，语言表达流畅。',            1, 3, NOW()),
(8, 15, '英文摘要已提交。',                       NOW(),                          NULL,   NULL,                                       0, NULL, NULL),

(9, 16, 'SQL综合查询练习已完成。',                NOW(),                          NULL,   NULL,                                       0, NULL, NULL);

-- ============================================
-- 7. 大量新题目 (新增50题，覆盖所有课程)
-- ============================================

-- === Java 课程 (course_id=1) 新增 ===
INSERT INTO t_question (course_id, teacher_id, question_type, title, options, answer, analysis, knowledge_point, difficulty, score, status) VALUES
(1, 2, 'single', '以下哪个关键字用于定义接口？',
 '[{"optionKey":"A","optionContent":"class","sortOrder":1},{"optionKey":"B","optionContent":"interface","sortOrder":2},{"optionKey":"C","optionContent":"abstract","sortOrder":3},{"optionKey":"D","optionContent":"extends","sortOrder":4}]',
 '[{"answerContent":"B","sortOrder":1}]', 'interface 关键字用于定义接口。', '接口', 1, 5.00, 1),

(1, 2, 'single', 'Java 中 main 方法的正确签名是？',
 '[{"optionKey":"A","optionContent":"public void main(String[] args)","sortOrder":1},{"optionKey":"B","optionContent":"public static void main(String[] args)","sortOrder":2},{"optionKey":"C","optionContent":"public static int main(String[] args)","sortOrder":3},{"optionKey":"D","optionContent":"static void main(String args)","sortOrder":4}]',
 '[{"answerContent":"B","sortOrder":1}]', 'main 方法必须是 public static void，参数为 String 数组。', '基础语法', 1, 5.00, 1),

(1, 2, 'single', 'ArrayList 和 LinkedList 的主要区别是？',
 '[{"optionKey":"A","optionContent":"ArrayList 线程安全，LinkedList 不安全","sortOrder":1},{"optionKey":"B","optionContent":"ArrayList 底层是数组，LinkedList 底层是双向链表","sortOrder":2},{"optionKey":"C","optionContent":"ArrayList 不能存 null，LinkedList 可以","sortOrder":3},{"optionKey":"D","optionContent":"没有区别，只是名字不同","sortOrder":4}]',
 '[{"answerContent":"B","sortOrder":1}]', 'ArrayList 基于数组实现，随机访问快；LinkedList 基于链表，插入删除快。', '集合', 2, 5.00, 1),

(1, 2, 'multiple', '以下哪些是 Java 的访问修饰符？',
 '[{"optionKey":"A","optionContent":"public","sortOrder":1},{"optionKey":"B","optionContent":"private","sortOrder":2},{"optionKey":"C","optionContent":"protected","sortOrder":3},{"optionKey":"D","optionContent":"friendly","sortOrder":4}]',
 '[{"answerContent":"A","sortOrder":1},{"answerContent":"B","sortOrder":2},{"answerContent":"C","sortOrder":3}]',
 'public、private、protected 是 Java 的访问修饰符，friendly 不是关键字。', '访问修饰符', 2, 8.00, 1),

(1, 2, 'judge', 'Java 支持多继承。',
 '[{"optionKey":"A","optionContent":"正确","sortOrder":1},{"optionKey":"B","optionContent":"错误","sortOrder":2}]',
 '[{"answerContent":"B","sortOrder":1}]', 'Java 不支持多继承，一个类只能继承一个父类，但可以实现多个接口。', '继承', 1, 5.00, 1),

(1, 2, 'judge', 'static 方法可以直接访问非静态成员变量。',
 '[{"optionKey":"A","optionContent":"正确","sortOrder":1},{"optionKey":"B","optionContent":"错误","sortOrder":2}]',
 '[{"answerContent":"B","sortOrder":1}]', 'static 方法不能直接访问非静态成员，需要先创建对象实例。', 'static关键字', 2, 5.00, 1),

(1, 2, 'blank', 'Java 中所有类的父类是 ______ 。',
 NULL,
 '[{"answerContent":"Object","sortOrder":1}]',
 'Object 类是 Java 中所有类的根父类。', '继承', 1, 5.00, 1),

(1, 2, 'blank', 'Java 中处理异常的两种方式分别是 try-catch 和 ______ 。',
 NULL,
 '[{"answerContent":"throws","sortOrder":1}]',
 'throws 用于在方法签名上声明可能抛出的异常。', '异常处理', 2, 5.00, 1),

(1, 2, 'short_answer', '简述 Overload 和 Override 的区别。',
 NULL,
 '[{"answerContent":"Overload是方法重载，同类中方法名相同参数列表不同；Override是方法重写，子类重新定义父类方法，方法签名相同","sortOrder":1}]',
 '重载发生在编译期，重写发生在运行期。', '多态', 2, 10.00, 1),

(1, 2, 'short_answer', '简述 final 关键字的三种用法。',
 NULL,
 '[{"answerContent":"final修饰类不能被继承，修饰方法不能被重写，修饰变量成为常量不可修改","sortOrder":1}]',
 'final 提供了不可变性保证。', 'final关键字', 2, 10.00, 1);

-- === Vue 课程 (course_id=2) 新增 ===
INSERT INTO t_question (course_id, teacher_id, question_type, title, options, answer, analysis, knowledge_point, difficulty, score, status) VALUES
(2, 2, 'single', 'Vue 3 中创建响应式数据推荐使用？',
 '[{"optionKey":"A","optionContent":"data()","sortOrder":1},{"optionKey":"B","optionContent":"ref() 或 reactive()","sortOrder":2},{"optionKey":"C","optionContent":"computed()","sortOrder":3},{"optionKey":"D","optionContent":"watch()","sortOrder":4}]',
 '[{"answerContent":"B","sortOrder":1}]', 'Vue 3 Composition API 中使用 ref() 和 reactive() 创建响应式数据。', '响应式', 1, 5.00, 1),

(2, 2, 'single', 'Vue Router 中用于动态路由的参数写法是？',
 '[{"optionKey":"A","optionContent":"/user/:id","sortOrder":1},{"optionKey":"B","optionContent":"/user/*id","sortOrder":2},{"optionKey":"C","optionContent":"/user/{id}","sortOrder":3},{"optionKey":"D","optionContent":"/user/<id>","sortOrder":4}]',
 '[{"answerContent":"A","sortOrder":1}]', 'Vue Router 使用冒号 : 来定义动态路由参数。', '路由', 1, 5.00, 1),

(2, 2, 'single', '以下哪个生命周期钩子在 Vue 3 中已移除？',
 '[{"optionKey":"A","optionContent":"onMounted","sortOrder":1},{"optionKey":"B","optionContent":"beforeCreate","sortOrder":2},{"optionKey":"C","optionContent":"onUnmounted","sortOrder":3},{"optionKey":"D","optionContent":"onUpdated","sortOrder":4}]',
 '[{"answerContent":"B","sortOrder":1}]', 'Vue 3 Composition API 中 beforeCreate 和 created 被 setup() 替代。', '生命周期', 2, 5.00, 1),

(2, 2, 'multiple', '以下哪些是 Vue 的内置指令？',
 '[{"optionKey":"A","optionContent":"v-if","sortOrder":1},{"optionKey":"B","optionContent":"v-for","sortOrder":2},{"optionKey":"C","optionContent":"v-style","sortOrder":3},{"optionKey":"D","optionContent":"v-bind","sortOrder":4}]',
 '[{"answerContent":"A","sortOrder":1},{"answerContent":"B","sortOrder":2},{"answerContent":"D","sortOrder":3}]',
 'v-style 不是 Vue 的内置指令，绑定样式使用 v-bind:style。', '指令', 2, 8.00, 1),

(2, 2, 'judge', 'Vue 组件中 data 必须是一个函数而不是对象。',
 '[{"optionKey":"A","optionContent":"正确","sortOrder":1},{"optionKey":"B","optionContent":"错误","sortOrder":2}]',
 '[{"answerContent":"A","sortOrder":1}]', '组件中 data 必须是函数，这样每个实例可以维护独立的数据副本。', '组件', 1, 5.00, 1),

(2, 2, 'judge', 'Vue 中 v-show 和 v-if 的行为完全一致。',
 '[{"optionKey":"A","optionContent":"正确","sortOrder":1},{"optionKey":"B","optionContent":"错误","sortOrder":2}]',
 '[{"answerContent":"B","sortOrder":1}]', 'v-if 是真正的条件渲染（销毁/重建），v-show 只是切换 display 样式。', '指令', 2, 5.00, 1),

(2, 2, 'blank', 'Vue 3 Composition API 中，生命周期 mounted 对应的函数是 ______ 。',
 NULL,
 '[{"answerContent":"onMounted","sortOrder":1}]',
 'onMounted 在组件挂载完成后调用。', '生命周期', 1, 5.00, 1),

(2, 2, 'blank', 'Vue 中父子组件通信，父传子通过 ______ 实现。',
 NULL,
 '[{"answerContent":"props","sortOrder":1}]',
 '父组件通过 props 向子组件传递数据。', '组件通信', 1, 5.00, 1),

(2, 2, 'short_answer', '简述 computed 和 watch 的区别及使用场景。',
 NULL,
 '[{"answerContent":"computed用于计算派生数据，有缓存机制依赖不变不重新计算；watch用于监听数据变化执行副作用如异步请求","sortOrder":1}]',
 'computed 适合模板中的复杂计算，watch 适合响应数据变化执行操作。', '响应式', 3, 10.00, 1),

(2, 2, 'single', 'Pinia 中定义 store 使用哪个函数？',
 '[{"optionKey":"A","optionContent":"createStore","sortOrder":1},{"optionKey":"B","optionContent":"defineStore","sortOrder":2},{"optionKey":"C","optionContent":"useStore","sortOrder":3},{"optionKey":"D","optionContent":"newStore","sortOrder":4}]',
 '[{"answerContent":"B","sortOrder":1}]', 'Pinia 使用 defineStore() 定义 store。', 'Pinia', 2, 5.00, 1);

-- === MySQL 课程 (course_id=3) 新增 ===
INSERT INTO t_question (course_id, teacher_id, question_type, title, options, answer, analysis, knowledge_point, difficulty, score, status) VALUES
(3, 2, 'single', 'SQL 中删除表中所有数据但保留表结构的语句是？',
 '[{"optionKey":"A","optionContent":"DELETE FROM table","sortOrder":1},{"optionKey":"B","optionContent":"DROP TABLE table","sortOrder":2},{"optionKey":"C","optionContent":"TRUNCATE TABLE table","sortOrder":3},{"optionKey":"D","optionContent":"REMOVE FROM table","sortOrder":4}]',
 '[{"answerContent":"C","sortOrder":1}]', 'TRUNCATE 删除所有数据并重置自增ID，DELETE 逐行删除可回滚。', 'DDL', 1, 5.00, 1),

(3, 2, 'single', '以下哪个约束用于保证列值唯一？',
 '[{"optionKey":"A","optionContent":"PRIMARY KEY","sortOrder":1},{"optionKey":"B","optionContent":"UNIQUE","sortOrder":2},{"optionKey":"C","optionContent":"NOT NULL","sortOrder":3},{"optionKey":"D","optionContent":"CHECK","sortOrder":4}]',
 '[{"answerContent":"B","sortOrder":1}]', 'UNIQUE 约束确保列中的所有值各不相同，NULL 除外。', '约束', 1, 5.00, 1),

(3, 2, 'single', 'InnoDB 存储引擎的默认事务隔离级别是？',
 '[{"optionKey":"A","optionContent":"READ UNCOMMITTED","sortOrder":1},{"optionKey":"B","optionContent":"READ COMMITTED","sortOrder":2},{"optionKey":"C","optionContent":"REPEATABLE READ","sortOrder":3},{"optionKey":"D","optionContent":"SERIALIZABLE","sortOrder":4}]',
 '[{"answerContent":"C","sortOrder":1}]', 'InnoDB 默认隔离级别为 REPEATABLE READ，可防止不可重复读。', '事务', 2, 5.00, 1),

(3, 2, 'multiple', '以下哪些是 MySQL 的存储引擎？',
 '[{"optionKey":"A","optionContent":"InnoDB","sortOrder":1},{"optionKey":"B","optionContent":"MyISAM","sortOrder":2},{"optionKey":"C","optionContent":"MongoDB","sortOrder":3},{"optionKey":"D","optionContent":"MEMORY","sortOrder":4}]',
 '[{"answerContent":"A","sortOrder":1},{"answerContent":"B","sortOrder":2},{"answerContent":"D","sortOrder":3}]',
 'InnoDB、MyISAM、MEMORY 都是 MySQL 存储引擎，MongoDB 是独立的非关系型数据库。', '存储引擎', 2, 8.00, 1),

(3, 2, 'judge', 'MySQL 中 VARCHAR 类型的最大长度是 65535 字节。',
 '[{"optionKey":"A","optionContent":"正确","sortOrder":1},{"optionKey":"B","optionContent":"错误","sortOrder":2}]',
 '[{"answerContent":"A","sortOrder":1}]', 'VARCHAR 最大长度为 65535 字节，实际受行大小和字符集影响。', '数据类型', 2, 5.00, 1),

(3, 2, 'judge', '索引越多查询越快。',
 '[{"optionKey":"A","optionContent":"正确","sortOrder":1},{"optionKey":"B","optionContent":"错误","sortOrder":2}]',
 '[{"answerContent":"B","sortOrder":1}]', '索引过多会降低写入性能并占用磁盘空间，需要权衡。', '索引', 2, 5.00, 1),

(3, 2, 'blank', 'MySQL 中用于分组后筛选的关键字是 ______ 。',
 NULL,
 '[{"answerContent":"HAVING","sortOrder":1}]',
 'WHERE 在分组前筛选，HAVING 在分组后筛选聚合结果。', 'SQL查询', 2, 5.00, 1),

(3, 2, 'blank', 'SQL 中用于限制查询结果行数的关键字是 ______ 。',
 NULL,
 '[{"answerContent":"LIMIT","sortOrder":1}]',
 'LIMIT 用于限制 SELECT 返回的记录条数。', 'SQL基础', 1, 5.00, 1),

(3, 2, 'short_answer', '简述 LEFT JOIN、RIGHT JOIN 和 INNER JOIN 的区别。',
 NULL,
 '[{"answerContent":"INNER JOIN取两表交集，LEFT JOIN保留左表全部记录右表无匹配填NULL，RIGHT JOIN保留右表全部记录左表无匹配填NULL","sortOrder":1}]',
 '理解 JOIN 类型是编写正确 SQL 的基础。', 'JOIN查询', 2, 10.00, 1),

(3, 2, 'single', '以下哪个不是数据库事务的 ACID 特性？',
 '[{"optionKey":"A","optionContent":"原子性","sortOrder":1},{"optionKey":"B","optionContent":"一致性","sortOrder":2},{"optionKey":"C","optionContent":"持久性","sortOrder":3},{"optionKey":"D","optionContent":"兼容性","sortOrder":4}]',
 '[{"answerContent":"D","sortOrder":1}]', 'ACID 指原子性(Atomicity)、一致性(Consistency)、隔离性(Isolation)、持久性(Durability)。', '事务', 1, 5.00, 1);

-- === 算法课程 (course_id=4) 新增 ===
INSERT INTO t_question (course_id, teacher_id, question_type, title, options, answer, analysis, knowledge_point, difficulty, score, status) VALUES
(4, 3, 'single', '栈的特点是什么？',
 '[{"optionKey":"A","optionContent":"先进先出","sortOrder":1},{"optionKey":"B","optionContent":"先进后出","sortOrder":2},{"optionKey":"C","optionContent":"随机访问","sortOrder":3},{"optionKey":"D","optionContent":"两端操作","sortOrder":4}]',
 '[{"answerContent":"B","sortOrder":1}]', '栈是 LIFO（Last In First Out）结构。', '栈', 1, 5.00, 1),

(4, 3, 'single', '二叉搜索树的中序遍历结果是？',
 '[{"optionKey":"A","optionContent":"无序序列","sortOrder":1},{"optionKey":"B","optionContent":"递减序列","sortOrder":2},{"optionKey":"C","optionContent":"递增序列","sortOrder":3},{"optionKey":"D","optionContent":"不确定","sortOrder":4}]',
 '[{"answerContent":"C","sortOrder":1}]', '二叉搜索树中序遍历得到递增有序序列。', '二叉树', 2, 5.00, 1),

(4, 3, 'single', '哈希表解决冲突的常用方法是？',
 '[{"optionKey":"A","optionContent":"二分查找","sortOrder":1},{"optionKey":"B","optionContent":"链地址法","sortOrder":2},{"optionKey":"C","optionContent":"冒泡排序","sortOrder":3},{"optionKey":"D","optionContent":"递归","sortOrder":4}]',
 '[{"answerContent":"B","sortOrder":1}]', '链地址法和开放地址法是哈希表解决冲突的两种主要方法。', '哈希表', 2, 5.00, 1),

(4, 3, 'multiple', '以下哪些是常见的排序算法？',
 '[{"optionKey":"A","optionContent":"冒泡排序","sortOrder":1},{"optionKey":"B","optionContent":"二分查找","sortOrder":2},{"optionKey":"C","optionContent":"归并排序","sortOrder":3},{"optionKey":"D","optionContent":"堆排序","sortOrder":4}]',
 '[{"answerContent":"A","sortOrder":1},{"answerContent":"C","sortOrder":2},{"answerContent":"D","sortOrder":3}]',
 '二分查找是搜索算法，不是排序算法。', '排序', 1, 8.00, 1),

(4, 3, 'judge', '二分查找要求数据必须有序。',
 '[{"optionKey":"A","optionContent":"正确","sortOrder":1},{"optionKey":"B","optionContent":"错误","sortOrder":2}]',
 '[{"answerContent":"A","sortOrder":1}]', '二分查找的前提是数据已排序。', '查找算法', 1, 5.00, 1),

(4, 3, 'judge', '图的广度优先遍历（BFS）使用栈来实现。',
 '[{"optionKey":"A","optionContent":"正确","sortOrder":1},{"optionKey":"B","optionContent":"错误","sortOrder":2}]',
 '[{"answerContent":"B","sortOrder":1}]', 'BFS 使用队列实现，DFS（深度优先）通常使用栈或递归实现。', '图', 2, 5.00, 1),

(4, 3, 'blank', '动态规划算法的两个核心要素是最优子结构和 ______ 。',
 NULL,
 '[{"answerContent":"重叠子问题","sortOrder":1}]',
 '最优子结构和重叠子问题是动态规划的两大特征。', '动态规划', 3, 5.00, 1),

(4, 3, 'blank', '队列的特点是 ______ 先出。',
 NULL,
 '[{"answerContent":"先进","sortOrder":1}]',
 '队列是 FIFO（First In First Out）结构。', '队列', 1, 5.00, 1),

(4, 3, 'short_answer', '简述贪心算法和动态规划的区别。',
 NULL,
 '[{"answerContent":"贪心算法每步选择当前最优不求全局最优，动态规划通过记录子问题解求全局最优解","sortOrder":1}]',
 '贪心不一定得到全局最优，动规保证全局最优但复杂度更高。', '算法思想', 3, 10.00, 1),

(4, 3, 'short_answer', '简述链表相比数组的优缺点。',
 NULL,
 '[{"answerContent":"链表插入删除O(1)不需要移动元素但随机访问O(n)，数组随机访问O(1)但插入删除需移动元素","sortOrder":1}]',
 '选择数据结构时需根据操作场景权衡。', '链表', 1, 10.00, 1);

-- === 学术英语课程 (course_id=5) 新增 ===
INSERT INTO t_question (course_id, teacher_id, question_type, title, options, answer, analysis, knowledge_point, difficulty, score, status) VALUES
(5, 3, 'single', '以下哪个单词表示"摘要"？',
 '[{"optionKey":"A","optionContent":"Introduction","sortOrder":1},{"optionKey":"B","optionContent":"Abstract","sortOrder":2},{"optionKey":"C","optionContent":"Conclusion","sortOrder":3},{"optionKey":"D","optionContent":"Reference","sortOrder":4}]',
 '[{"answerContent":"B","sortOrder":1}]', 'Abstract 是论文摘要，Introduction 是引言，Conclusion 是结论。', '学术词汇', 1, 5.00, 1),

(5, 3, 'single', 'APA 引用格式中，文中引用应使用？',
 '[{"optionKey":"A","optionContent":"[1]","sortOrder":1},{"optionKey":"B","optionContent":"(Author, Year)","sortOrder":2},{"optionKey":"C","optionContent":"Author (Year)","sortOrder":3},{"optionKey":"D","optionContent":"见脚注","sortOrder":4}]',
 '[{"answerContent":"B","sortOrder":1}]', 'APA 格式使用作者-日期制 (Author, Year) 进行文中引用。', '引用格式', 2, 5.00, 1),

(5, 3, 'single', '学术写作中表示"因此"的正式表达是？',
 '[{"optionKey":"A","optionContent":"so","sortOrder":1},{"optionKey":"B","optionContent":"therefore","sortOrder":2},{"optionKey":"C","optionContent":"but","sortOrder":3},{"optionKey":"D","optionContent":"and","sortOrder":4}]',
 '[{"answerContent":"B","sortOrder":1}]', '学术写作中therefore比so更正式，常用于因果关系。', '学术表达', 1, 5.00, 1),

(5, 3, 'multiple', '以下哪些属于学术论文的组成部分？',
 '[{"optionKey":"A","optionContent":"Abstract","sortOrder":1},{"optionKey":"B","optionContent":"Methodology","sortOrder":2},{"optionKey":"C","optionContent":"Literature Review","sortOrder":3},{"optionKey":"D","optionContent":"Weather Report","sortOrder":4}]',
 '[{"answerContent":"A","sortOrder":1},{"answerContent":"B","sortOrder":2},{"answerContent":"C","sortOrder":3}]',
 '论文结构通常包括 Abstract、Introduction、Literature Review、Methodology、Results、Discussion、Conclusion。', '论文结构', 1, 8.00, 1),

(5, 3, 'judge', '学术写作中可以使用第一人称"I"。',
 '[{"optionKey":"A","optionContent":"正确","sortOrder":1},{"optionKey":"B","optionContent":"错误","sortOrder":2}]',
 '[{"answerContent":"A","sortOrder":1}]', '现代学术写作中适度使用第一人称已被广泛接受，但需视学科和期刊要求而定。', '写作规范', 2, 5.00, 1),

(5, 3, 'judge', '直接引用他人文字不需要注明出处。',
 '[{"optionKey":"A","optionContent":"正确","sortOrder":1},{"optionKey":"B","optionContent":"错误","sortOrder":2}]',
 '[{"answerContent":"B","sortOrder":1}]', '直接引用必须加引号并标注出处，否则构成抄袭。', '学术诚信', 1, 5.00, 1),

(5, 3, 'blank', '英文论文的关键词通常表示为 ______ 。',
 NULL,
 '[{"answerContent":"Keywords","sortOrder":1}]',
 'Keywords 位于 Abstract 之后，列出3-8个核心术语。', '论文结构', 1, 5.00, 1),

(5, 3, 'short_answer', '简述学术写作中避免抄袭的三种方法。',
 NULL,
 '[{"answerContent":"直接引用加引号和出处；改写用自己的话重新表达并标注来源；使用查重工具检查相似度","sortOrder":1}]',
 '学术诚信是学术写作的基石。', '学术诚信', 2, 10.00, 1),

(5, 3, 'single', '"Literature Review" 的中文意思是？',
 '[{"optionKey":"A","optionContent":"文献综述","sortOrder":1},{"optionKey":"B","optionContent":"研究方法","sortOrder":2},{"optionKey":"C","optionContent":"数据分析","sortOrder":3},{"optionKey":"D","optionContent":"实验设计","sortOrder":4}]',
 '[{"answerContent":"A","sortOrder":1}]', 'Literature Review（文献综述）是对已有研究的系统梳理和评述。', '学术词汇', 1, 5.00, 1),

(5, 3, 'blank', '英文邮件中开头的礼貌称呼通常是 ______ 。',
 NULL,
 '[{"answerContent":"Dear","sortOrder":1}]',
 'Dear 是英文邮件和信函中最常用的礼貌称呼语。', '学术沟通', 1, 5.00, 1);

-- ============================================
-- 8. 更多试卷 (新增4份)
-- ============================================
INSERT INTO t_exam_paper (course_id, teacher_id, title, description, total_score, duration_minutes, status) VALUES
(1, 2, 'Java综合测试卷',          '涵盖基础语法和面向对象，综合性测试。',       70.00, 120, 1),
(2, 2, 'Vue前端基础测试卷',        'Vue 组件、路由和状态管理综合测试。',        45.00,  90, 1),
(3, 2, 'MySQL数据库测试卷',        'SQL查询、表设计和事务基础测试。',            50.00,  90, 1),
(4, 3, '数据结构与算法期末试卷',    '包含排序、查找、树和图的基础考查。',        50.00, 120, 1),
(5, 3, '学术英语期中测试',         '学术词汇、论文结构和写作规范测试。',          45.00,  60, 1);

-- ============================================
-- 9. 试卷-题目关联
-- ============================================
INSERT INTO t_exam_paper_question (paper_id, question_id, score, sort_order, required_answer) VALUES
-- 卷4: Java综合测试卷 (paper_id=4)
(4, 1,  5.00, 1, 1),
(4, 2,  5.00, 3, 1),
(4, 3,  5.00, 5, 1),
(4, 4,  8.00, 7, 1),
(4, 5,  5.00, 9, 1),
(4, 11, 5.00, 2, 1),
(4, 13, 5.00, 4, 1),
(4, 15, 5.00, 6, 1),
(4, 17, 5.00, 8, 1),
(4, 18, 5.00, 10, 1),
(4, 19, 10.00, 11, 1),
(4, 20, 10.00, 12, 1),

-- 卷5: Vue前端基础测试卷 (paper_id=5)
(5, 6,  5.00, 1, 1),
(5, 7,  5.00, 2, 1),
(5, 21, 5.00, 3, 1),
(5, 22, 5.00, 4, 1),
(5, 23, 5.00, 5, 1),
(5, 24, 8.00, 6, 1),
(5, 26, 5.00, 7, 1),
(5, 28, 5.00, 8, 1),
(5, 29, 10.00, 9, 1),

-- 卷6: MySQL数据库测试卷 (paper_id=6)
(6, 8,  5.00, 1, 1),
(6, 31, 5.00, 2, 1),
(6, 32, 5.00, 3, 1),
(6, 33, 5.00, 4, 1),
(6, 34, 8.00, 5, 1),
(6, 36, 5.00, 6, 1),
(6, 37, 5.00, 7, 1),
(6, 38, 5.00, 8, 1),
(6, 39, 10.00, 9, 1),
(6, 40, 5.00, 10, 1),

-- 卷7: 数据结构与算法期末试卷 (paper_id=7)
(7, 9,  5.00, 1, 1),
(7, 10, 8.00, 2, 1),
(7, 41, 5.00, 3, 1),
(7, 42, 5.00, 4, 1),
(7, 43, 5.00, 5, 1),
(7, 44, 8.00, 6, 1),
(7, 46, 5.00, 7, 1),
(7, 48, 5.00, 8, 1),
(7, 49, 10.00, 9, 1),
(7, 50, 10.00, 10, 1),

-- 卷8: 学术英语期中测试 (paper_id=8)
(8, 51, 5.00, 1, 1),
(8, 52, 5.00, 2, 1),
(8, 53, 5.00, 3, 1),
(8, 54, 8.00, 4, 1),
(8, 56, 5.00, 5, 1),
(8, 57, 5.00, 6, 1),
(8, 58, 10.00, 7, 1),
(8, 59, 5.00, 8, 1);

-- ============================================
-- 10. 更多考试安排
-- ============================================
INSERT INTO t_exam_session (class_id, paper_id, teacher_id, title, start_time, end_time, duration, status) VALUES
(1, 4, 2, 'Java综合测试',       DATE_ADD(NOW(), INTERVAL 7 DAY),  DATE_ADD(DATE_ADD(NOW(), INTERVAL 7 DAY), INTERVAL 120 MINUTE), 120, 1),
(2, 5, 2, 'Vue基础测试',         DATE_ADD(NOW(), INTERVAL 5 DAY),  DATE_ADD(DATE_ADD(NOW(), INTERVAL 5 DAY), INTERVAL 90 MINUTE),   90, 1),
(3, 6, 2, 'MySQL数据库测试',     DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 10 DAY), INTERVAL 90 MINUTE),  90, 1),
(5, 6, 2, '软工2班MySQL测试',    DATE_ADD(NOW(), INTERVAL 12 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 12 DAY), INTERVAL 90 MINUTE),  90, 1),
(4, 7, 3, '计科1班算法测试',     DATE_ADD(NOW(), INTERVAL 6 DAY),  DATE_ADD(DATE_ADD(NOW(), INTERVAL 6 DAY), INTERVAL 120 MINUTE),  120, 1),
(6, 8, 3, '学术英语期中考试',    DATE_ADD(NOW(), INTERVAL 8 DAY),  DATE_ADD(DATE_ADD(NOW(), INTERVAL 8 DAY), INTERVAL 60 MINUTE),   60, 1);

-- ============================================
-- 11. 更多考试记录
-- ============================================
INSERT INTO t_exam_record (exam_session_id, student_id, start_time, submit_time, total_score, objective_score, subjective_score, status) VALUES
(1, 7, NOW(), NOW(), 35.00, 30.00,  5.00, 2),
(1, 8, NOW(), NOW(), 41.00, 33.00,  8.00, 2),
(2, 4, DATE_ADD(NOW(), INTERVAL -3 DAY), DATE_ADD(NOW(), INTERVAL -3 DAY), 32.00, 25.00, 7.00, 2),
(2, 7, DATE_ADD(NOW(), INTERVAL -3 DAY), DATE_ADD(NOW(), INTERVAL -3 DAY), 28.00, 20.00, 8.00, 2),
(2, 10, DATE_ADD(NOW(), INTERVAL -3 DAY), DATE_ADD(NOW(), INTERVAL -3 DAY), 35.00, 28.00, 7.00, 2),
(3, 5, DATE_ADD(NOW(), INTERVAL -2 DAY), DATE_ADD(NOW(), INTERVAL -2 DAY), 40.00, 35.00, 5.00, 2),
(3, 6, DATE_ADD(NOW(), INTERVAL -2 DAY), DATE_ADD(NOW(), INTERVAL -2 DAY), 45.00, 38.00, 7.00, 2),
(3, 10, DATE_ADD(NOW(), INTERVAL -2 DAY), DATE_ADD(NOW(), INTERVAL -2 DAY), 32.00, 28.00, 4.00, 2),
(1, 9, NOW(), NULL, NULL, NULL, NULL, 0),
(2, 11, NOW(), NULL, NULL, NULL, NULL, 0),
(3, 12, NOW(), NULL, NULL, NULL, NULL, 0);

-- ============================================
-- 12. 更多考试答题记录
-- ============================================
INSERT INTO t_exam_answer (exam_record_id, question_id, student_answer, correct_answer, score, is_correct, grade_status, grader_id, grade_time) VALUES
-- 学生7 在Java基础测验中的答题 (exam_record_id=4)
(4, 1, 'B', '[{"answerContent":"B","sortOrder":1}]', 5.00, 1, 1, 2, NOW()),
(4, 2, 'A', '[{"answerContent":"A","sortOrder":1}]', 5.00, 1, 1, 2, NOW()),
(4, 3, '["A","C"]', '[{"answerContent":"A","sortOrder":1},{"answerContent":"C","sortOrder":2},{"answerContent":"D","sortOrder":3}]', 0.00, 0, 1, 2, NOW()),
(4, 6, 'B', '[{"answerContent":"B","sortOrder":1}]', 5.00, 1, 1, 2, NOW()),
(4, 7, 'A', '[{"answerContent":"B","sortOrder":1}]', 0.00, 0, 1, 2, NOW()),
(4, 5, '封装继承多态是三大特性', '[{"answerContent":"封装、继承、多态","sortOrder":1}]', 5.00, 1, 2, 2, NOW()),

-- 学生8 在Java基础测验中的答题 (exam_record_id=5)
(5, 1, 'B', '[{"answerContent":"B","sortOrder":1}]', 5.00, 1, 1, 2, NOW()),
(5, 2, 'A', '[{"answerContent":"A","sortOrder":1}]', 5.00, 1, 1, 2, NOW()),
(5, 3, '["A","C","D"]', '[{"answerContent":"A","sortOrder":1},{"answerContent":"C","sortOrder":2},{"answerContent":"D","sortOrder":3}]', 8.00, 1, 1, 2, NOW()),
(5, 6, 'B', '[{"answerContent":"B","sortOrder":1}]', 5.00, 1, 1, 2, NOW()),
(5, 7, 'B', '[{"answerContent":"B","sortOrder":1}]', 5.00, 1, 1, 2, NOW()),
(5, 5, '封装继承多态', '[{"answerContent":"封装、继承、多态","sortOrder":1}]', 8.00, 1, 2, 2, NOW()),

-- 学生4 在面向对象测验中的答题 (exam_record_id=6)
(6, 2, 'A', '[{"answerContent":"A","sortOrder":1}]', 5.00, 1, 1, 2, NOW()),
(6, 5, '封装继承多态', '[{"answerContent":"封装、继承、多态","sortOrder":1}]', 7.00, 1, 2, 2, NOW()),

-- 学生7 在面向对象测验中的答题 (exam_record_id=7)
(7, 2, 'A', '[{"answerContent":"A","sortOrder":1}]', 5.00, 1, 1, 2, NOW()),
(7, 5, '三大特性是封装继承和多态', '[{"answerContent":"封装、继承、多态","sortOrder":1}]', 8.00, 1, 2, 2, NOW()),

-- 学生10 在面向对象测验中的答题 (exam_record_id=8)
(8, 2, 'A', '[{"answerContent":"A","sortOrder":1}]', 5.00, 1, 1, 2, NOW()),
(8, 5, '封装继承多态', '[{"answerContent":"封装、继承、多态","sortOrder":1}]', 7.00, 1, 2, 2, NOW());

-- ============================================
-- 13. 更多成绩记录
-- ============================================
INSERT INTO t_grade_record (student_id, class_id, course_id, teacher_id, grade_name, source_type, source_id, score, full_score, grade_comment) VALUES
(6, 1, 1, 2, 'Java基础语法练习',      'homework', 1,  78.00, 100.00, '作业成绩'),
(7, 1, 1, 2, 'Java基础语法练习',      'homework', 1,  95.00, 100.00, '作业成绩'),
(7, 1, 1, 2, '面向对象编程练习',      'homework', 4,  NULL,   100.00, '待批改'),
(8, 1, 1, 2, '面向对象编程练习',      'homework', 4,  91.00, 100.00, '作业成绩'),
(9, 1, 1, 2, 'Java基础语法练习',      'homework', 1,  88.00, 100.00, '作业成绩'),
(6, 3, 3, 2, 'SQL查询练习',           'homework', 3,  96.00, 100.00, '作业成绩'),
(7, 2, 2, 2, 'Vue组件练习',           'homework', 2,  NULL,   100.00, '待批改'),
(10, 2, 2, 2, 'Vue组件练习',          'homework', 2,  82.00, 100.00, '作业成绩'),
(7, 2, 2, 2, 'Vue路由练习',           'homework', 5,  88.00, 100.00, '作业成绩'),
(10, 4, 4, 3, '排序算法对比分析',      'homework', 7,  87.00, 100.00, '作业成绩'),
(14, 6, 5, 3, '学术摘要写作',          'homework', 8,  92.00, 100.00, '作业成绩'),

-- 考试成绩
(7,  1, 1, 2, 'Java基础第一次测验',   'exam',    4,   35.00,  43.00, '考试成绩'),
(8,  1, 1, 2, 'Java基础第一次测验',   'exam',    5,   41.00,  43.00, '考试成绩'),
(4,  1, 1, 2, 'Java面向对象测验',     'exam',    6,   32.00,  20.00, '考试成绩'),
(7,  1, 1, 2, 'Java面向对象测验',     'exam',    7,   28.00,  20.00, '考试成绩'),
(10, 1, 1, 2, 'Java面向对象测验',     'exam',    8,   35.00,  20.00, '考试成绩'),
(5,  3, 3, 2, 'MySQL数据库测试',      'exam',    9,   40.00,  50.00, '考试成绩'),
(6,  3, 3, 2, 'MySQL数据库测试',      'exam',    10,  45.00,  50.00, '考试成绩'),
(10, 3, 3, 2, 'MySQL数据库测试',      'exam',    11,  32.00,  50.00, '考试成绩');

-- ============================================
-- 14. 论文数据 (t_paper + t_paper_author)
-- ============================================
INSERT INTO t_paper (arxiv_id, title, abstract_text, year, url, pdf_url, venue, citation_count, source) VALUES
('1706.03762', 'Attention Is All You Need',
 'The dominant sequence transduction models are based on complex recurrent or convolutional neural networks that include an encoder and a decoder. The best performing models also connect the encoder and decoder through an attention mechanism. We propose a new simple network architecture, the Transformer, based solely on attention mechanisms, dispensing with recurrence and convolutions entirely.',
 2017, 'https://arxiv.org/abs/1706.03762', 'https://arxiv.org/pdf/1706.03762.pdf', 'NeurIPS 2017', 120000, 'arxiv'),

('1810.04805', 'BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding',
 'We introduce a new language representation model called BERT, which stands for Bidirectional Encoder Representations from Transformers. Unlike recent language representation models, BERT is designed to pre-train deep bidirectional representations from unlabeled text by jointly conditioning on both left and right context in all layers.',
 2018, 'https://arxiv.org/abs/1810.04805', 'https://arxiv.org/pdf/1810.04805.pdf', 'NAACL 2019', 95000, 'arxiv'),

('2005.14165', 'Language Models are Few-Shot Learners (GPT-3)',
 'Recent work has demonstrated substantial gains on many NLP tasks and benchmarks by pre-training on a large corpus of text followed by fine-tuning on a specific task. While typically task-agnostic in architecture, this method still requires task-specific fine-tuning datasets. We demonstrate that scaling up language models greatly improves task-agnostic, few-shot performance.',
 2020, 'https://arxiv.org/abs/2005.14165', 'https://arxiv.org/pdf/2005.14165.pdf', 'NeurIPS 2020', 45000, 'arxiv'),

('2303.08774', 'GPT-4 Technical Report',
 'We report the development of GPT-4, a large-scale, multimodal model which can accept image and text inputs and produce text outputs. While less capable than humans in many real-world scenarios, GPT-4 exhibits human-level performance on various professional and academic benchmarks.',
 2023, 'https://arxiv.org/abs/2303.08774', 'https://arxiv.org/pdf/2303.08774.pdf', 'OpenAI Technical Report', 15000, 'arxiv'),

('1512.03385', 'Deep Residual Learning for Image Recognition',
 'Deeper neural networks are more difficult to train. We present a residual learning framework to ease the training of networks that are substantially deeper than those used previously. We explicitly reformulate the layers as learning residual functions with reference to the layer inputs, instead of learning unreferenced functions.',
 2015, 'https://arxiv.org/abs/1512.03385', 'https://arxiv.org/pdf/1512.03385.pdf', 'CVPR 2016', 110000, 'arxiv'),

('1406.2661', 'Generative Adversarial Networks',
 'We propose a new framework for estimating generative models via an adversarial process, in which we simultaneously train two models: a generative model G that captures the data distribution, and a discriminative model D that estimates the probability that a sample came from the training data rather than G.',
 2014, 'https://arxiv.org/abs/1406.2661', 'https://arxiv.org/pdf/1406.2661.pdf', 'NeurIPS 2014', 65000, 'arxiv'),

('1603.04467', 'TensorFlow: A System for Large-Scale Machine Learning',
 'TensorFlow is a machine learning system that operates at large scale and in heterogeneous environments. TensorFlow uses dataflow graphs to represent computation, shared state, and the operations that mutate that state.',
 2016, 'https://arxiv.org/abs/1603.04467', 'https://arxiv.org/pdf/1603.04467.pdf', 'OSDI 2016', 35000, 'arxiv'),

('1906.04341', 'Generating Long Sequences with Sparse Transformers',
 'Transformers are powerful sequence models, but require time and memory that scale quadratically with the sequence length. We introduce sparse factorizations of the attention matrix which reduce this to O(n*sqrt(n)).',
 2019, 'https://arxiv.org/abs/1906.04341', 'https://arxiv.org/pdf/1906.04341.pdf', 'arXiv preprint', 3200, 'arxiv'),

('1701.03757', 'Deep Reinforcement Learning: An Overview',
 'We provide an overview of deep reinforcement learning, including its main approaches, key algorithms, and major applications. We discuss deep Q-networks, policy gradient methods, and actor-critic architectures.',
 2017, 'https://arxiv.org/abs/1701.03757', 'https://arxiv.org/pdf/1701.03757.pdf', 'arXiv preprint', 2800, 'arxiv'),

('2101.00027', 'Swin Transformer: Hierarchical Vision Transformer using Shifted Windows',
 'This paper presents a new vision Transformer, called Swin Transformer, that can serve as a general-purpose backbone for computer vision. The hierarchical architecture is built by merging image patches in deeper layers and has linear computation complexity with respect to image size.',
 2021, 'https://arxiv.org/abs/2101.00027', 'https://arxiv.org/pdf/2101.00027.pdf', 'ICCV 2021', 18000, 'arxiv'),

('1912.01703', 'On the Relationship between Self-Attention and Convolutional Layers',
 'Recent trends of incorporating attention mechanisms in vision have led to a number of works exploring the combination of convolutions and self-attention. We show that multi-head self-attention layers can express any convolutional layer.',
 2019, 'https://arxiv.org/abs/1912.01703', 'https://arxiv.org/pdf/1912.01703.pdf', 'ICLR 2020', 1800, 'arxiv'),

('1802.05751', 'Graph Neural Networks: A Review of Methods and Applications',
 'Lots of learning tasks require dealing with graph data which contains rich relational information. Graph Neural Networks (GNNs) are connectionist models that capture the dependence of graphs via message passing between the nodes of graphs.',
 2018, 'https://arxiv.org/abs/1802.05751', 'https://arxiv.org/pdf/1802.05751.pdf', 'arXiv preprint', 7800, 'arxiv'),

('2203.02155', 'Training Language Models to Follow Instructions (InstructGPT)',
 'Making language models bigger does not inherently make them better at following a user''s intent. We show an avenue for aligning language models with user intent on a wide range of tasks by fine-tuning with human feedback.',
 2022, 'https://arxiv.org/abs/2203.02155', 'https://arxiv.org/pdf/2203.02155.pdf', 'NeurIPS 2022', 8500, 'arxiv'),

('2212.14024', 'LLaMA: Open and Efficient Foundation Language Models',
 'We introduce LLaMA, a collection of foundation language models ranging from 7B to 65B parameters. We train our models on trillions of tokens, and show that it is possible to train state-of-the-art models using publicly available datasets exclusively.',
 2023, 'https://arxiv.org/abs/2212.14024', 'https://arxiv.org/pdf/2212.14024.pdf', 'arXiv preprint', 12000, 'arxiv'),

('2302.13971', 'LLaMA 2: Open Foundation and Fine-Tuned Chat Models',
 'In this work, we develop and release LLaMA 2, a collection of pretrained and fine-tuned large language models (LLMs) ranging in scale from 7 billion to 70 billion parameters. Our fine-tuned LLMs, called LLaMA 2-Chat, are optimized for dialogue use cases.',
 2023, 'https://arxiv.org/abs/2302.13971', 'https://arxiv.org/pdf/2302.13971.pdf', 'Meta AI Technical Report', 25000, 'arxiv');

INSERT INTO t_paper_author (paper_id, name, sort_order) VALUES
(1, 'Vaswani, Ashish',       0), (1, 'Shazeer, Noam',         1),
(1, 'Parmar, Niki',          2), (1, 'Uszkoreit, Jakob',      3),
(1, 'Jones, Llion',          4), (1, 'Gomez, Aidan N.',       5),
(1, 'Kaiser, Lukasz',        6), (1, 'Polosukhin, Illia',     7),
(2, 'Devlin, Jacob',         0), (2, 'Chang, Ming-Wei',       1),
(2, 'Lee, Kenton',           2), (2, 'Toutanova, Kristina',   3),
(3, 'Brown, Tom B.',         0), (3, 'Mann, Benjamin',        1),
(3, 'Ryder, Nick',           2), (3, 'Subbiah, Melanie',      3),
(4, 'OpenAI',                0),
(5, 'He, Kaiming',           0), (5, 'Zhang, Xiangyu',        1),
(5, 'Ren, Shaoqing',         2), (5, 'Sun, Jian',            3),
(6, 'Goodfellow, Ian',       0), (6, 'Pouget-Abadie, Jean',   1),
(6, 'Mirza, Mehdi',          2), (6, 'Xu, Bing',             3),
(6, 'Warde-Farley, David',   4), (6, 'Ozair, Sherjil',        5),
(6, 'Courville, Aaron',      6), (6, 'Bengio, Yoshua',        7),
(7, 'Abadi, Martin',         0), (7, 'Barham, Paul',         1),
(7, 'Chen, Jianmin',         2), (7, 'Davis, Andy',           3),
(8, 'Child, Rewon',          0), (8, 'Gray, Scott',           1),
(8, 'Radford, Alec',         2), (8, 'Sutskever, Ilya',       3),
(9, 'Arulkumaran, Kai',      0), (9, 'Deisenroth, Marc Peter', 1),
(9, 'Brundage, Miles',       2), (9, 'Bharath, Anil Anthony', 3),
(10, 'Liu, Ze',              0), (10, 'Lin, Yutong',          1),
(10, 'Cao, Yue',             2), (10, 'Hu, Han',              3),
(11, 'Cordonnier, Jean-Baptiste', 0), (11, 'Loukas, Andreas',  1),
(11, 'Jaggi, Martin',        2),
(12, 'Zhou, Jie',            0), (12, 'Cui, Ganqu',           1),
(12, 'Hu, Shengding',        2), (12, 'Zhang, Zhengyan',      3),
(13, 'Ouyang, Long',         0), (13, 'Wu, Jeff',             1),
(13, 'Jiang, Xu',            2), (13, 'Almeida, Diogo',       3),
(14, 'Touvron, Hugo',        0), (14, 'Lavril, Thibaut',      1),
(14, 'Izacard, Gautier',     2), (14, 'Martinet, Xavier',     3),
(15, 'Touvron, Hugo',        0), (15, 'Martin, Louis',        1),
(15, 'Stone, Kevin',         2), (15, 'Albert, Peter',        3);

-- ============================================
-- 15. 更多通知
-- ============================================
INSERT INTO t_notice (publisher_id, class_id, title, content, notice_type, status) VALUES
(2, 1, '面向对象编程作业发布',    '请完成面向对象编程练习并按时提交。',                     'homework', 1),
(3, 4, '排序算法小测通知',        '下周一将进行排序算法小测，请做好复习准备。',               'exam',     1),
(2, 3, '数据库期末复习指南',      '期末考试范围为SQL查询和表设计，请参考课件复习。',          'system',   1),
(3, 6, '学术论文写作技巧讲座',    '本周五下午举办学术写作技巧讲座，欢迎同学参加。',            'system',   1),
(1, NULL, '五一假期课程安排通知',  '五一期间所有课程正常进行，请同学们合理安排时间。',          'system',   1),
(2, 1, 'Java综合测试复习提纲',    '综合测试涵盖基础语法和面向对象，请提前复习相关章节。',      'exam',     1);

-- ============================================
-- 16. 更多操作日志
-- ============================================
INSERT INTO t_operation_log (user_id, operation, method, url, params, ip) VALUES
(2, '用户登录',       'POST',   '/auth/login',         '{"username":"teacher"}',   '127.0.0.1'),
(3, '用户登录',       'POST',   '/auth/login',         '{"username":"teacher2"}',  '127.0.0.1'),
(2, '新增题目',       'POST',   '/api/questions',       '{"type":"SINGLE_CHOICE"}', '127.0.0.1'),
(2, '新增试卷',       'POST',   '/api/papers',          '{"title":"Java综合测试卷"}','127.0.0.1'),
(2, '发布考试',       'POST',   '/api/exam-sessions',   '{"title":"Java综合测试"}', '127.0.0.1'),
(3, '新增题目',       'POST',   '/api/questions',       '{"type":"SINGLE_CHOICE"}', '127.0.0.1'),
(3, '新增课程',       'POST',   '/courses',             '{"title":"学术英语写作"}', '127.0.0.1'),
(1, '修改用户状态',   'PUT',    '/users/5/status',      '{"status":1}',              '127.0.0.1'),
(2, '批改作业',       'POST',   '/homework-submits/grade','{"score":92}',            '127.0.0.1'),
(2, '发布通知',       'POST',   '/notices',             '{"title":"作业提醒"}',      '127.0.0.1'),
(3, '批改作业',       'POST',   '/homework-submits/grade','{"score":87}',            '127.0.0.1'),
(4, '用户登录',       'POST',   '/auth/login',         '{"username":"student"}',   '127.0.0.1'),
(4, '提交作业',       'POST',   '/homework-submits',    '{"homeworkId":1}',          '127.0.0.1'),
(5, '用户登录',       'POST',   '/auth/login',         '{"username":"student2"}',  '127.0.0.1'),
(5, '开始考试',       'POST',   '/api/exam-records',    '{"sessionId":1}',           '127.0.0.1');
