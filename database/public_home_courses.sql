-- 首页公共课预置数据。
-- 启用后的首页推荐课程允许学生在课程详情页自助加入其首个启用教学班。
INSERT INTO t_home_course_recommendation
    (course_id, reason, sort_order, status, create_time, update_time)
SELECT
    c.id,
    seed.reason,
    seed.sort_order,
    1,
    NOW(),
    NOW()
FROM t_course c
JOIN (
    SELECT 'DEMO_互动式机器学习实验' AS title, '拖动学习率并完成互动实验，适合作为机器学习实践入门。' AS reason, 1 AS sort_order
    UNION ALL SELECT '互动', '通过互动课本体验参数变化与即时反馈。', 2
    UNION ALL SELECT 'LeetCode刷题精讲', '从常见题型到解题模板，系统训练算法思维。', 3
    UNION ALL SELECT 'UML建模与系统设计', '通过真实案例掌握需求分析、建模与系统设计。', 4
    UNION ALL SELECT '专业英语阅读与翻译', '结合技术材料提升专业英语阅读与表达能力。', 5
    UNION ALL SELECT '线性代数与解析几何', '夯实向量、矩阵与空间解析几何基础。', 6
    UNION ALL SELECT 'Node.js服务端开发', '从基础 API 到工程实践构建服务端开发能力。', 7
    UNION ALL SELECT '计算机专业导论', '建立计算机学科知识地图，适合新生公共学习。', 8
) seed ON seed.title = c.title
WHERE c.status = 1
  AND EXISTS (
      SELECT 1 FROM t_teaching_class tc
      WHERE tc.course_id = c.id AND tc.status = 1
  )
  AND EXISTS (
      SELECT 1
      FROM t_chapter ch
      JOIN t_lesson l ON l.chapter_id = ch.id
      WHERE ch.course_id = c.id
  )
ON DUPLICATE KEY UPDATE
    reason = VALUES(reason),
    sort_order = VALUES(sort_order),
    status = 1,
    update_time = NOW();
