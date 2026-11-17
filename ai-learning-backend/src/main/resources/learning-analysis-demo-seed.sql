-- Synthetic accounts for local analysis demonstrations only.
INSERT INTO t_user (id,username,password,nickname,email,status) SELECT 1003901,'demo_teacher','97a1cdb3ebad79cbb00cdeaf507e8620','录制教师','demo_teacher@example.com',1 WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE id=1003901);
INSERT INTO t_user_role (id,user_id,role_id) SELECT 1003901,1003901,2 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1003901);
INSERT INTO t_user (id,username,password,nickname,email,status) SELECT 1003902,'demo_student','97a1cdb3ebad79cbb00cdeaf507e8620','录制学生','demo_student@example.com',1 WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE id=1003902);
INSERT INTO t_user_role (id,user_id,role_id) SELECT 1003902,1003902,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1003902);

-- Synthetic learning-analysis demonstration. Dedicated IDs, insert missing rows only.

-- Passwords inherit the existing local demo account hashes. No real student information.

INSERT INTO t_course (id,category_id,teacher_id,title,description,teacher,difficulty,status,student_count,setup_stage) SELECT 1004911,5,1003901,'学情演示：机器学习基础（合成数据）','8名合成学生、3次考试和1次作业，用于展示成绩趋势和待批改状态。','录制教师','beginner',1,16,'COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM t_course WHERE id=1004911);

INSERT INTO t_teaching_class (id,course_id,teacher_id,class_name,semester,description,status) SELECT 1004931,1004911,1003901,'学情分析演示班（合成数据）','2026秋','所有成绩均为演示预置，非真实教学结果。',1 WHERE NOT EXISTS (SELECT 1 FROM t_teaching_class WHERE id=1004931);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004960,1004931,1003902,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004960);

INSERT INTO t_user (id,username,password,nickname,email,status) SELECT 1004921,'analysis_student_01',password,'演示学生01·稳步提升','analysis01@example.com',1 FROM t_user WHERE id=1003902 AND NOT EXISTS (SELECT 1 FROM t_user WHERE id=1004921);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1004921,1004921,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1004921);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004961,1004931,1004921,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004961);

INSERT INTO t_user (id,username,password,nickname,email,status) SELECT 1004922,'analysis_student_02',password,'演示学生02·持续薄弱','analysis02@example.com',1 FROM t_user WHERE id=1003902 AND NOT EXISTS (SELECT 1 FROM t_user WHERE id=1004922);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1004922,1004922,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1004922);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004962,1004931,1004922,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004962);

INSERT INTO t_user (id,username,password,nickname,email,status) SELECT 1004923,'analysis_student_03',password,'演示学生03·成绩下降','analysis03@example.com',1 FROM t_user WHERE id=1003902 AND NOT EXISTS (SELECT 1 FROM t_user WHERE id=1004923);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1004923,1004923,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1004923);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004963,1004931,1004923,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004963);

INSERT INTO t_user (id,username,password,nickname,email,status) SELECT 1004924,'analysis_student_04',password,'演示学生04·稳定优秀','analysis04@example.com',1 FROM t_user WHERE id=1003902 AND NOT EXISTS (SELECT 1 FROM t_user WHERE id=1004924);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1004924,1004924,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1004924);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004964,1004931,1004924,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004964);

INSERT INTO t_user (id,username,password,nickname,email,status) SELECT 1004925,'analysis_student_05',password,'演示学生05·待批改','analysis05@example.com',1 FROM t_user WHERE id=1003902 AND NOT EXISTS (SELECT 1 FROM t_user WHERE id=1004925);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1004925,1004925,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1004925);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004965,1004931,1004925,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004965);

INSERT INTO t_user (id,username,password,nickname,email,status) SELECT 1004926,'analysis_student_06',password,'演示学生06·进行中','analysis06@example.com',1 FROM t_user WHERE id=1003902 AND NOT EXISTS (SELECT 1 FROM t_user WHERE id=1004926);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1004926,1004926,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1004926);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004966,1004931,1004926,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004966);

INSERT INTO t_user (id,username,password,nickname,email,status) SELECT 1004927,'analysis_student_07',password,'演示学生07·未提交','analysis07@example.com',1 FROM t_user WHERE id=1003902 AND NOT EXISTS (SELECT 1 FROM t_user WHERE id=1004927);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1004927,1004927,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1004927);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004967,1004931,1004927,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004967);

INSERT INTO t_exam_paper (id,course_id,teacher_id,title,description,total_score,duration_minutes,status) SELECT 1004941,1004911,1003901,'合成演示·基础概念测验','预置汇总评分，供学情分析演示。',100,45,1 WHERE NOT EXISTS (SELECT 1 FROM t_exam_paper WHERE id=1004941);

INSERT INTO t_exam_session (id,class_id,paper_id,teacher_id,title,description,start_time,end_time,duration,status) SELECT 1004971,1004931,1004941,1003901,'合成演示·基础概念测验','合成演示评分','2026-11-03 09:00:00','2026-11-03 09:45:00',45,4 WHERE NOT EXISTS (SELECT 1 FROM t_exam_session WHERE id=1004971);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005000,1004971,1003902,'1003902','2026-11-03 09:40:00',65,65,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005000);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005000,1003902,1004931,1004911,1003901,'合成演示·基础概念测验','exam',1004971,65,100,'合成演示评分；用于趋势展示','2026-11-03 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005000);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005001,1004971,1004921,'1004921','2026-11-03 09:40:00',48,48,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005001);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005001,1004921,1004931,1004911,1003901,'合成演示·基础概念测验','exam',1004971,48,100,'合成演示评分；用于趋势展示','2026-11-03 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005001);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005002,1004971,1004922,'1004922','2026-11-03 09:40:00',42,42,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005002);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005002,1004922,1004931,1004911,1003901,'合成演示·基础概念测验','exam',1004971,42,100,'合成演示评分；用于趋势展示','2026-11-03 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005002);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005003,1004971,1004923,'1004923','2026-11-03 09:40:00',88,88,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005003);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005003,1004923,1004931,1004911,1003901,'合成演示·基础概念测验','exam',1004971,88,100,'合成演示评分；用于趋势展示','2026-11-03 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005003);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005004,1004971,1004924,'1004924','2026-11-03 09:40:00',92,92,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005004);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005004,1004924,1004931,1004911,1003901,'合成演示·基础概念测验','exam',1004971,92,100,'合成演示评分；用于趋势展示','2026-11-03 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005004);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005005,1004971,1004925,'1004925','2026-11-03 09:40:00',70,70,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005005);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005005,1004925,1004931,1004911,1003901,'合成演示·基础概念测验','exam',1004971,70,100,'合成演示评分；用于趋势展示','2026-11-03 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005005);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005006,1004971,1004926,'1004926','2026-11-03 09:40:00',60,60,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005006);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005006,1004926,1004931,1004911,1003901,'合成演示·基础概念测验','exam',1004971,60,100,'合成演示评分；用于趋势展示','2026-11-03 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005006);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005007,1004971,1004927,'1004927','2026-11-03 09:40:00',78,78,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005007);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005007,1004927,1004931,1004911,1003901,'合成演示·基础概念测验','exam',1004971,78,100,'合成演示评分；用于趋势展示','2026-11-03 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005007);

INSERT INTO t_exam_paper (id,course_id,teacher_id,title,description,total_score,duration_minutes,status) SELECT 1004942,1004911,1003901,'合成演示·梯度下降测验','预置汇总评分，供学情分析演示。',100,45,1 WHERE NOT EXISTS (SELECT 1 FROM t_exam_paper WHERE id=1004942);

INSERT INTO t_exam_session (id,class_id,paper_id,teacher_id,title,description,start_time,end_time,duration,status) SELECT 1004972,1004931,1004942,1003901,'合成演示·梯度下降测验','合成演示评分','2026-11-08 09:00:00','2026-11-08 09:45:00',45,4 WHERE NOT EXISTS (SELECT 1 FROM t_exam_session WHERE id=1004972);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005010,1004972,1003902,'1003902','2026-11-08 09:40:00',78,78,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005010);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005010,1003902,1004931,1004911,1003901,'合成演示·梯度下降测验','exam',1004972,78,100,'合成演示评分；用于趋势展示','2026-11-08 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005010);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005011,1004972,1004921,'1004921','2026-11-08 09:40:00',67,67,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005011);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005011,1004921,1004931,1004911,1003901,'合成演示·梯度下降测验','exam',1004972,67,100,'合成演示评分；用于趋势展示','2026-11-08 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005011);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005012,1004972,1004922,'1004922','2026-11-08 09:40:00',46,46,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005012);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005012,1004922,1004931,1004911,1003901,'合成演示·梯度下降测验','exam',1004972,46,100,'合成演示评分；用于趋势展示','2026-11-08 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005012);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005013,1004972,1004923,'1004923','2026-11-08 09:40:00',72,72,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005013);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005013,1004923,1004931,1004911,1003901,'合成演示·梯度下降测验','exam',1004972,72,100,'合成演示评分；用于趋势展示','2026-11-08 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005013);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005014,1004972,1004924,'1004924','2026-11-08 09:40:00',94,94,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005014);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005014,1004924,1004931,1004911,1003901,'合成演示·梯度下降测验','exam',1004972,94,100,'合成演示评分；用于趋势展示','2026-11-08 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005014);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005015,1004972,1004925,'1004925','2026-11-08 09:40:00',73,73,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005015);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005015,1004925,1004931,1004911,1003901,'合成演示·梯度下降测验','exam',1004972,73,100,'合成演示评分；用于趋势展示','2026-11-08 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005015);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005016,1004972,1004926,'1004926','2026-11-08 09:40:00',64,64,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005016);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005016,1004926,1004931,1004911,1003901,'合成演示·梯度下降测验','exam',1004972,64,100,'合成演示评分；用于趋势展示','2026-11-08 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005016);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005017,1004972,1004927,'1004927','2026-11-08 09:40:00',80,80,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005017);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005017,1004927,1004931,1004911,1003901,'合成演示·梯度下降测验','exam',1004972,80,100,'合成演示评分；用于趋势展示','2026-11-08 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005017);

INSERT INTO t_exam_paper (id,course_id,teacher_id,title,description,total_score,duration_minutes,status) SELECT 1004943,1004911,1003901,'合成演示·阶段综合测验','预置汇总评分，供学情分析演示。',100,45,1 WHERE NOT EXISTS (SELECT 1 FROM t_exam_paper WHERE id=1004943);

INSERT INTO t_exam_session (id,class_id,paper_id,teacher_id,title,description,start_time,end_time,duration,status) SELECT 1004973,1004931,1004943,1003901,'合成演示·阶段综合测验','合成演示评分','2026-11-13 09:00:00','2026-11-13 09:45:00',45,4 WHERE NOT EXISTS (SELECT 1 FROM t_exam_session WHERE id=1004973);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005020,1004973,1003902,'1003902','2026-11-13 09:40:00',86,86,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005020);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005020,1003902,1004931,1004911,1003901,'合成演示·阶段综合测验','exam',1004973,86,100,'合成演示评分；用于趋势展示','2026-11-13 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005020);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005021,1004973,1004921,'1004921','2026-11-13 09:40:00',82,82,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005021);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005021,1004921,1004931,1004911,1003901,'合成演示·阶段综合测验','exam',1004973,82,100,'合成演示评分；用于趋势展示','2026-11-13 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005021);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005022,1004973,1004922,'1004922','2026-11-13 09:40:00',51,51,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005022);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005022,1004922,1004931,1004911,1003901,'合成演示·阶段综合测验','exam',1004973,51,100,'合成演示评分；用于趋势展示','2026-11-13 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005022);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005023,1004973,1004923,'1004923','2026-11-13 09:40:00',55,55,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005023);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005023,1004923,1004931,1004911,1003901,'合成演示·阶段综合测验','exam',1004973,55,100,'合成演示评分；用于趋势展示','2026-11-13 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005023);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005024,1004973,1004924,'1004924','2026-11-13 09:40:00',96,96,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005024);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005024,1004924,1004931,1004911,1003901,'合成演示·阶段综合测验','exam',1004973,96,100,'合成演示评分；用于趋势展示','2026-11-13 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005024);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005025,1004973,1004925,'1004925','2026-11-13 09:40:00',75,75,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005025);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005025,1004925,1004931,1004911,1003901,'合成演示·阶段综合测验','exam',1004973,75,100,'合成演示评分；用于趋势展示','2026-11-13 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005025);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005026,1004973,1004926,'1004926','2026-11-13 09:40:00',68,68,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005026);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005026,1004926,1004931,1004911,1003901,'合成演示·阶段综合测验','exam',1004973,68,100,'合成演示评分；用于趋势展示','2026-11-13 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005026);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005027,1004973,1004927,'1004927','2026-11-13 09:40:00',83,83,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005027);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005027,1004927,1004931,1004911,1003901,'合成演示·阶段综合测验','exam',1004973,83,100,'合成演示评分；用于趋势展示','2026-11-13 09:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005027);

INSERT INTO t_homework_paper (id,course_id,teacher_id,title,description,total_score,status) SELECT 1004944,1004911,1003901,'合成演示·学习率应用作业','预置评分和提交状态',100,1 WHERE NOT EXISTS (SELECT 1 FROM t_homework_paper WHERE id=1004944);

INSERT INTO t_homework_session (id,class_id,paper_id,teacher_id,title,description,start_time,end_time,duration,status) SELECT 1004981,1004931,1004944,1003901,'合成演示·学习率应用作业','5份已评分、1份待批改、1人进行中、1人未提交。','2026-11-14 09:00:00','2026-11-16 23:00:00',0,3 WHERE NOT EXISTS (SELECT 1 FROM t_homework_session WHERE id=1004981);

INSERT INTO t_homework_record (id,homework_session_id,student_id,candidate_id,submit_time,total_score,status,comment) SELECT 1005040,1004981,1003902,'1003902','2026-11-15 18:00:00',88,2,'合成演示提交' WHERE NOT EXISTS (SELECT 1 FROM t_homework_record WHERE id=1005040);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005040,1003902,1004931,1004911,1003901,'合成演示·学习率应用作业','homework',1004981,88,100,'合成演示评分','2026-11-16 10:00:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005040);

INSERT INTO t_homework_record (id,homework_session_id,student_id,candidate_id,submit_time,total_score,status,comment) SELECT 1005041,1004981,1004921,'1004921','2026-11-15 18:00:00',85,2,'合成演示提交' WHERE NOT EXISTS (SELECT 1 FROM t_homework_record WHERE id=1005041);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005041,1004921,1004931,1004911,1003901,'合成演示·学习率应用作业','homework',1004981,85,100,'合成演示评分','2026-11-16 10:00:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005041);

INSERT INTO t_homework_record (id,homework_session_id,student_id,candidate_id,submit_time,total_score,status,comment) SELECT 1005042,1004981,1004922,'1004922','2026-11-15 18:00:00',53,2,'合成演示提交' WHERE NOT EXISTS (SELECT 1 FROM t_homework_record WHERE id=1005042);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005042,1004922,1004931,1004911,1003901,'合成演示·学习率应用作业','homework',1004981,53,100,'合成演示评分','2026-11-16 10:00:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005042);

INSERT INTO t_homework_record (id,homework_session_id,student_id,candidate_id,submit_time,total_score,status,comment) SELECT 1005043,1004981,1004923,'1004923','2026-11-15 18:00:00',58,2,'合成演示提交' WHERE NOT EXISTS (SELECT 1 FROM t_homework_record WHERE id=1005043);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005043,1004923,1004931,1004911,1003901,'合成演示·学习率应用作业','homework',1004981,58,100,'合成演示评分','2026-11-16 10:00:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005043);

INSERT INTO t_homework_record (id,homework_session_id,student_id,candidate_id,submit_time,total_score,status,comment) SELECT 1005044,1004981,1004924,'1004924','2026-11-15 18:00:00',97,2,'合成演示提交' WHERE NOT EXISTS (SELECT 1 FROM t_homework_record WHERE id=1005044);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005044,1004924,1004931,1004911,1003901,'合成演示·学习率应用作业','homework',1004981,97,100,'合成演示评分','2026-11-16 10:00:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005044);

INSERT INTO t_homework_record (id,homework_session_id,student_id,candidate_id,submit_time,total_score,status,comment) SELECT 1005045,1004981,1004925,'1004925','2026-11-15 18:00:00',NULL,1,'合成演示提交' WHERE NOT EXISTS (SELECT 1 FROM t_homework_record WHERE id=1005045);

INSERT INTO t_homework_record (id,homework_session_id,student_id,candidate_id,submit_time,total_score,status,comment) SELECT 1005046,1004981,1004926,'1004926',NULL,NULL,0,'合成演示提交' WHERE NOT EXISTS (SELECT 1 FROM t_homework_record WHERE id=1005046);


-- Second synthetic class for course-wide comparison.

INSERT INTO t_teaching_class (id,course_id,teacher_id,class_name,semester,description,status) SELECT 1004932,1004911,1003901,'学情分析演示二班（合成数据）','2026秋','课程全部教学班比较用合成数据',1 WHERE NOT EXISTS (SELECT 1 FROM t_teaching_class WHERE id=1004932);

INSERT INTO t_user(id,username,password,nickname,email,status) SELECT 1004928,'analysis_class2_01',password,'二班演示学生01','class2demo1@example.com',1 FROM t_user WHERE id=1003902 AND NOT EXISTS(SELECT 1 FROM t_user WHERE id=1004928);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1004928,1004928,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1004928);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004990,1004932,1004928,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004990);

INSERT INTO t_user(id,username,password,nickname,email,status) SELECT 1004929,'analysis_class2_02',password,'二班演示学生02','class2demo2@example.com',1 FROM t_user WHERE id=1003902 AND NOT EXISTS(SELECT 1 FROM t_user WHERE id=1004929);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1004929,1004929,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1004929);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004991,1004932,1004929,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004991);

INSERT INTO t_user(id,username,password,nickname,email,status) SELECT 1004930,'analysis_class2_03',password,'二班演示学生03','class2demo3@example.com',1 FROM t_user WHERE id=1003902 AND NOT EXISTS(SELECT 1 FROM t_user WHERE id=1004930);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1004930,1004930,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1004930);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004992,1004932,1004930,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004992);

INSERT INTO t_user(id,username,password,nickname,email,status) SELECT 1004931,'analysis_class2_04',password,'二班演示学生04','class2demo4@example.com',1 FROM t_user WHERE id=1003902 AND NOT EXISTS(SELECT 1 FROM t_user WHERE id=1004931);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1004931,1004931,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1004931);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004993,1004932,1004931,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004993);

INSERT INTO t_user(id,username,password,nickname,email,status) SELECT 1004932,'analysis_class2_05',password,'二班演示学生05','class2demo5@example.com',1 FROM t_user WHERE id=1003902 AND NOT EXISTS(SELECT 1 FROM t_user WHERE id=1004932);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1004932,1004932,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1004932);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004994,1004932,1004932,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004994);

INSERT INTO t_user(id,username,password,nickname,email,status) SELECT 1004933,'analysis_class2_06',password,'二班演示学生06','class2demo6@example.com',1 FROM t_user WHERE id=1003902 AND NOT EXISTS(SELECT 1 FROM t_user WHERE id=1004933);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1004933,1004933,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1004933);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004995,1004932,1004933,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004995);

INSERT INTO t_user(id,username,password,nickname,email,status) SELECT 1004934,'analysis_class2_07',password,'二班演示学生07','class2demo7@example.com',1 FROM t_user WHERE id=1003902 AND NOT EXISTS(SELECT 1 FROM t_user WHERE id=1004934);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1004934,1004934,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1004934);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004996,1004932,1004934,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004996);

INSERT INTO t_user(id,username,password,nickname,email,status) SELECT 1004935,'analysis_class2_08',password,'二班演示学生08','class2demo8@example.com',1 FROM t_user WHERE id=1003902 AND NOT EXISTS(SELECT 1 FROM t_user WHERE id=1004935);

INSERT INTO t_user_role (id,user_id,role_id) SELECT 1004935,1004935,1 WHERE NOT EXISTS (SELECT 1 FROM t_user_role WHERE id=1004935);

INSERT INTO t_class_student (id,class_id,student_id,status) SELECT 1004997,1004932,1004935,1 WHERE NOT EXISTS (SELECT 1 FROM t_class_student WHERE id=1004997);

INSERT INTO t_exam_paper (id,course_id,teacher_id,title,description,total_score,duration_minutes,status) SELECT 1004945,1004911,1003901,'二班合成演示·基础概念测验','合成演示汇总评分',100,45,1 WHERE NOT EXISTS (SELECT 1 FROM t_exam_paper WHERE id=1004945);

INSERT INTO t_exam_session (id,class_id,paper_id,teacher_id,title,start_time,end_time,duration,status) SELECT 1004974,1004932,1004945,1003901,'二班合成演示·基础概念测验','2026-11-03 10:40:00','2026-11-03 10:40:00',45,4 WHERE NOT EXISTS (SELECT 1 FROM t_exam_session WHERE id=1004974);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005100,1004974,1004928,'1004928','2026-11-03 10:40:00',60,60,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005100);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005100,1004928,1004932,1004911,1003901,'二班合成演示·基础概念测验','exam',1004974,60,100,'合成演示评分','2026-11-03 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005100);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005101,1004974,1004929,'1004929','2026-11-03 10:40:00',38,38,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005101);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005101,1004929,1004932,1004911,1003901,'二班合成演示·基础概念测验','exam',1004974,38,100,'合成演示评分','2026-11-03 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005101);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005102,1004974,1004930,'1004930','2026-11-03 10:40:00',80,80,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005102);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005102,1004930,1004932,1004911,1003901,'二班合成演示·基础概念测验','exam',1004974,80,100,'合成演示评分','2026-11-03 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005102);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005103,1004974,1004931,'1004931','2026-11-03 10:40:00',85,85,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005103);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005103,1004931,1004932,1004911,1003901,'二班合成演示·基础概念测验','exam',1004974,85,100,'合成演示评分','2026-11-03 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005103);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005104,1004974,1004932,'1004932','2026-11-03 10:40:00',55,55,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005104);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005104,1004932,1004932,1004911,1003901,'二班合成演示·基础概念测验','exam',1004974,55,100,'合成演示评分','2026-11-03 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005104);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005105,1004974,1004933,'1004933','2026-11-03 10:40:00',65,65,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005105);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005105,1004933,1004932,1004911,1003901,'二班合成演示·基础概念测验','exam',1004974,65,100,'合成演示评分','2026-11-03 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005105);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005106,1004974,1004934,'1004934','2026-11-03 10:40:00',72,72,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005106);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005106,1004934,1004932,1004911,1003901,'二班合成演示·基础概念测验','exam',1004974,72,100,'合成演示评分','2026-11-03 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005106);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005107,1004974,1004935,'1004935','2026-11-03 10:40:00',45,45,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005107);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005107,1004935,1004932,1004911,1003901,'二班合成演示·基础概念测验','exam',1004974,45,100,'合成演示评分','2026-11-03 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005107);

INSERT INTO t_exam_paper (id,course_id,teacher_id,title,description,total_score,duration_minutes,status) SELECT 1004946,1004911,1003901,'二班合成演示·梯度下降测验','合成演示汇总评分',100,45,1 WHERE NOT EXISTS (SELECT 1 FROM t_exam_paper WHERE id=1004946);

INSERT INTO t_exam_session (id,class_id,paper_id,teacher_id,title,start_time,end_time,duration,status) SELECT 1004975,1004932,1004946,1003901,'二班合成演示·梯度下降测验','2026-11-08 10:40:00','2026-11-08 10:40:00',45,4 WHERE NOT EXISTS (SELECT 1 FROM t_exam_session WHERE id=1004975);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005110,1004975,1004928,'1004928','2026-11-08 10:40:00',68,68,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005110);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005110,1004928,1004932,1004911,1003901,'二班合成演示·梯度下降测验','exam',1004975,68,100,'合成演示评分','2026-11-08 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005110);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005111,1004975,1004929,'1004929','2026-11-08 10:40:00',46,46,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005111);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005111,1004929,1004932,1004911,1003901,'二班合成演示·梯度下降测验','exam',1004975,46,100,'合成演示评分','2026-11-08 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005111);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005112,1004975,1004930,'1004930','2026-11-08 10:40:00',73,73,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005112);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005112,1004930,1004932,1004911,1003901,'二班合成演示·梯度下降测验','exam',1004975,73,100,'合成演示评分','2026-11-08 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005112);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005113,1004975,1004931,'1004931','2026-11-08 10:40:00',90,90,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005113);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005113,1004931,1004932,1004911,1003901,'二班合成演示·梯度下降测验','exam',1004975,90,100,'合成演示评分','2026-11-08 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005113);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005114,1004975,1004932,'1004932','2026-11-08 10:40:00',61,61,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005114);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005114,1004932,1004932,1004911,1003901,'二班合成演示·梯度下降测验','exam',1004975,61,100,'合成演示评分','2026-11-08 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005114);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005115,1004975,1004933,'1004933','2026-11-08 10:40:00',70,70,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005115);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005115,1004933,1004932,1004911,1003901,'二班合成演示·梯度下降测验','exam',1004975,70,100,'合成演示评分','2026-11-08 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005115);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005116,1004975,1004934,'1004934','2026-11-08 10:40:00',75,75,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005116);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005116,1004934,1004932,1004911,1003901,'二班合成演示·梯度下降测验','exam',1004975,75,100,'合成演示评分','2026-11-08 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005116);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005117,1004975,1004935,'1004935','2026-11-08 10:40:00',54,54,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005117);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005117,1004935,1004932,1004911,1003901,'二班合成演示·梯度下降测验','exam',1004975,54,100,'合成演示评分','2026-11-08 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005117);

INSERT INTO t_exam_paper (id,course_id,teacher_id,title,description,total_score,duration_minutes,status) SELECT 1004947,1004911,1003901,'二班合成演示·阶段综合测验','合成演示汇总评分',100,45,1 WHERE NOT EXISTS (SELECT 1 FROM t_exam_paper WHERE id=1004947);

INSERT INTO t_exam_session (id,class_id,paper_id,teacher_id,title,start_time,end_time,duration,status) SELECT 1004976,1004932,1004947,1003901,'二班合成演示·阶段综合测验','2026-11-13 10:40:00','2026-11-13 10:40:00',45,4 WHERE NOT EXISTS (SELECT 1 FROM t_exam_session WHERE id=1004976);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005120,1004976,1004928,'1004928','2026-11-13 10:40:00',75,75,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005120);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005120,1004928,1004932,1004911,1003901,'二班合成演示·阶段综合测验','exam',1004976,75,100,'合成演示评分','2026-11-13 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005120);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005121,1004976,1004929,'1004929','2026-11-13 10:40:00',55,55,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005121);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005121,1004929,1004932,1004911,1003901,'二班合成演示·阶段综合测验','exam',1004976,55,100,'合成演示评分','2026-11-13 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005121);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005122,1004976,1004930,'1004930','2026-11-13 10:40:00',65,65,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005122);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005122,1004930,1004932,1004911,1003901,'二班合成演示·阶段综合测验','exam',1004976,65,100,'合成演示评分','2026-11-13 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005122);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005123,1004976,1004931,'1004931','2026-11-13 10:40:00',94,94,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005123);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005123,1004931,1004932,1004911,1003901,'二班合成演示·阶段综合测验','exam',1004976,94,100,'合成演示评分','2026-11-13 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005123);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005124,1004976,1004932,'1004932','2026-11-13 10:40:00',67,67,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005124);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005124,1004932,1004932,1004911,1003901,'二班合成演示·阶段综合测验','exam',1004976,67,100,'合成演示评分','2026-11-13 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005124);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005125,1004976,1004933,'1004933','2026-11-13 10:40:00',72,72,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005125);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005125,1004933,1004932,1004911,1003901,'二班合成演示·阶段综合测验','exam',1004976,72,100,'合成演示评分','2026-11-13 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005125);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005126,1004976,1004934,'1004934','2026-11-13 10:40:00',80,80,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005126);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005126,1004934,1004932,1004911,1003901,'二班合成演示·阶段综合测验','exam',1004976,80,100,'合成演示评分','2026-11-13 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005126);

INSERT INTO t_exam_record (id,exam_session_id,student_id,candidate_id,submit_time,total_score,objective_score,subjective_score,status,comment) SELECT 1005127,1004976,1004935,'1004935','2026-11-13 10:40:00',62,62,0,2,'合成演示评分' WHERE NOT EXISTS (SELECT 1 FROM t_exam_record WHERE id=1005127);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005127,1004935,1004932,1004911,1003901,'二班合成演示·阶段综合测验','exam',1004976,62,100,'合成演示评分','2026-11-13 10:40:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005127);

INSERT INTO t_homework_paper (id,course_id,teacher_id,title,description,total_score,status) SELECT 1004948,1004911,1003901,'二班合成演示·学习率应用作业','合成演示汇总评分',100,1 WHERE NOT EXISTS (SELECT 1 FROM t_homework_paper WHERE id=1004948);

INSERT INTO t_homework_session (id,class_id,paper_id,teacher_id,title,start_time,end_time,duration,status) SELECT 1004982,1004932,1004948,1003901,'二班合成演示·学习率应用作业','2026-11-14 09:00:00','2026-11-16 23:00:00',0,3 WHERE NOT EXISTS (SELECT 1 FROM t_homework_session WHERE id=1004982);

INSERT INTO t_homework_record (id,homework_session_id,student_id,candidate_id,submit_time,total_score,status,comment) SELECT 1005140,1004982,1004928,'1004928','2026-11-15 20:00:00',78,2,'合成演示提交' WHERE NOT EXISTS (SELECT 1 FROM t_homework_record WHERE id=1005140);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005140,1004928,1004932,1004911,1003901,'二班合成演示·学习率应用作业','homework',1004982,78,100,'合成演示评分','2026-11-16 10:00:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005140);

INSERT INTO t_homework_record (id,homework_session_id,student_id,candidate_id,submit_time,total_score,status,comment) SELECT 1005141,1004982,1004929,'1004929','2026-11-15 20:00:00',58,2,'合成演示提交' WHERE NOT EXISTS (SELECT 1 FROM t_homework_record WHERE id=1005141);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005141,1004929,1004932,1004911,1003901,'二班合成演示·学习率应用作业','homework',1004982,58,100,'合成演示评分','2026-11-16 10:00:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005141);

INSERT INTO t_homework_record (id,homework_session_id,student_id,candidate_id,submit_time,total_score,status,comment) SELECT 1005142,1004982,1004930,'1004930','2026-11-15 20:00:00',63,2,'合成演示提交' WHERE NOT EXISTS (SELECT 1 FROM t_homework_record WHERE id=1005142);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005142,1004930,1004932,1004911,1003901,'二班合成演示·学习率应用作业','homework',1004982,63,100,'合成演示评分','2026-11-16 10:00:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005142);

INSERT INTO t_homework_record (id,homework_session_id,student_id,candidate_id,submit_time,total_score,status,comment) SELECT 1005143,1004982,1004931,'1004931','2026-11-15 20:00:00',95,2,'合成演示提交' WHERE NOT EXISTS (SELECT 1 FROM t_homework_record WHERE id=1005143);

INSERT INTO t_grade_record (id,student_id,class_id,course_id,teacher_id,grade_name,source_type,source_id,score,full_score,grade_comment,create_time) SELECT 1005143,1004931,1004932,1004911,1003901,'二班合成演示·学习率应用作业','homework',1004982,95,100,'合成演示评分','2026-11-16 10:00:00' WHERE NOT EXISTS (SELECT 1 FROM t_grade_record WHERE id=1005143);

INSERT INTO t_homework_record (id,homework_session_id,student_id,candidate_id,submit_time,total_score,status,comment) SELECT 1005144,1004982,1004932,'1004932','2026-11-15 20:00:00',NULL,1,'合成演示提交' WHERE NOT EXISTS (SELECT 1 FROM t_homework_record WHERE id=1005144);

INSERT INTO t_homework_record (id,homework_session_id,student_id,candidate_id,submit_time,total_score,status,comment) SELECT 1005145,1004982,1004933,'1004933','2026-11-15 20:00:00',NULL,1,'合成演示提交' WHERE NOT EXISTS (SELECT 1 FROM t_homework_record WHERE id=1005145);

INSERT INTO t_homework_record (id,homework_session_id,student_id,candidate_id,submit_time,total_score,status,comment) SELECT 1005146,1004982,1004934,'1004934',NULL,NULL,0,'合成演示提交' WHERE NOT EXISTS (SELECT 1 FROM t_homework_record WHERE id=1005146);
