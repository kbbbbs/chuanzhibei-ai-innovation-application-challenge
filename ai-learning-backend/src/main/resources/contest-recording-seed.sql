-- Synthetic contest-recording fixture. Insert missing demo rows only.

-- Demo accounts demo_teacher / demo_student both use Demo10039!.

-- The recording profile uses a separate local H2 database.

INSERT INTO t_user (id,username,password,nickname,email,status) SELECT 1003901,'demo_teacher','97a1cdb3ebad79cbb00cdeaf507e8620','录制教师','demo_teacher@example.com',1 WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE id=1003901);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1003901,1003901,2 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1003901);

INSERT INTO t_user (id,username,password,nickname,email,status) SELECT 1003902,'demo_student','97a1cdb3ebad79cbb00cdeaf507e8620','录制学生','demo_student@example.com',1 WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE id=1003902);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1003902,1003902,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1003902);

INSERT INTO t_course (id,category_id,teacher_id,title,description,teacher,difficulty,status,student_count) SELECT 1003911,5,1003901,'录制示例：梯度下降与学习率','已发布课程，用于展示课时选文与课程资料RAG。','录制教师','beginner',1,1 WHERE NOT EXISTS (SELECT 1 FROM t_course WHERE id=1003911);

INSERT INTO t_course (id,category_id,teacher_id,title,description,teacher,difficulty,status,student_count) SELECT 1003912,5,1003901,'录制草稿：发布后学生可见','学生已加入对应教学班。教师编辑本草稿并发布后，学生课程列表显示本课程。','录制教师','beginner',2,1 WHERE NOT EXISTS (SELECT 1 FROM t_course WHERE id=1003912);

INSERT INTO t_course (id,category_id,teacher_id,title,description,teacher,difficulty,status,student_count) SELECT 1003913,5,1003901,'录制示例：仅解释选中文本','已发布课程，刻意不授权资料，用于演示仅依据选中文本。','录制教师','beginner',1,1 WHERE NOT EXISTS (SELECT 1 FROM t_course WHERE id=1003913);

INSERT INTO t_chapter (id,course_id,title,sort_order,status) SELECT 1003941,1003911,'梯度下降入门',1,1 WHERE NOT EXISTS (SELECT 1 FROM t_chapter WHERE id=1003941);

INSERT INTO t_chapter (id,course_id,title,sort_order,status) SELECT 1003942,1003912,'待发布示例章节',1,1 WHERE NOT EXISTS (SELECT 1 FROM t_chapter WHERE id=1003942);

INSERT INTO t_chapter (id,course_id,title,sort_order,status) SELECT 1003943,1003913,'文本解释示例',1,1 WHERE NOT EXISTS (SELECT 1 FROM t_chapter WHERE id=1003943);

INSERT INTO t_lesson (id,chapter_id,title,resource_type,content,sort_order,status) SELECT 1003951,1003941,'梯度下降如何更新参数','markdown','# 梯度下降

梯度下降沿损失函数的负梯度方向更新模型参数。学习率决定每次更新的步长。

选中这段文字，点击解释这段，再结合课程资料提问。',1,1 WHERE NOT EXISTS (SELECT 1 FROM t_lesson WHERE id=1003951);

INSERT INTO t_lesson (id,chapter_id,title,resource_type,content,sort_order,status) SELECT 1003952,1003941,'学习率与更新步长','markdown','# 学习率

学习率过大可能使优化过程不稳定，过小则可能减慢收敛。请结合资料比较不同步长的影响。',2,1 WHERE NOT EXISTS (SELECT 1 FROM t_lesson WHERE id=1003952);

INSERT INTO t_lesson (id,chapter_id,title,resource_type,content,sort_order,status) SELECT 1003953,1003942,'发布操作示例课时','markdown','# 发布演示

这门课程已预置教学班和学生成员。教师完成内容编辑并发布课程后，学生刷新我的课程即可看到。',1,1 WHERE NOT EXISTS (SELECT 1 FROM t_lesson WHERE id=1003953);

INSERT INTO t_lesson (id,chapter_id,title,resource_type,content,sort_order,status) SELECT 1003954,1003943,'只解释当前选文','markdown','# 当前选文解释

学习率决定参数更新的步长。请选择这句话，观察没有资料授权时的解释模式。',1,1 WHERE NOT EXISTS (SELECT 1 FROM t_lesson WHERE id=1003954);

INSERT INTO t_teaching_class (id,course_id,teacher_id,class_name,semester,description,status) SELECT 1003931,1003911,1003901,'录制教学班：RAG学习','2026录制演示','合成演示关系，学生预先加入。',1 WHERE NOT EXISTS (SELECT 1 FROM t_teaching_class WHERE id=1003931);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1003961,1003931,1003902,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1003961);

INSERT INTO t_teaching_class (id,course_id,teacher_id,class_name,semester,description,status) SELECT 1003932,1003912,1003901,'录制教学班：发布验证','2026录制演示','合成演示关系，学生预先加入。',1 WHERE NOT EXISTS (SELECT 1 FROM t_teaching_class WHERE id=1003932);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1003962,1003932,1003902,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1003962);

INSERT INTO t_teaching_class (id,course_id,teacher_id,class_name,semester,description,status) SELECT 1003933,1003913,1003901,'录制教学班：选文解释','2026录制演示','合成演示关系，学生预先加入。',1 WHERE NOT EXISTS (SELECT 1 FROM t_teaching_class WHERE id=1003933);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1003963,1003933,1003902,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1003963);
