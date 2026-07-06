/**
 * AI Academy 数据库数据生成器
 * 为每个表补充数据至 100+ 条
 * 用法: node generate_data.js > data_expansion.sql
 */

// ============ 基础数据配置 ============

const PWD = 'e10adc3949ba59abbe56e057f20f883e'; // MD5(123456), formatValue will add quotes

// 学生 ID 范围: 4-6, 11-20 = 12 students
const existingStudentIds = [4,5,6,11,12,13,14,15,16,17,18,19,20];
// 教师 ID 范围: 2,3,7,8,9,10 = 6 teachers
const existingTeacherIds = [2,3,7,8,9,10];
// 管理员: 1

// ============ 工具函数 ============

function esc(s) {
  if (s === null || s === undefined) return 'NULL';
  return "'" + String(s).replace(/'/g, "''") + "'";
}

function dtExpr(offsetDays, offsetHours=0, offsetMinutes=0) {
  if (offsetDays === 0 && offsetHours === 0 && offsetMinutes === 0) return 'NOW()';
  const parts = [];
  if (offsetDays !== 0) parts.push(`INTERVAL ${offsetDays} DAY`);
  if (offsetHours !== 0) parts.push(`INTERVAL ${offsetHours} HOUR`);
  if (offsetMinutes !== 0) parts.push(`INTERVAL ${offsetMinutes} MINUTE`);
  const op = (offsetDays + offsetHours + offsetMinutes) > 0 ? '+' : '-';
  // Handle negative by inverting
  const absParts = parts.map(p => p.replace(/^-/, ''));
  if (offsetDays + offsetHours + offsetMinutes > 0) {
    return `DATE_ADD(NOW(), ${absParts.join(', ')})`;
  } else {
    return `DATE_SUB(NOW(), ${absParts.map(p => p.replace('INTERVAL ', 'INTERVAL ')).join(', ')})`;
  }
}

function dateAdd(base, days, hours=0, mins=0) {
  if (days === 0 && hours === 0 && mins === 0) return base;
  const parts = [];
  if (days !== 0) parts.push(`INTERVAL ${days} DAY`);
  if (hours !== 0) parts.push(`INTERVAL ${hours} HOUR`);
  if (mins !== 0) parts.push(`INTERVAL ${mins} MINUTE`);
  return `DATE_ADD(${base}, ${parts.join(', ')})`;
}

function dateSub(base, days, hours=0, mins=0) {
  const parts = [];
  if (days !== 0) parts.push(`INTERVAL ${days} DAY`);
  if (hours !== 0) parts.push(`INTERVAL ${hours} HOUR`);
  if (mins !== 0) parts.push(`INTERVAL ${mins} MINUTE`);
  return `DATE_SUB(${base}, ${parts.join(', ')})`;
}

function randInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randFloat(min, max, decimals=2) {
  const val = Math.random() * (max - min) + min;
  return parseFloat(val.toFixed(decimals));
}

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function pickN(arr, n) {
  const shuffled = [...arr].sort(() => Math.random() - 0.5);
  return shuffled.slice(0, n);
}

function shuffle(arr) {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function formatValue(v) {
  if (v === null || v === undefined) return 'NULL';
  if (v && v.__isSQL) return v.value;
  if (typeof v === 'number') return String(v);
  return esc(v);
}

// SQL expression marker - value will be output as-is (no quoting)
function SQL(val) { return { __isSQL: true, value: val }; }

// ============ 批量 INSERT 生成器 ============

let outputLines = [];

function emitComment(msg) {
  outputLines.push(`\n-- ============================================`);
  outputLines.push(`-- ${msg}`);
  outputLines.push(`-- ============================================`);
}

function emitInsert(table, columns, rows, batchSize=50) {
  if (rows.length === 0) return;
  // Split into batches
  for (let i = 0; i < rows.length; i += batchSize) {
    const batch = rows.slice(i, i + batchSize);
    outputLines.push(`\nINSERT INTO ${table} (${columns}) VALUES`);
    const valueStrings = batch.map(row => {
      return '  (' + row.map(v => formatValue(v)).join(', ') + ')';
    });
    for (let j = 0; j < valueStrings.length; j++) {
      outputLines.push(valueStrings[j] + (j < valueStrings.length - 1 ? ',' : ';'));
    }
  }
}

// ============ 1. t_role ============
// Existing: 3 roles (student, teacher, admin). Max ID: 3
// Need 97+ more

const roleData = [
  ['助教', 'teaching_assistant'],
  ['教务员', 'academic_affairs'],
  ['督导员', 'supervisor'],
  ['教学秘书', 'teaching_secretary'],
  ['实验室管理员', 'lab_manager'],
  ['院系管理员', 'dept_admin'],
  ['课程负责人', 'course_director'],
  ['专业负责人', 'program_director'],
  ['学科带头人', 'discipline_leader'],
  ['实习导师', 'internship_advisor'],
  ['论文导师', 'thesis_advisor'],
  ['辅导员', 'counselor'],
  ['班主任', 'class_advisor'],
  ['教研室主任', 'teaching_research_head'],
  ['副院长', 'vice_dean'],
  ['院长', 'dean'],
  ['副校长', 'vice_president'],
  ['校长', 'president'],
  ['系统审计员', 'system_auditor'],
  ['内容审核员', 'content_moderator'],
  ['数据管理员', 'data_admin'],
  ['应用管理员', 'app_admin'],
  ['安全管理员', 'security_admin'],
  ['API用户', 'api_user'],
  ['访客', 'guest'],
  ['校友', 'alumni'],
  ['校外导师', 'external_advisor'],
  ['企业导师', 'corporate_mentor'],
  ['客座教授', 'visiting_professor'],
  ['荣誉教授', 'honorary_professor'],
  ['特聘教授', 'distinguished_professor'],
  ['博士后', 'postdoc'],
  ['博士生', 'phd_student'],
  ['硕士生', 'master_student'],
  ['本科生(一年级)', 'undergrad_1'],
  ['本科生(二年级)', 'undergrad_2'],
  ['本科生(三年级)', 'undergrad_3'],
  ['本科生(四年级)', 'undergrad_4'],
  ['留学生', 'international_student'],
  ['交换生', 'exchange_student'],
  ['预科生', 'foundation_student'],
  ['旁听生', 'audit_student'],
  ['在线学员', 'online_learner'],
  ['微专业学员', 'micro_major_learner'],
  ['培训学员', 'trainee'],
  ['出卷人', 'exam_setter'],
  ['阅卷人', 'exam_grader'],
  ['题库管理员', 'question_bank_admin'],
  ['排课管理员', 'scheduler'],
  ['选课管理员', 'enrollment_admin'],
  ['成绩管理员', 'grade_admin'],
  ['学籍管理员', 'student_status_admin'],
  ['招生管理员', 'admissions_admin'],
  ['财务管理员', 'finance_admin'],
  ['科研秘书', 'research_secretary'],
  ['项目管理员', 'project_admin'],
  ['设备管理员', 'equipment_admin'],
  ['图书管理员', 'librarian'],
  ['信息技术支持', 'it_support'],
  ['多媒体管理员', 'multimedia_admin'],
  ['网络管理员', 'network_admin'],
  ['平台运维', 'platform_ops'],
  ['课程设计师', 'instructional_designer'],
  ['教学评估员', 'teaching_evaluator'],
  ['教学质量监控', 'quality_monitor'],
  ['专业评估员', 'program_reviewer'],
  ['认证协调员', 'accreditation_coordinator'],
  ['学术委员会成员', 'academic_council_member'],
  ['学位委员会成员', 'degree_council_member'],
  ['教材评审员', 'textbook_reviewer'],
  ['实验室安全员', 'lab_safety_officer'],
  ['创新创业导师', 'innovation_mentor'],
  ['竞赛指导教师', 'competition_advisor'],
  ['社团指导教师', 'club_advisor'],
  ['心理辅导师', 'psychology_counselor'],
  ['就业指导师', 'career_counselor'],
  ['职业规划师', 'career_planner'],
  ['学术诚信管理员', 'academic_integrity_officer'],
  ['版权管理员', 'copyright_admin'],
  ['档案管理员', 'archive_admin'],
  ['会议室管理员', 'meeting_room_admin'],
  ['课程录制师', 'course_recorder'],
  ['直播教学师', 'live_broadcast_teacher'],
  ['虚拟仿真实验员', 'virtual_lab_tech'],
  ['AI训练师', 'ai_trainer'],
  ['数据分析师', 'data_analyst'],
  ['教育研究员', 'education_researcher'],
  ['教学质量评估专家', 'teaching_quality_expert'],
  ['国际交流专员', 'international_exchange_officer'],
  ['校企合作专员', 'industry_cooperation_officer'],
  ['实训基地管理员', 'training_base_admin'],
  ['校外实践导师', 'field_practice_advisor'],
  ['毕业设计指导教师', 'graduation_project_advisor'],
  ['学术讲座组织员', 'seminar_organizer'],
  ['会议管理员', 'conference_admin'],
  ['期刊编辑', 'journal_editor'],
  ['学报编委', 'journal_board_member'],
  ['学术道德委员会', 'ethics_committee_member'],
  ['学位论文评审员', 'thesis_reviewer'],
  ['答辩秘书', 'defense_secretary'],
];

console.error(`Generating ${roleData.length} roles (total with existing: ${3 + roleData.length})`);

// ============ 2. t_user ============
// Existing: 20 users. Max ID: 20
// Need 80+ more

const surnames = '赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜戚谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳丰鲍史唐费廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮卞齐康伍余元卜顾孟平黄和穆萧尹姚邵湛汪祁毛禹狄米贝明臧计伏成戴谈宋茅庞熊纪舒屈项祝董梁杜阮蓝闵席季麻强贾路娄危江童颜郭梅盛林刁钟徐邱骆高夏蔡田樊胡凌霍虞万支柯昝管卢莫经房裘缪干解应宗丁宣贲邓郁单杭洪包诸左石崔吉钮龚程嵇邢滑裴陆荣翁荀羊於惠甄曲家封芮羿储靳汲邴糜松井段富巫乌焦巴弓牧隗山谷车侯宓蓬全郗班仰秋仲伊宫宁仇栾暴甘钭厉戎祖武符刘景詹束龙叶幸司韶郜黎蓟薄印宿白怀蒲邰从鄂索咸籍赖卓蔺屠蒙池乔阴郁胥能苍双闻莘党翟谭贡劳逄姬申扶堵冉宰郦雍郤璩桑桂濮牛寿通边扈燕冀郏浦尚农温别庄晏柴瞿阎充慕连茹习宦艾鱼容向古易慎戈廖庾终暨居衡步都耿满弘匡国文寇广禄阙东欧殳沃利蔚越夔隆师巩厍聂晁勾敖融冷訾辛阚那简饶空曾沙须丰巢关蒯相查后荆红游竺权逯盖益桓公';
const maleGivenNames = '伟强磊军勇明杰浩鹏飞超波斌华宇志建国平海涛鑫龙威松峰毅刚俊云峰建华光辉宏亮成勇利剑文博智远展鹏佳琪思远昊天宇航奕辰宇轩浩宇晨曦铭泽梓睿';
const femaleGivenNames = '芳娟敏静华丽萍琳霞红梅雪燕莉莉萍荣玲萍香云凤莲萍秀英兰萍春霞桂英秀兰玉梅小红丽华美珍雪琴淑华素芬金花翠花秀珍彩霞春花桂香秀芳彩凤秀梅玉兰桂芳春梅秀云彩云玉珍秀荣素英桂珍彩珍';

// Generate 100 users (IDs: 21-120)
const newUsers = [];
const usedUsernames = new Set();

for (let i = 21; i <= 120; i++) {
  const isTeacher = i <= 60; // 40 teachers
  const isMale = Math.random() > 0.5;
  const surname = pick(surnames);
  const givenName = isMale ? pick(maleGivenNames) : pick(femaleGivenNames);
  const nickname = surname + givenName;
  let username;
  // Generate unique username
  do {
    if (isTeacher) {
      username = 't' + (i - 20) + '_' + surname.toLowerCase() + givenName.toLowerCase();
    } else {
      username = 's' + (i - 20) + '_' + surname.toLowerCase() + givenName.toLowerCase();
    }
  } while (usedUsernames.has(username));
  usedUsernames.add(username);

  const email = `${username}@cupk.edu.cn`;
  const phone = `1${randInt(30, 99)}${String(randInt(10000000, 99999999))}`;
  const status = Math.random() > 0.1 ? 1 : 0; // 10% disabled
  const role = isTeacher ? 2 : 1;
  newUsers.push({
    id: i,
    username,
    nickname,
    email,
    phone,
    status,
    role_id: role,
    isTeacher
  });
}

console.error(`Generated ${newUsers.length} new users`);

// ============ 3. t_user_role ============
// Existing: 20 records. Max ID: 20
// New records for new users + some extra role assignments

const newUserRoles = [];
let userRoleId = 21;
for (const u of newUsers) {
  newUserRoles.push([userRoleId++, u.id, u.role_id]);
  // Some teachers also have admin role
  if (u.isTeacher && Math.random() > 0.85) {
    newUserRoles.push([userRoleId++, u.id, 3]);
  }
  // Some students also have TA role (role 4 = teaching_assistant)
  if (!u.isTeacher && Math.random() > 0.92) {
    newUserRoles.push([userRoleId++, u.id, 4]);
  }
}
// Ensure 100+ records
while (newUserRoles.length < 100) {
  const u = pick(newUsers);
  const r = randInt(4, roleData.length + 3);
  if (!newUserRoles.some(x => x[1] === u.id && x[2] === r)) {
    newUserRoles.push([userRoleId++, u.id, r]);
  }
}

console.error(`Generated ${newUserRoles.length} user_role records`);

// Collect all student IDs and teacher IDs
const allStudentIds = [...existingStudentIds, ...newUsers.filter(u => !u.isTeacher).map(u => u.id)];
const allTeacherIds = [...existingTeacherIds, ...newUsers.filter(u => u.isTeacher).map(u => u.id)];
const allUserIds = [1, ...allTeacherIds, ...allStudentIds];

console.error(`Total students: ${allStudentIds.length}, Total teachers: ${allTeacherIds.length}`);

// ============ 4. t_course_category ============
// Existing: 10 categories. Max ID: 10
// Need 90+ more

const categoryNames = [
  ['编程语言', '各种编程语言的学习与对比课程'],
  ['数据结构', '线性表、树、图等数据结构课程'],
  ['计算机组成原理', '计算机硬件体系结构与工作原理'],
  ['编译原理', '编译程序构造与优化技术'],
  ['离散数学', '集合论、图论、数理逻辑等数学基础'],
  ['概率统计', '概率论与数理统计课程'],
  ['线性代数', '矩阵运算与线性空间课程'],
  ['高等数学', '微积分与数学分析方法'],
  ['大学物理', '力学、电磁学、热学等物理基础'],
  ['大学英语', '综合英语与听说读写课程'],
  ['思想政治', '马克思主义原理与思政课程'],
  ['体育健康', '大学体育与健康锻炼课程'],
  ['军事理论', '国防教育与军事理论基础'],
  ['创新创业', '创新创业教育与项目实践'],
  ['职业规划', '大学生职业发展与就业指导'],
  ['人文社科', '历史、哲学、文学等人文课程'],
  ['艺术审美', '美术、音乐、影视等艺术鉴赏课程'],
  ['专业导论', '各专业学科导论与认知课程'],
  ['工程伦理', '工程职业道德与伦理规范'],
  ['学术规范', '学术写作规范与科研诚信'],
  ['人工智能应用', 'AI在各个行业的应用案例'],
  ['机器学习', '经典机器学习算法与模型'],
  ['深度学习', '深度神经网络理论与框架实践'],
  ['自然语言处理', '文本分析与自然语言理解技术'],
  ['计算机视觉', '图像处理与视觉识别技术'],
  ['强化学习', '智能体决策与强化学习算法'],
  ['推荐系统', '推荐算法与个性化推荐架构'],
  ['知识图谱', '知识表示与图数据库技术'],
  ['数据挖掘', '海量数据模式发现与分析'],
  ['大数据技术', 'Hadoop/Spark等大数据处理框架'],
  ['前端框架', 'React/Vue/Angular等前端框架'],
  ['移动开发', 'Android/iOS/跨平台移动开发'],
  ['游戏开发', 'Unity/Unreal等游戏引擎开发'],
  ['嵌入式系统', '嵌入式硬件与软件开发'],
  ['物联网技术', 'IoT架构与传感网络技术'],
  ['区块链技术', '区块链原理与智能合约开发'],
  ['DevOps实践', '持续集成/持续部署与自动化运维'],
  ['微服务架构', '分布式微服务设计与治理'],
  ['容器技术', 'Docker/Kubernetes容器化技术'],
  ['软件测试', '自动化测试与质量保障'],
  ['接口测试', 'API测试与接口自动化框架'],
  ['性能测试', '性能基准测试与调优技术'],
  ['安全测试', '渗透测试与安全审计技术'],
  ['需求工程', '软件需求获取与分析建模'],
  ['系统分析', '系统分析与设计方法'],
  ['项目管理', '软件项目管理与敏捷实践'],
  ['Linux系统', 'Linux操作系统管理与运维'],
  ['网络工程', '网络规划设计与设备配置'],
  ['数据库管理', '数据库运维与性能优化'],
  ['数据仓库', '数据仓库设计与ETL开发'],
  ['数据可视化', '数据可视化工具与仪表板设计'],
  ['商务智能', 'BI分析与商业决策支持'],
  ['企业架构', '企业IT架构设计与治理'],
  ['IT服务管理', 'ITIL服务管理与运维规范'],
  ['信息检索', '搜索引擎原理与信息检索技术'],
  ['数字信号处理', '信号采样/变换与数字滤波器设计'],
  ['模式识别', '统计模式识别与分类器设计'],
  ['机器人学', '机器人运动学与智能控制'],
  ['计算机图形学', '图形渲染管线与三维建模'],
  ['人机交互', '用户界面设计与可用性工程'],
  ['计算理论', '计算模型与可计算性理论'],
  ['密码学', '加密算法与安全协议设计'],
  ['逆向工程', '软件逆向分析与安全防护'],
  ['信息隐藏', '数字水印与隐写技术'],
  ['社交网络分析', '网络科学与社会网络分析'],
  ['计算生物学', '生物信息学与计算基因组学'],
  ['量子计算', '量子比特与量子算法基础'],
  ['边缘计算', '边缘节点计算与雾计算技术'],
  ['AR/VR技术', '增强现实与虚拟现实开发'],
  ['数字孪生', '数字孪生建模与仿真技术'],
  ['低代码开发', '低代码平台与应用搭建'],
  ['RPA自动化', '机器人流程自动化技术'],
  ['智能客服', '对话系统与智能问答技术'],
  ['语音识别', '语音信号处理与识别技术'],
  ['推荐广告', '计算广告与点击率预估'],
  ['风控系统', '金融风控与反欺诈技术'],
  ['量化交易', '量化投资策略与回测系统'],
  ['金融科技', '金融科技创新与监管科技'],
  ['智慧医疗', 'AI辅助诊断与医疗信息化'],
  ['智慧教育', '教育科技与自适应学习系统'],
  ['智慧城市', '城市计算与智能交通系统'],
  ['智慧农业', '精准农业与农业物联网'],
  ['能源互联网', '能源系统数字化与智能电网'],
  ['工业互联网', '工业4.0与智能制造技术'],
  ['数字营销', '数字营销技术与用户增长'],
  ['电子商务', '电商平台架构与推荐技术'],
  ['社交网络', '社交平台设计与推荐算法'],
  ['即时通讯', 'IM系统设计与消息协议'],
  ['视频直播', '直播系统架构与CDN技术'],
  ['Web3技术', '去中心化应用与NFT技术'],
  ['隐私计算', '联邦学习与多方安全计算'],
  ['图计算', '图数据库与图计算引擎'],
  ['Serverless', '无服务器架构与函数计算'],
  ['混沌工程', '分布式系统弹性与混沌实验'],
  ['可观测性', '监控/日志/链路追踪技术'],
  ['AIOps', '智能运维与异常检测技术'],
  ['平台工程', '内部开发者平台建设实践'],
];

console.error(`Generated ${categoryNames.length} categories (total: ${10 + categoryNames.length})`);

// ============ 5. t_course ============
// Existing: 13 courses. Max ID: 13
// Need 87+ more

const courseTemplates = [
  { cat: 1, title: 'C语言程序设计', desc: '学习C语言的基本语法、指针、内存管理和文件操作，为系统编程打下坚实基础。', diff: 'beginner', rating: 4.6 },
  { cat: 1, title: 'C++面向对象编程', desc: '深入掌握C++的类、继承、多态、模板和STL标准模板库。', diff: 'intermediate', rating: 4.5 },
  { cat: 1, title: 'C#与.NET开发', desc: '学习C#语言特性和.NET框架下的Windows应用开发。', diff: 'intermediate', rating: 4.3 },
  { cat: 1, title: 'Go语言开发实战', desc: '掌握Go语言并发编程、网络编程和微服务开发的核心技能。', diff: 'intermediate', rating: 4.7 },
  { cat: 1, title: 'Rust系统编程', desc: '学习Rust语言的所有权系统、生命周期和安全并发编程模型。', diff: 'advanced', rating: 4.8 },
  { cat: 1, title: 'TypeScript编程进阶', desc: '深入学习TypeScript类型系统、泛型和装饰器等高级特性。', diff: 'intermediate', rating: 4.4 },
  { cat: 1, title: 'Kotlin现代开发', desc: '使用Kotlin语言进行Android开发和后端服务构建。', diff: 'intermediate', rating: 4.5 },
  { cat: 2, title: 'JavaScript核心编程', desc: '掌握JavaScript语言核心机制、异步编程和ES6+新特性。', diff: 'beginner', rating: 4.5 },
  { cat: 2, title: 'React框架实战', desc: '学习React组件化开发、Hooks状态管理和Next.js服务端渲染。', diff: 'intermediate', rating: 4.7 },
  { cat: 2, title: 'HTML5与CSS3精粹', desc: '掌握现代Web布局技术、Flex/Grid和CSS动画效果。', diff: 'beginner', rating: 4.3 },
  { cat: 2, title: '前端工程化实践', desc: '学习Webpack/Vite构建工具、ESLint规范和前端自动化测试。', diff: 'advanced', rating: 4.6 },
  { cat: 2, title: '小程序开发实战', desc: '掌握微信/支付宝小程序的开发框架、组件和API使用。', diff: 'intermediate', rating: 4.4 },
  { cat: 2, title: 'Node.js服务端开发', desc: '学习Node.js事件驱动模型、Express/Koa框架和数据库集成。', diff: 'intermediate', rating: 4.5 },
  { cat: 3, title: 'Redis缓存与高并发', desc: '掌握Redis核心数据结构、持久化策略和分布式缓存方案。', diff: 'intermediate', rating: 4.6 },
  { cat: 3, title: 'MongoDB非关系型数据库', desc: '学习MongoDB文档模型、聚合管道和分片集群搭建。', diff: 'intermediate', rating: 4.3 },
  { cat: 3, title: 'Elasticsearch搜索引擎', desc: '掌握ES倒排索引、查询DSL和日志分析平台搭建。', diff: 'advanced', rating: 4.5 },
  { cat: 3, title: 'SQL优化与调优实践', desc: '深入理解查询计划、索引优化和SQL性能调优方法论。', diff: 'advanced', rating: 4.7 },
  { cat: 4, title: '算法分析与设计', desc: '学习分治、动态规划、贪心等经典算法设计与分析方法。', diff: 'advanced', rating: 4.6 },
  { cat: 4, title: '竞赛算法专题', desc: '面向ACM/ICPC竞赛算法训练，涵盖图论、数论和计算几何。', diff: 'advanced', rating: 4.8 },
  { cat: 4, title: 'LeetCode刷题精讲', desc: '精选高频面试题，系统讲解解题思路和算法优化技巧。', diff: 'intermediate', rating: 4.9 },
  { cat: 5, title: '专业英语阅读与翻译', desc: '提升计算机专业英文文献阅读和学术翻译能力。', diff: 'intermediate', rating: 4.2 },
  { cat: 5, title: '雅思托福备考训练', desc: '系统训练雅思/托福听说读写各项技能与应试策略。', diff: 'intermediate', rating: 4.1 },
  { cat: 5, title: '英语口语交际训练', desc: '通过情景对话和小组讨论提升英语口语表达能力。', diff: 'beginner', rating: 4.0 },
  { cat: 6, title: '机器学习实战', desc: '使用Scikit-learn实现经典机器学习算法的完整项目流程。', diff: 'intermediate', rating: 4.7 },
  { cat: 6, title: '深度学习与神经网络', desc: '使用PyTorch实现CNN/RNN/Transformer等深度学习模型。', diff: 'advanced', rating: 4.8 },
  { cat: 6, title: '计算机视觉：理论与应用', desc: '学习图像分类、目标检测和语义分割等视觉核心技术。', diff: 'advanced', rating: 4.6 },
  { cat: 6, title: '自然语言处理实战', desc: '掌握文本分类、命名实体识别和机器翻译等NLP任务。', diff: 'advanced', rating: 4.5 },
  { cat: 6, title: '强化学习入门', desc: '学习马尔可夫决策过程、Q-Learning和深度强化学习算法。', diff: 'advanced', rating: 4.4 },
  { cat: 7, title: 'Linux系统管理与运维', desc: '掌握Linux常用命令、Shell脚本和系统监控工具。', diff: 'intermediate', rating: 4.5 },
  { cat: 7, title: 'Windows Server管理', desc: '学习AD域控、组策略和Hyper-V虚拟化管理。', diff: 'intermediate', rating: 4.0 },
  { cat: 8, title: '设计模式与架构实践', desc: '深入理解23种设计模式及其在大型项目中的应用。', diff: 'advanced', rating: 4.6 },
  { cat: 8, title: 'UML建模与系统设计', desc: '掌握UML2.0各种图形的绘制方法和系统建模工具。', diff: 'intermediate', rating: 4.3 },
  { cat: 8, title: '敏捷开发与Scrum实践', desc: '学习Scrum框架、站会、Sprint计划和回顾会议等实践。', diff: 'intermediate', rating: 4.4 },
  { cat: 9, title: 'Web安全攻防实战', desc: '掌握SQL注入、XSS、文件上传等常见漏洞的利用与防御。', diff: 'advanced', rating: 4.7 },
  { cat: 9, title: '渗透测试与Metasploit', desc: '学习信息收集、漏洞扫描和渗透测试的完整方法论。', diff: 'advanced', rating: 4.6 },
  { cat: 9, title: '代码审计与安全开发', desc: '培养安全编码习惯，学习Java/Python代码审计技术。', diff: 'advanced', rating: 4.5 },
  { cat: 9, title: 'CTF竞赛入门与进阶', desc: '系统训练CTF竞赛的Web/逆向/PWN/Crypto/ Misc等方向技能。', diff: 'intermediate', rating: 4.8 },
  { cat: 10, title: 'Docker与Kubernetes实战', desc: '掌握容器化部署和K8s编排调度在生产环境的应用。', diff: 'advanced', rating: 4.7 },
  { cat: 10, title: 'AWS云服务架构', desc: '学习AWS核心服务EC2/S3/Lambda/RDS的架构设计。', diff: 'advanced', rating: 4.5 },
  { cat: 10, title: '阿里云ACP认证课程', desc: '系统学习阿里云核心产品与解决方案架构设计。', diff: 'intermediate', rating: 4.3 },
  { cat: 10, title: 'TensorFlow与Keras实战', desc: '使用TensorFlow2.x框架搭建和训练深度学习模型。', diff: 'intermediate', rating: 4.5 },
  { cat: 11, title: '高等数学A（上）', desc: '函数极限、导数与微分、不定积分与定积分。', diff: 'intermediate', rating: 4.4 },
  { cat: 11, title: '高等数学A（下）', desc: '多元函数微积分、重积分、曲线积分与曲面积分。', diff: 'intermediate', rating: 4.3 },
  { cat: 11, title: '线性代数与解析几何', desc: '矩阵运算、向量空间、特征值和二次型。', diff: 'intermediate', rating: 4.5 },
  { cat: 11, title: '概率论与数理统计', desc: '随机变量、参数估计、假设检验和回归分析。', diff: 'intermediate', rating: 4.4 },
  { cat: 12, title: '大学物理A（上）', desc: '力学、振动波动和热力学基础。', diff: 'intermediate', rating: 4.2 },
  { cat: 12, title: '大学物理A（下）', desc: '电磁学、光学和近代物理基础。', diff: 'intermediate', rating: 4.1 },
  { cat: 13, title: '思想道德与法治', desc: '思想道德修养与法治教育基础课程。', diff: 'beginner', rating: 4.0 },
  { cat: 13, title: '中国近现代史纲要', desc: '中国近现代重大历史事件与国情教育。', diff: 'beginner', rating: 4.1 },
  { cat: 13, title: '马克思主义基本原理', desc: '马克思主义哲学、政治经济学和科学社会主义。', diff: 'intermediate', rating: 4.0 },
  { cat: 14, title: '大学体育（篮球）', desc: '篮球运动基本技战术与裁判法。', diff: 'beginner', rating: 4.5 },
  { cat: 14, title: '大学体育（羽毛球）', desc: '羽毛球基本技术和比赛规则。', diff: 'beginner', rating: 4.4 },
  { cat: 14, title: '大学体育（游泳）', desc: '蛙泳/自由泳基本技术和水上安全知识。', diff: 'beginner', rating: 4.3 },
  { cat: 15, title: '大学生创新创业基础', desc: '创新思维培养与创业计划书撰写。', diff: 'beginner', rating: 4.2 },
  { cat: 15, title: '商业模式与市场分析', desc: '商业模式画布、市场调研与竞品分析方法。', diff: 'intermediate', rating: 4.3 },
  { cat: 16, title: '大学生职业发展规划', desc: '自我认知、职业探索与职业生涯规划方法。', diff: 'beginner', rating: 4.1 },
  { cat: 16, title: '简历与面试技巧', desc: '简历撰写、面试准备与职场沟通技巧训练。', diff: 'beginner', rating: 4.4 },
  { cat: 17, title: '中国文化概论', desc: '中国传统文化精髓与现代价值传承。', diff: 'beginner', rating: 4.3 },
  { cat: 17, title: '批判性思维与逻辑', desc: '逻辑推理、论证分析和批判性思维训练。', diff: 'intermediate', rating: 4.5 },
  { cat: 18, title: '影视鉴赏与评论', desc: '中外经典影视作品分析与鉴赏方法。', diff: 'beginner', rating: 4.2 },
  { cat: 18, title: '音乐基础与欣赏', desc: '基础乐理知识、音乐鉴赏与经典作品分析。', diff: 'beginner', rating: 4.1 },
  { cat: 19, title: '计算机专业导论', desc: '计算机学科知识体系与各专业方向介绍。', diff: 'beginner', rating: 4.3 },
  { cat: 19, title: '软件工程专业导论', desc: '软件工程学科范畴、核心知识和职业前景。', diff: 'beginner', rating: 4.2 },
  { cat: 19, title: '数据科学导论', desc: '数据科学方法论、工具链和典型应用场景。', diff: 'beginner', rating: 4.4 },
  { cat: 20, title: '工程伦理与职业道德', desc: '工程实践中的伦理困境与工程师职业道德规范。', diff: 'intermediate', rating: 4.1 },
  { cat: 20, title: '信息技术伦理', desc: '隐私保护、算法公平与人工智能伦理问题探讨。', diff: 'intermediate', rating: 4.3 },
  { cat: 21, title: '学术论文写作指导', desc: '论文选题、文献综述、写作规范与发表流程。', diff: 'intermediate', rating: 4.4 },
  { cat: 21, title: '科研方法与实验设计', desc: '科学方法论、实验设计与数据分析基础。', diff: 'intermediate', rating: 4.2 },
  { cat: 22, title: 'Python数据分析', desc: '使用Pandas/NumPy/Matplotlib进行数据分析与可视化。', diff: 'intermediate', rating: 4.6 },
  { cat: 22, title: 'Tableau数据可视化', desc: '学习Tableau仪表板设计、故事讲述与数据洞察。', diff: 'intermediate', rating: 4.3 },
  { cat: 23, title: '数据科学入门', desc: '数据科学全流程：数据采集、清洗、分析和建模。', diff: 'beginner', rating: 4.5 },
  { cat: 23, title: '大数据分析技术', desc: '使用Hadoop/Spark进行大规模数据处理与分析。', diff: 'advanced', rating: 4.4 },
  { cat: 24, title: 'Android应用开发', desc: '基于Kotlin的Android原生应用开发与Material Design设计。', diff: 'intermediate', rating: 4.3 },
  { cat: 24, title: 'iOS应用开发(Swift)', desc: '学习Swift语言和iOS应用开发全流程。', diff: 'intermediate', rating: 4.4 },
  { cat: 24, title: 'Flutter跨平台开发', desc: '使用Dart语言和Flutter框架构建跨平台移动应用。', diff: 'intermediate', rating: 4.5 },
  { cat: 25, title: 'Unity游戏开发实战', desc: '学习Unity引擎使用和2D/3D游戏开发完整流程。', diff: 'intermediate', rating: 4.5 },
  { cat: 25, title: '游戏设计原理', desc: '游戏玩法设计、关卡策划与用户体验优化。', diff: 'beginner', rating: 4.2 },
  { cat: 26, title: '嵌入式Linux驱动开发', desc: 'Linux内核模块编程、字符设备驱动和设备树配置。', diff: 'advanced', rating: 4.6 },
  { cat: 26, title: 'STM32单片机开发', desc: '基于STM32的嵌入式系统开发与RTOS应用。', diff: 'intermediate', rating: 4.4 },
  { cat: 27, title: 'RFID与物联网感知技术', desc: 'RFID原理、传感器网络和物联网通信协议。', diff: 'intermediate', rating: 4.2 },
  { cat: 27, title: 'NB-IoT与LoRa通信技术', desc: '低功耗广域网技术和物联网行业应用方案。', diff: 'advanced', rating: 4.1 },
  { cat: 28, title: '区块链原理与开发', desc: '以太坊智能合约开发、Solidity语言和DApp构建。', diff: 'advanced', rating: 4.3 },
  { cat: 28, title: 'Hyperledger Fabric实战', desc: '联盟链架构设计和企业级区块链应用开发。', diff: 'advanced', rating: 4.2 },
  { cat: 29, title: 'Jenkins CI/CD流水线', desc: '使用Jenkins/GitLab CI构建自动化构建和部署流水线。', diff: 'intermediate', rating: 4.5 },
  { cat: 29, title: 'Docker Compose与Swarm', desc: '学习Docker Compose多容器编排和Swarm集群管理。', diff: 'intermediate', rating: 4.4 },
  { cat: 30, title: 'Spring Cloud微服务实战', desc: '使用Spring Cloud全家桶构建微服务架构系统。', diff: 'advanced', rating: 4.7 },
  { cat: 30, title: '服务网格Istio入门', desc: '学习Istio服务网格的流量管理和安全策略配置。', diff: 'advanced', rating: 4.3 },
  { cat: 31, title: '软件测试基础', desc: '黑盒/白盒测试方法、测试用例设计和缺陷管理。', diff: 'beginner', rating: 4.3 },
  { cat: 31, title: 'Selenium自动化测试', desc: '使用Selenium WebDriver进行Web自动化测试。', diff: 'intermediate', rating: 4.4 },
  { cat: 32, title: 'Postman接口测试', desc: 'API接口测试方法与Postman自动化测试脚本编写。', diff: 'beginner', rating: 4.5 },
  { cat: 32, title: 'JMeter性能测试', desc: '使用JMeter进行Web应用性能测试和负载测试。', diff: 'intermediate', rating: 4.3 },
  { cat: 33, title: 'Kali Linux渗透测试', desc: '使用Kali Linux工具集进行渗透测试和安全评估。', diff: 'advanced', rating: 4.6 },
  { cat: 33, title: 'Burp Suite Web安全测试', desc: '使用Burp Suite进行Web应用安全漏洞扫描和分析。', diff: 'advanced', rating: 4.5 },
  { cat: 34, title: '软件项目管理', desc: '项目计划、进度管理、风险控制和团队协作方法。', diff: 'intermediate', rating: 4.3 },
  { cat: 34, title: '敏捷项目管理', desc: 'Scrum/Kanban敏捷方法在项目管理中的实践应用。', diff: 'intermediate', rating: 4.4 },
];

// Filter to unique titles
const usedTitles = new Set();
const uniqueCourses = courseTemplates.filter(c => {
  if (usedTitles.has(c.title)) return false;
  usedTitles.add(c.title);
  return true;
});

console.error(`Generated ${uniqueCourses.length} courses`);

// ============ (Continue with more data generation) ============

// For the output SQL, we need to write in topological order.
// Let me build the complete output.

// ============ OUTPUT GENERATION ============

outputLines.push(`-- ============================================`);
outputLines.push(`-- AI Academy 数据库扩展数据`);
outputLines.push(`-- 生成时间: ${new Date().toISOString()}`);
outputLines.push(`-- 说明: 为每个表补充数据至 100+ 条`);
outputLines.push(`-- ============================================`);
outputLines.push(``);
outputLines.push(`USE ai_academy;`);
outputLines.push(``);

// ===== 1. t_role (新增角色) =====
emitComment('1. 角色表扩展 (t_role)');
const roleCols = 'role_name, role_code';
const roleRows = roleData.map((r, i) => [r[0], r[1]]);
emitInsert('t_role', roleCols, roleRows);

// ===== 2. t_user (新增用户) =====
emitComment('2. 用户表扩展 (t_user)');
const userCols = 'username, password, nickname, email, phone, status';
const userRows = newUsers.map(u => [u.username, PWD, u.nickname, u.email, u.phone, u.status]);
emitInsert('t_user', userCols, userRows);

// ===== 3. t_user_role (新增用户角色) =====
emitComment('3. 用户角色表扩展 (t_user_role)');
emitInsert('t_user_role', 'user_id, role_id', newUserRoles.map(r => [r[1], r[2]]));

// ===== 4. t_course_category (新增课程分类) =====
emitComment('4. 课程分类表扩展 (t_course_category)');
const catRows = categoryNames.map((c, i) => [c[0], c[1], 11 + i]);
emitInsert('t_course_category', 'name, description, sort_order', catRows);

// ===== 5. t_course (新增课程) =====
emitComment('5. 课程表扩展 (t_course)');
const courseCols = 'category_id, teacher_id, title, description, cover, cover_url, teacher, difficulty, rating, student_count, view_count, status';
const tNames = ['赵老师','孙老师','周老师','吴老师','郑老师','王老师','冯老师','韩老师','曹老师','徐老师','黄老师','林老师','杨老师','张老师','刘老师','陈老师','马老师','高老师','梁老师','宋老师','唐老师','郭老师','何老师','罗老师','谢老师','钟老师','邱老师','廖老师','邹老师','熊老师'];
const totalCategories = 10 + categoryNames.length;

const courseRows = uniqueCourses.map((c, idx) => {
  const catId = c.cat <= 10 ? c.cat : randInt(11, totalCategories);
  const tId = pick(allTeacherIds);
  const tName = tNames[randInt(0, tNames.length - 1)];
  const studentCount = randInt(20, 300);
  const viewCount = studentCount + randInt(50, 500);
  const status = Math.random() > 0.15 ? 1 : (Math.random() > 0.5 ? 0 : 2);
  const cover = `/uploads/course/course_${14 + idx}.jpg`;
  return [catId, tId, c.title, c.desc, cover, cover, tName, c.diff, c.rating, studentCount, viewCount, status];
});
emitInsert('t_course', courseCols, courseRows);

// ===== 6. t_chapter (新增章节) =====
emitComment('6. 章节表扩展 (t_chapter)');
const totalCourses = 13 + uniqueCourses.length;
const chapterTemplates = [
  '课程导论与概述', '基础知识', '核心概念详解', '进阶应用', '高级主题',
  '实践案例分析', '项目实战（一）', '项目实战（二）', '综合复习', '课程考核',
  '新技术展望', '学术前沿探讨', '实验操作指导', '小组讨论', '作业讲评',
  '知识点梳理', '重难点突破', '常见错误分析', '能力拓展', '综合测评'
];

const chapterRows = [];
let chapterSort = {};
for (let cid = 14; cid <= totalCourses; cid++) {
  const numChapters = randInt(3, 6);
  for (let j = 0; j < numChapters; j++) {
    const title = pick(chapterTemplates);
    const desc = `课程ID ${cid} 的第 ${j + 1} 章：${title}`;
    const key = `${cid}-${title}`;
    if (!chapterSort[key]) chapterSort[key] = j + 1;
    // Add chapters in topological order (will be sorted later by course)
    chapterRows.push([cid, title, desc, j + 1]);
  }
}
// Shuffle to make it more interesting
const shuffledChapters = shuffle(chapterRows).sort((a, b) => a[0] - b[0] || a[3] - b[3]);
emitInsert('t_chapter', 'course_id, title, description, sort_order', shuffledChapters);

// Calculate total chapters for FK reference
const totalChapters = 27 + shuffledChapters.length;
console.error(`Generated ${shuffledChapters.length} chapters (total: ${totalChapters})`);

// ===== 7. t_lesson (新增课时) =====
emitComment('7. 课时表扩展 (t_lesson)');
const lessonTitles = [
  ['课程介绍与学习路线', 'article'],
  ['开发环境搭建指南', 'article'],
  ['Hello World程序解析', 'video_link'],
  ['基本语法规则详解', 'article'],
  ['变量与常量定义', 'article'],
  ['运算符与表达式', 'article'],
  ['条件判断语句', 'article'],
  ['循环结构详解', 'article'],
  ['数组与集合操作', 'article'],
  ['函数定义与调用', 'video_link'],
  ['参数传递机制', 'article'],
  ['面向对象编程思想', 'video_link'],
  ['类与对象的定义', 'article'],
  ['继承与多态', 'video_link'],
  ['抽象类与接口', 'article'],
  ['异常处理机制', 'article'],
  ['文件读写操作', 'article'],
  ['序列化与反序列化', 'article'],
  ['泛型编程介绍', 'article'],
  ['集合框架详解', 'article'],
  ['数据结构实现', 'article'],
  ['算法复杂度分析', 'article'],
  ['排序算法实现', 'video_link'],
  ['搜索算法详解', 'video_link'],
  ['递归算法实践', 'article'],
  ['动态规划入门', 'video_link'],
  ['链表操作实现', 'article'],
  ['栈与队列应用', 'article'],
  ['二叉树遍历', 'video_link'],
  ['图的表示与遍历', 'video_link'],
  ['哈希表原理', 'article'],
  ['数据库连接操作', 'video_link'],
  ['SQL查询优化', 'article'],
  ['索引原理与使用', 'article'],
  ['事务与并发控制', 'article'],
  ['缓存技术应用', 'video_link'],
  ['消息队列实践', 'article'],
  ['单元测试编写', 'article'],
  ['集成测试方法', 'article'],
  ['代码审查实践', 'article'],
  ['API设计规范', 'article'],
  ['接口文档编写', 'article'],
  ['版本控制(Git)', 'article'],
  ['持续集成入门', 'video_link'],
  ['性能优化技巧', 'article'],
  ['安全编码规范', 'article'],
  ['项目部署流程', 'video_link'],
  ['容器化部署实践', 'video_link'],
  ['监控与日志', 'article'],
  ['微服务架构设计', 'video_link'],
];

const lessonRows = [];
// Generate lessons for each course based on its chapters
// We need to know chapter IDs. Chapters for courses 14+ will have IDs 28+.
// Let me estimate: each course has 3-6 chapters, so for 87 courses, ~400 chapters
// Chapter IDs will be roughly 28 to 28+shuffledChapters.length

// For simplicity, let's distribute lessons across chapters in batches
const chaptersPerCourse = {};
for (const ch of shuffledChapters) {
  if (!chaptersPerCourse[ch[0]]) chaptersPerCourse[ch[0]] = [];
  // We don't have the actual chapter IDs yet, but they're sequential starting from 28
}
// Since we don't know exact chapter IDs, let's regenerate with tracking
// Actually the issue is we're generating data without knowing actual DB assigned IDs.
// Since AUTO_INCREMENT will assign them, in the INSERT we should NOT specify ID.
// But for FK references between tables, we need to reference the correct IDs.

// APPROACH: Generate chapter IDs sequentially as they'll be auto-assigned
let nextChapterId = 28; // After existing 27 chapters
const actualChapterRows = [];
let chIdx = 0;
const courseChapterStartIds = {};

for (let cid = 14; cid <= totalCourses; cid++) {
  const numChapters = randInt(3, 6);
  const startId = nextChapterId;
  courseChapterStartIds[cid] = startId;
  for (let j = 0; j < numChapters; j++) {
    const title = pick(chapterTemplates);
    const desc = `${title} - 课程ID ${cid} 第 ${j + 1} 章`;
    actualChapterRows.push([cid, title, desc, j + 1]);
    chapterRows.push({ id: nextChapterId, course_id: cid, sort_order: j + 1 });
    nextChapterId++;
  }
}

// Now generate lessons based on actual chapter IDs
const actualLessonRows = [];
let nextLessonId = 45; // After existing 44 lessons
for (let cid = 14; cid <= totalCourses; cid++) {
  const startChId = courseChapterStartIds[cid];
  const numCh = randInt(3, 6);
  for (let j = 0; j < numCh; j++) {
    const chId = startChId + j;
    const numLessons = randInt(1, 3);
    for (let k = 0; k < numLessons; k++) {
      const lt = pick(lessonTitles);
      const title = `${lt[0]}${randInt(1, 100)}`;
      const resType = lt[1];
      const resUrl = resType === 'video_link' ? `https://example.com/video/course${cid}_${nextLessonId}` : 'NULL';
      const videoUrl = resType === 'video_link' ? resUrl : 'NULL';
      const content = `${title}的课程内容，涵盖核心知识点讲解和实践操作指导。`;
      const duration = randInt(600, 3600);
      const viewCount = randInt(50, 800);
      actualLessonRows.push([chId, `${title}`, resType, resUrl === 'NULL' ? null : resUrl, videoUrl === 'NULL' ? null : videoUrl, content, duration, k + 1, viewCount]);
      nextLessonId++;
    }
  }
}

// Output chapters and lessons with corrected data
// Re-build chapterRows for actual output
emitComment('6a. 章节表扩展 (t_chapter) - 重新生成');
emitInsert('t_chapter', 'course_id, title, description, sort_order', actualChapterRows);

emitComment('7. 课时表扩展 (t_lesson)');
emitInsert('t_lesson', 'chapter_id, title, resource_type, resource_url, video_url, content, duration, sort_order, view_count', actualLessonRows);

console.error(`Generated ${actualChapterRows.length} chapters, ${actualLessonRows.length} lessons`);

// ===== 8. t_teaching_class (新增教学班) =====
emitComment('8. 教学班表扩展 (t_teaching_class)');
const classNames = ['软件1班','软件2班','软工1班','软工2班','计科1班','计科2班','计科3班','数据1班','数据2班','AI1班','AI2班','网安1班','网安2班','物联网1班','数媒1班'];
const semester = '2026春季';
const teachingClasses = [];
for (let cid = 14; cid <= totalCourses; cid++) {
  const numClasses = randInt(1, 3);
  for (let j = 0; j < numClasses; j++) {
    const cn = pick(classNames);
    const tId = pick(allTeacherIds);
    const desc = `面向${cn}的${uniqueCourses.find(c => c.title === '')?.title || '课程'}教学班`;
    // Find the course title
    const course = uniqueCourses[cid - 14];
    const title = course ? course.title : `课程${cid}`;
    const name = `${cn} ${title}`;
    const status = Math.random() > 0.15 ? 1 : 0;
    const sem = Math.random() > 0.5 ? '2026春季' : '2026秋季';
    teachingClasses.push([cid, tId, name, sem, `面向${cn}的${title}教学班。`, status]);
  }
}
emitInsert('t_teaching_class', 'course_id, teacher_id, class_name, semester, description, status', teachingClasses);

const totalTeachingClasses = 10 + teachingClasses.length;
console.error(`Generated ${teachingClasses.length} teaching classes (total: ${totalTeachingClasses})`);

// ===== 9. t_class_student (新增选课记录) =====
emitComment('9. 教学班学生表扩展 (t_class_student)');
const classStudentRows = [];
const usedClassStudent = new Set();
for (let classId = 11; classId <= totalTeachingClasses; classId++) {
  const studentsInClass = pickN(allStudentIds, randInt(5, 15));
  for (const sId of studentsInClass) {
    const key = `${classId}-${sId}`;
    if (!usedClassStudent.has(key)) {
      usedClassStudent.add(key);
      const status = Math.random() > 0.08 ? 1 : 2;
      classStudentRows.push([classId, sId, status]);
    }
  }
}
// Ensure 100+
while (classStudentRows.length < 100) {
  const classId = randInt(11, totalTeachingClasses);
  const sId = pick(allStudentIds);
  const key = `${classId}-${sId}`;
  if (!usedClassStudent.has(key)) {
    usedClassStudent.add(key);
    classStudentRows.push([classId, sId, 1]);
  }
}
emitInsert('t_class_student', 'class_id, student_id, status', classStudentRows);
console.error(`Generated ${classStudentRows.length} class_student records`);

// ===== 10. t_learning_progress (新增学习进度) =====
emitComment('10. 学习进度表扩展 (t_learning_progress)');
const progressRows = [];
// Map lessons to their course to ensure valid FK
const lessonToCourse = {};
for (const lr of actualLessonRows) {
  // lr[0] is chapter_id
  const chId = lr[0];
  // chapter_id -> course_id
}
// Simplified: for each class-student pair, add some progress records
const classStudentPairs = classStudentRows.slice(0, 100);
for (const cs of classStudentPairs) {
  const classId = cs[0];
  // Find which course this class belongs to
  // We need to map class -> course_id. For simplicity, use the course from teachingClasses
  const tc = teachingClasses.find((_, idx) => (11 + idx) === classId) || teachingClasses[0];
  const courseId = tc ? tc[0] : 14;
  // Find lessons for this course
  const chStart = courseChapterStartIds[courseId];
  if (!chStart) continue;
  const chCount = actualChapterRows.filter(r => r[0] === courseId).length;
  if (chCount === 0) continue;
  const chId = chStart;
  const lessonsForChapter = actualLessonRows.filter(l => l[0] === chId);
  if (lessonsForChapter.length === 0) continue;
  const lessonId = 44 + actualLessonRows.indexOf(lessonsForChapter[0]) + 1; // approx
  const progress = randInt(0, 100);
  const seconds = Math.floor(progress / 100 * randInt(600, 3600));
  const daysAgo = randInt(0, 30);
  const timeExpr = daysAgo === 0 ? 'NOW()' : `DATE_SUB(NOW(), INTERVAL ${daysAgo} DAY)`;
  progressRows.push([classId, cs[1], lessonId > 44 ? lessonId : 45, progress, seconds, seconds, SQL(timeExpr)]);
}
emitInsert('t_learning_progress', 'class_id, student_id, lesson_id, progress, learned_seconds, last_position, last_learn_time', progressRows);
console.error(`Generated ${progressRows.length} learning_progress records`);

// ===== 11. t_homework (新增作业) =====
emitComment('11. 作业表扩展 (t_homework)');
const homeworkTitles = [
  ['基础练习题', '完成本章节基础概念相关练习题，加深理解。'],
  ['编程实践题', '根据所学知识编写完整程序实现指定功能。'],
  ['案例分析报告', '结合实际案例进行分析并撰写分析报告。'],
  ['小组合作项目', '分组完成一个小型项目，锻炼团队协作能力。'],
  ['实验报告撰写', '根据实验操作过程撰写规范的实验报告。'],
  ['文献调研任务', '查阅相关文献并撰写文献综述报告。'],
  ['代码优化实践', '对给定的代码进行重构和性能优化。'],
  ['Bug修复练习', '在给定的代码中定位并修复隐藏的Bug。'],
  ['单元测试编写', '为核心功能模块编写完整的单元测试用例。'],
  ['API接口开发', '设计和实现符合RESTful规范的API接口。'],
  ['数据库设计实践', '根据需求设计数据库表结构和索引方案。'],
  ['算法实现挑战', '使用不同算法解决同一问题并对比性能差异。'],
  ['系统设计文档', '针对给定的需求撰写系统设计说明书。'],
  ['用户手册编写', '为已完成的系统编写用户操作手册。'],
  ['演示PPT制作', '制作项目汇报演示文稿并准备答辩。'],
];

const homeworkRows = [];
for (let classId = 11; classId <= totalTeachingClasses; classId++) {
  const numHomeworks = randInt(1, 4);
  for (let h = 0; h < numHomeworks; h++) {
    const ht = pick(homeworkTitles);
    const tId = pick(allTeacherIds);
    const fullScore = pick([100, 100, 100, 50, 50, 20]);
    const startDays = randInt(-14, 7);
    const deadlineDays = startDays + randInt(7, 21);
    const status = deadlineDays < 0 ? 2 : (startDays > 0 ? 0 : 1);
    const hwStartTime = SQL(startDays > 0 ? `DATE_ADD(NOW(), INTERVAL ${startDays} DAY)` : (startDays < 0 ? `DATE_SUB(NOW(), INTERVAL ${Math.abs(startDays)} DAY)` : 'NOW()'));
    const hwDeadline = SQL(deadlineDays > 0 ? `DATE_ADD(NOW(), INTERVAL ${deadlineDays} DAY)` : (deadlineDays < 0 ? `DATE_SUB(NOW(), INTERVAL ${Math.abs(deadlineDays)} DAY)` : 'NOW()'));
    const hwAttach = Math.random() > 0.3 ? `/uploads/homework/hw_${homeworkRows.length + 9}.docx` : null;
    homeworkRows.push([classId, tId, `${ht[0]}${randInt(1, 100)}`, ht[1], hwAttach, fullScore, hwStartTime, hwDeadline, status]);
  }
}
emitInsert('t_homework', 'class_id, teacher_id, title, content, attachment_url, full_score, start_time, deadline, status', homeworkRows);
const totalHomeworks = 8 + homeworkRows.length;
console.error(`Generated ${homeworkRows.length} homeworks (total: ${totalHomeworks})`);

// ===== 12. t_homework_submit (新增作业提交) =====
emitComment('12. 作业提交表扩展 (t_homework_submit)');
const hwSubmitRows = [];
for (let hwId = 9; hwId <= totalHomeworks; hwId++) {
  // Find students in this homework's class
  // We don't have the mapping directly, so distribute randomly
  const numSubmits = randInt(3, 10);
  const selectedStudents = pickN(allStudentIds, Math.min(numSubmits, allStudentIds.length));
  for (const sId of selectedStudents) {
    const daysAgo = randInt(0, 20);
    const subTime = SQL(daysAgo === 0 ? 'NOW()' : `DATE_SUB(NOW(), INTERVAL ${daysAgo} DAY)`);
    const graded = Math.random() > 0.3;
    const score = graded ? randFloat(60, 100) : null;
    const gStatus = graded ? 1 : 0;
    const gId = graded ? pick(allTeacherIds) : null;
    const subGradeTime = graded ? SQL(daysAgo > 1 ? `DATE_SUB(NOW(), INTERVAL ${randInt(0, daysAgo)} DAY)` : 'NOW()') : null;
    const feedback = graded ? pick(['完成质量较高，继续保持。', '基本完成，部分细节可优化。', '建议加强练习，注意规范。', '完成情况良好，思路清晰。', '代码风格不错，逻辑较完整。', '整体较好，部分知识点需巩固。']) : null;
    hwSubmitRows.push([hwId, sId, `已完成作业并提交，提交编号 HW${hwSubmitRows.length + 10}`, subTime, score, feedback, gStatus, gId, subGradeTime]);
  }
}
emitInsert('t_homework_submit', 'homework_id, student_id, submit_content, submit_time, score, feedback, grade_status, grader_id, grade_time', hwSubmitRows);
console.error(`Generated ${hwSubmitRows.length} homework_submit records`);

// ===== 13. t_question (新增题目) =====
emitComment('13. 题库表扩展 (t_question)');
// Generate 70+ more questions to reach 100+
const questionTypes = ['single', 'judge', 'multiple', 'blank', 'short_answer'];
const singleQuestions = [
  ['以下哪种排序算法的最坏时间复杂度是O(n²)？', '[{"optionKey":"A","optionContent":"归并排序","sortOrder":1},{"optionKey":"B","optionContent":"快速排序","sortOrder":2},{"optionKey":"C","optionContent":"堆排序","sortOrder":3},{"optionKey":"D","optionContent":"计数排序","sortOrder":4}]', '[{"answerContent":"B","sortOrder":1}]', '快速排序最坏情况为O(n²)，平均O(n log n)。', '排序算法', 2],
  ['HTTP 响应状态码 404 表示什么？', '[{"optionKey":"A","optionContent":"服务器内部错误","sortOrder":1},{"optionKey":"B","optionContent":"资源未找到","sortOrder":2},{"optionKey":"C","optionContent":"请求超时","sortOrder":3},{"optionKey":"D","optionContent":"禁止访问","sortOrder":4}]', '[{"answerContent":"B","sortOrder":1}]', '404表示服务器无法找到请求的资源。', 'HTTP协议', 1],
  ['数据库中 ACID 中的 A 代表什么？', '[{"optionKey":"A","optionContent":"可用性","sortOrder":1},{"optionKey":"B","optionContent":"原子性","sortOrder":2},{"optionKey":"C","optionContent":"一致性","sortOrder":3},{"optionKey":"D","optionContent":"隔离性","sortOrder":4}]', '[{"answerContent":"B","sortOrder":1}]', '原子性指事务中的操作要么全部完成要么全部不执行。', '事务特性', 2],
  ['在面向对象中，子类可以访问父类的哪种成员？', '[{"optionKey":"A","optionContent":"私有成员","sortOrder":1},{"optionKey":"B","optionContent":"受保护成员","sortOrder":2},{"optionKey":"C","optionContent":"默认成员","sortOrder":3},{"optionKey":"D","optionContent":"全部成员","sortOrder":4}]', '[{"answerContent":"B","sortOrder":1}]', 'protected成员可以在同一包中或子类中被访问。', '封装与继承', 2],
  ['Linux 中查看当前目录的命令是？', '[{"optionKey":"A","optionContent":"cd","sortOrder":1},{"optionKey":"B","optionContent":"ls","sortOrder":2},{"optionKey":"C","optionContent":"pwd","sortOrder":3},{"optionKey":"D","optionContent":"dir","sortOrder":4}]', '[{"answerContent":"C","sortOrder":1}]', 'pwd(print working directory)显示当前工作目录。', 'Linux基础', 1],
  ['以下哪项不是关系型数据库？', '[{"optionKey":"A","optionContent":"MySQL","sortOrder":1},{"optionKey":"B","optionContent":"PostgreSQL","sortOrder":2},{"optionKey":"C","optionContent":"MongoDB","sortOrder":3},{"optionKey":"D","optionContent":"Oracle","sortOrder":4}]', '[{"answerContent":"C","sortOrder":1}]', 'MongoDB是文档型NoSQL数据库。', '数据库分类', 1],
  ['Git 中用于切换分支的命令是？', '[{"optionKey":"A","optionContent":"git branch","sortOrder":1},{"optionKey":"B","optionContent":"git checkout","sortOrder":2},{"optionKey":"C","optionContent":"git switch","sortOrder":3},{"optionKey":"D","optionContent":"git clone","sortOrder":4}]', '[{"answerContent":"B","sortOrder":1}]', 'git checkout可用于切换分支，git switch是较新的替代命令。', '版本控制', 1],
  ['TCP/IP 协议栈中IP协议位于哪一层？', '[{"optionKey":"A","optionContent":"应用层","sortOrder":1},{"optionKey":"B","optionContent":"传输层","sortOrder":2},{"optionKey":"C","optionContent":"网络层","sortOrder":3},{"optionKey":"D","optionContent":"链路层","sortOrder":4}]', '[{"answerContent":"C","sortOrder":1}]', 'IP协议工作在网络层，负责数据包的路由和转发。', '网络协议', 1],
  ['软件工程中"瀑布模型"的主要特点是什么？', '[{"optionKey":"A","optionContent":"迭代开发","sortOrder":1},{"optionKey":"B","optionContent":"阶段顺序执行","sortOrder":2},{"optionKey":"C","optionContent":"快速原型","sortOrder":3},{"optionKey":"D","optionContent":"持续交付","sortOrder":4}]', '[{"answerContent":"B","sortOrder":1}]', '瀑布模型各阶段严格按顺序执行，适合需求明确的项目。', '开发模型', 1],
  ['Docker 容器与虚拟机的本质区别是什么？', '[{"optionKey":"A","optionContent":"无区别","sortOrder":1},{"optionKey":"B","optionContent":"容器共享宿主机内核","sortOrder":2},{"optionKey":"C","optionContent":"容器包含完整操作系统","sortOrder":3},{"optionKey":"D","optionContent":"虚拟机运行更快","sortOrder":4}]', '[{"answerContent":"B","sortOrder":1}]', '容器共享宿主机内核实现轻量级虚拟化。', '容器技术', 2],
];

const judgeQuestions = [
  [1, 'HTTP 是无状态协议。', '[{"optionKey":"A","optionContent":"正确","sortOrder":1},{"optionKey":"B","optionContent":"错误","sortOrder":2}]', '[{"answerContent":"A","sortOrder":1}]', '每个HTTP请求都是独立的，服务器不保留之前请求的信息。', 'HTTP协议', 1],
  [0, '死锁只能通过重启系统来解决。', null, '[{"answerContent":"B","sortOrder":1}]', '死锁可通过预防、避免、检测和恢复等多种方式处理。', '操作系统', 2],
  [1, 'RESTful API 应使用动词命名资源路径。', null, '[{"answerContent":"B","sortOrder":1}]', 'RESTful API使用名词表示资源，HTTP动词表示操作。', 'API设计', 1],
  [0, '前端开发中 CSS 的 flex 布局可以实现水平垂直居中。', null, '[{"answerContent":"A","sortOrder":1}]', '使用 display:flex + align-items:center + justify-content:center可实现。', 'CSS布局', 1],
  [1, '时间复杂度为 O(n) 的算法一定比 O(n²) 的算法快。', null, '[{"answerContent":"B","sortOrder":1}]', '大O表示渐进上界，实际运行时间还受常数因子和数据规模影响。', '算法分析', 2],
  [0, 'HTTPS 在传输层使用 SSL/TLS 加密。', null, '[{"answerContent":"A","sortOrder":1}]', 'HTTPS在应用层与传输层之间加入SSL/TLS层实现加密。', '网络安全', 1],
  [1, '微服务架构必须使用 Docker 容器部署。', null, '[{"answerContent":"B","sortOrder":1}]', '微服务可使用多种部署方式，容器化是推荐但不是必须的。', '微服务', 1],
  [0, 'Python 中的列表和元组都是可变的。', null, '[{"answerContent":"B","sortOrder":1}]', '列表(list)可变，元组(tuple)不可变。', 'Python基础', 1],
];

const questionRows = [];
let nextQId = 39;

// Single choice questions
for (const q of singleQuestions) {
  const courseId = randInt(14, totalCourses);
  const tId = pick(allTeacherIds);
  const cols = randInt(1, 3);
  questionRows.push([courseId, tId, 'single', q[0], q[1], q[2], q[3], q[4], q[5], 5.00, 1]);
  nextQId++;
}

// Judge questions
for (const q of judgeQuestions) {
  const courseId = randInt(14, totalCourses);
  const tId = pick(allTeacherIds);
  questionRows.push([courseId, tId, 'judge', q[2], q[3], q[4], q[5], q[6], q[7], 5.00, 1]);
  nextQId++;
}

// Multiple choice questions
const multiQuestions = [
  ['以下哪些是关系型数据库管理系统？', '[{"optionKey":"A","optionContent":"MySQL","sortOrder":1},{"optionKey":"B","optionContent":"Redis","sortOrder":2},{"optionKey":"C","optionContent":"PostgreSQL","sortOrder":3},{"optionKey":"D","optionContent":"Oracle","sortOrder":4}]', '[{"answerContent":"A","sortOrder":1},{"answerContent":"C","sortOrder":2},{"answerContent":"D","sortOrder":3}]', 'Redis是键值存储系统，不是关系型数据库。', '数据库', 1],
  ['以下哪些属于 HTTP 请求方法？', '[{"optionKey":"A","optionContent":"GET","sortOrder":1},{"optionKey":"B","optionContent":"FETCH","sortOrder":2},{"optionKey":"C","optionContent":"POST","sortOrder":3},{"optionKey":"D","optionContent":"DELETE","sortOrder":4}]', '[{"answerContent":"A","sortOrder":1},{"answerContent":"C","sortOrder":2},{"answerContent":"D","sortOrder":3}]', 'FETCH不是标准HTTP方法，标准的还有PUT、PATCH等。', 'HTTP', 1],
  ['设计模式中哪些属于创建型模式？', '[{"optionKey":"A","optionContent":"工厂方法","sortOrder":1},{"optionKey":"B","optionContent":"观察者模式","sortOrder":2},{"optionKey":"C","optionContent":"单例模式","sortOrder":3},{"optionKey":"D","optionContent":"建造者模式","sortOrder":4}]', '[{"answerContent":"A","sortOrder":1},{"answerContent":"C","sortOrder":2},{"answerContent":"D","sortOrder":3}]', '观察者模式属于行为型模式。', '设计模式', 2],
  ['以下哪些是 Git 中文件的三种状态？', '[{"optionKey":"A","optionContent":"已修改(modified)","sortOrder":1},{"optionKey":"B","optionContent":"已暂存(staged)","sortOrder":2},{"optionKey":"C","optionContent":"已提交(committed)","sortOrder":3},{"optionKey":"D","optionContent":"已部署(deployed)","sortOrder":4}]', '[{"answerContent":"A","sortOrder":1},{"answerContent":"B","sortOrder":2},{"answerContent":"C","sortOrder":3}]', '已部署不是Git中的状态概念。', '版本控制', 1],
  ['Linux 文件权限中哪些是基本权限类型？', '[{"optionKey":"A","optionContent":"读(r)","sortOrder":1},{"optionKey":"B","optionContent":"写(w)","sortOrder":2},{"optionKey":"C","optionContent":"执行(x)","sortOrder":3},{"optionKey":"D","optionContent":"删除(d)","sortOrder":4}]', '[{"answerContent":"A","sortOrder":1},{"answerContent":"B","sortOrder":2},{"answerContent":"C","sortOrder":3}]', 'Linux基本权限为读(r)、写(w)、执行(x)。', 'Linux', 1],
];

for (const q of multiQuestions) {
  const courseId = randInt(14, totalCourses);
  const tId = pick(allTeacherIds);
  questionRows.push([courseId, tId, 'multiple', q[0], q[1], q[2], q[3], q[4], q[5], 8.00, 1]);
  nextQId++;
}

// Blank questions
const blankQuestions = [
  ['在关系数据库中，用于唯一标识每条记录的字段称为 ______ 。', '[{"answerContent":"主键","sortOrder":1}]', '主键用于唯一标识表中的每一行数据。', '数据库设计', 1],
  ['操作系统中的 LRU 页面置换算法的中文全称是 ______ 。', '[{"answerContent":"最近最久未使用","sortOrder":1}]', 'LRU(Least Recently Used)置换最近最久未使用的页面。', '内存管理', 2],
  ['计算机网络中 DNS 的中文含义是 ______ 。', '[{"answerContent":"域名系统","sortOrder":1}]', 'DNS将域名解析为IP地址。', '网络基础', 1],
  ['软件测试中，白盒测试的另一种常见名称是 ______ 。', '[{"answerContent":"结构测试","sortOrder":1}]', '白盒测试关注程序内部结构和逻辑路径。', '测试方法', 2],
  ['算法中 FIFO 的中文含义是 ______ 。', '[{"answerContent":"先进先出","sortOrder":1}]', 'FIFO(First In First Out)常用于队列调度。', '数据结构', 1],
];

for (const q of blankQuestions) {
  const courseId = randInt(14, totalCourses);
  const tId = pick(allTeacherIds);
  questionRows.push([courseId, tId, 'blank', q[0], null, q[1], q[2], q[3], q[4], 5.00, 1]);
  nextQId++;
}

// Short answer questions
const shortAnswerQuestions = [
  ['请简述进程和线程的主要区别。', '[{"answerContent":"进程是资源分配的基本单位，线程是CPU调度的基本单位；同一进程的线程共享地址空间，进程间地址空间独立","sortOrder":1}]', '进程拥有独立的地址空间和资源，线程共享所属进程的资源。', '操作系统', 2],
  ['解释 RESTful API 中 GET 和 POST 方法的区别。', '[{"answerContent":"GET获取资源，参数在URL中；POST创建资源，参数在请求体中；GET幂等且安全，POST不幂等","sortOrder":1}]', 'GET用于获取资源，POST用于创建资源，两者语义和安全性不同。', 'API设计', 2],
  ['简述 MVC 架构模式中三个组件的职责。', '[{"answerContent":"Model管理数据和业务逻辑，View负责数据显示，Controller处理用户输入并协调Model和View","sortOrder":1}]', 'MVC将应用拆分为模型、视图和控制器三个核心组件。', '架构模式', 2],
  ['什么是事务的隔离性？为什么需要隔离性？', '[{"answerContent":"隔离性指多个并发事务执行时互不干扰，每个事务都感觉其他事务不存在。需要隔离性以避免脏读、不可重复读和幻读等问题","sortOrder":1}]', '隔离性是ACID特性之一，通过锁或多版本并发控制实现。', '数据库事务', 3],
  ['简述哈希表冲突解决中的链地址法原理。', '[{"answerContent":"将哈希表的每个桶指向一个链表，冲突的元素存放在同一桶的链表中；查找时先定位桶再遍历链表","sortOrder":1}]', '链地址法将哈希冲突的元素组织在链表中，实现简单。', '数据结构', 2],
];

for (const q of shortAnswerQuestions) {
  const courseId = randInt(14, totalCourses);
  const tId = pick(allTeacherIds);
  questionRows.push([courseId, tId, 'short_answer', q[0], null, q[1], q[2], q[3], q[4], 10.00, 1]);
  nextQId++;
}

// Fill remaining to 100+ questions
while (questionRows.length < 62) { // 38 existing + need 62 more
  const qType = pick(questionTypes);
  const courseId = randInt(14, totalCourses);
  const tId = pick(allTeacherIds);
  const title = `补充题目 ${questionRows.length + 1}：${qType === 'judge' ? '判断题' : qType === 'single' ? '选择题' : '填空题'}`;
  const options = qType === 'single' ? `[{"optionKey":"A","optionContent":"选项A","sortOrder":1},{"optionKey":"B","optionContent":"选项B","sortOrder":2},{"optionKey":"C","optionContent":"选项C","sortOrder":3},{"optionKey":"D","optionContent":"选项D","sortOrder":4}]` : (qType === 'judge' ? `[{"optionKey":"A","optionContent":"正确","sortOrder":1},{"optionKey":"B","optionContent":"错误","sortOrder":2}]` : null);
  const answer = qType === 'single' ? '[{"answerContent":"A","sortOrder":1}]' : (qType === 'judge' ? '[{"answerContent":"A","sortOrder":1}]' : '[{"answerContent":"答案","sortOrder":1}]');
  questionRows.push([courseId, tId, qType, title, options, answer, '题目解析内容', '综合知识点', randInt(1, 3), 5.00, 1]);
}

emitInsert('t_question', 'course_id, teacher_id, question_type, title, options, answer, analysis, knowledge_point, difficulty, score, status', questionRows);
console.error(`Generated ${questionRows.length} questions (total with existing: ${38 + questionRows.length})`);

// ===== 14. t_exam_paper (新增试卷) =====
emitComment('14. 试卷表扩展 (t_exam_paper)');
const paperRows = [];
for (let courseId = 14; courseId <= totalCourses; courseId++) {
  const numPapers = randInt(0, 2);
  for (let p = 0; p < numPapers; p++) {
    const tId = pick(allTeacherIds);
    const paperTotalScore = randInt(20, 100);
    const duration = randInt(30, 120);
    const status = Math.random() > 0.2 ? 1 : 0;
    const course = uniqueCourses[courseId - 14];
    const title = course ? `${course.title}阶段测试${p + 1}` : `课程${courseId}阶段测试${p + 1}`;
    paperRows.push([courseId, tId, title, `${title}的考试试卷，覆盖该课程核心知识点。`, paperTotalScore, duration, status]);
  }
}
emitInsert('t_exam_paper', 'course_id, teacher_id, title, description, total_score, duration_minutes, status', paperRows);
const totalPapers = 9 + paperRows.length;
console.error(`Generated ${paperRows.length} paper (total: ${totalPapers})`);

// ===== 15. t_exam_paper_question (新增试卷题目) =====
emitComment('15. 试卷题目关联表扩展 (t_exam_paper_question)');
const totalQuestions = 38 + questionRows.length;
const epqRows = [];
for (let paperId = 10; paperId <= totalPapers; paperId++) {
  const numQuestions = randInt(3, 8);
  const usedQIds = new Set();
  for (let q = 0; q < numQuestions; q++) {
    let qId = randInt(1, totalQuestions);
    while (usedQIds.has(qId)) qId = randInt(1, totalQuestions);
    usedQIds.add(qId);
    const score = pick([5, 5, 5, 8, 10, 10, 15]);
    const required = Math.random() > 0.1 ? 1 : 0;
    epqRows.push([paperId, qId, score, q + 1, required]);
  }
}
emitInsert('t_exam_paper_question', 'paper_id, question_id, score, sort_order, required_answer', epqRows);
const totalEPQ = 36 + epqRows.length;
console.error(`Generated ${epqRows.length} paper_question records (total: ${totalEPQ})`);

// ===== 16. t_exam_session (新增考试安排) =====
emitComment('16. 考试安排表扩展 (t_exam_session)');
const sessionRows = [];
for (let classId = 11; classId <= totalTeachingClasses; classId += randInt(1, 2)) {
  if (classId > totalTeachingClasses) break;
  const paperId = randInt(10, totalPapers);
  const tId = pick(allTeacherIds);
  const daysFromNow = randInt(-10, 20);
  const duration = randInt(30, 120);
  const status = daysFromNow < -1 ? 3 : (daysFromNow < 0 ? 2 : (daysFromNow < 7 ? 1 : 0));
  const sessStart = SQL(daysFromNow <= 0 ? `DATE_SUB(NOW(), INTERVAL ${Math.abs(daysFromNow)} DAY)` : `DATE_ADD(NOW(), INTERVAL ${daysFromNow} DAY)`);
  const sessEnd = SQL(`DATE_ADD(${sessStart.value}, INTERVAL ${duration} MINUTE)`);
  sessionRows.push([classId, paperId, tId, `考试安排${sessionRows.length + 9}`, `${duration}分钟考试`, sessStart, sessEnd, duration, status]);
}
emitInsert('t_exam_session', 'class_id, paper_id, teacher_id, title, description, start_time, end_time, duration, status', sessionRows);
const totalSessions = 8 + sessionRows.length;
console.error(`Generated ${sessionRows.length} exam sessions (total: ${totalSessions})`);

// ===== 17. t_exam_record (新增考试记录) =====
emitComment('17. 考试记录表扩展 (t_exam_record)');
const recordRows = [];
for (let sessionId = 9; sessionId <= totalSessions; sessionId++) {
  const numRecords = randInt(3, 10);
  const selectedStudents = pickN(allStudentIds, Math.min(numRecords, allStudentIds.length));
  for (const sId of selectedStudents) {
    const submitted = Math.random() > 0.15;
    const recTotalScore = submitted ? randFloat(20, 100) : 'NULL';
    const objScore = submitted ? randFloat(15, 80) : 'NULL';
    const subjScore = submitted && Math.random() > 0.3 ? randFloat(0, 20) : 'NULL';
    const status = submitted ? (Math.random() > 0.2 ? 2 : 3) : 0;
    const startDays = randInt(0, 20);
    const recStartTime = SQL(startDays === 0 ? 'NOW()' : `DATE_SUB(NOW(), INTERVAL ${startDays} DAY)`);
    const recSubmitTime = submitted ? SQL(startDays > 0 ? `DATE_ADD(NOW(), INTERVAL ${startDays - randInt(0, 2)} DAY)` : 'NOW()') : null;
    recordRows.push([sessionId, sId, recStartTime, recSubmitTime, recTotalScore, objScore, subjScore, status]);
  }
}
emitInsert('t_exam_record', 'exam_session_id, student_id, start_time, submit_time, total_score, objective_score, subjective_score, status', recordRows);
const totalRecords = 12 + recordRows.length;
console.error(`Generated ${recordRows.length} exam records (total: ${totalRecords})`);

// ===== 18. t_exam_answer (新增答题记录) =====
emitComment('18. 考试答题表扩展 (t_exam_answer)');
const answerRows = [];
for (let recordId = 13; recordId <= totalRecords; recordId++) {
  const numAnswers = randInt(3, 8);
  for (let a = 0; a < numAnswers; a++) {
    const qId = randInt(1, totalQuestions);
    const pqId = randInt(1, totalEPQ);
    const isCorrect = Math.random() > 0.35;
    const correctAns = `[{"answerContent":"${pick(['A','B','C','D','true','false'])}","sortOrder":1}]`;
    const studentAns = isCorrect ? correctAns : `[{"answerContent":"${pick(['A','B','C','D'])}","sortOrder":1}]`;
    const score = isCorrect ? randFloat(3, 10) : 0;
    const gStatus = 1;
    const gId = pick(allTeacherIds);
    answerRows.push([recordId, qId, pqId, studentAns, correctAns, score, isCorrect ? 1 : 0, gStatus, gId, SQL(`DATE_SUB(NOW(), INTERVAL ${randInt(0, 10)} DAY)`)]);
  }
}

// Ensure 100+
while (answerRows.length < 67) { // 33 existing + need 67 more
  const recordId = randInt(13, totalRecords);
  const qId = randInt(1, totalQuestions);
   answerRows.push([recordId, qId, randInt(1, totalEPQ), 'A', '[{"answerContent":"A","sortOrder":1}]', 5.00, 1, 1, pick(allTeacherIds), SQL('NOW()')]);
}
emitInsert('t_exam_answer', 'exam_record_id, question_id, paper_question_id, student_answer, correct_answer, score, is_correct, grade_status, grader_id, grade_time', answerRows);
console.error(`Generated ${answerRows.length} exam answers (total with existing: ${33 + answerRows.length})`);

// ===== 19. t_grade_record (新增成绩记录) =====
emitComment('19. 成绩记录表扩展 (t_grade_record)');
const gradeRows = [];
for (let recI = 0; recI < Math.min(recordRows.length, 100); recI++) {
  const rec = recordRows[recI];
  const sId = rec[1];
  const recScore = rec[4];
  if (recScore === 'NULL') continue;
  const sessionId = rec[0];
  // Find which class this session belongs to
  const session = sessionRows[sessionId - 9] || sessionRows[0];
  const classId = session ? session[0] : 11;
  // Course ID from teachingClass
  const tc = teachingClasses.find((_, idx) => (11 + idx) === classId) || teachingClasses[0];
  const courseId = tc ? tc[0] : 14;
  const tId = pick(allTeacherIds);
  const fullScore = 100;
  gradeRows.push([sId, classId, courseId, tId, `考试记录${recI + 16}`, 'exam', recI + 16, recScore, fullScore, '考试成绩']);
}
emitInsert('t_grade_record', 'student_id, class_id, course_id, teacher_id, grade_name, source_type, source_id, score, full_score, grade_comment', gradeRows);
console.error(`Generated ${gradeRows.length} grade records (total with existing: ${15 + gradeRows.length})`);

// ===== 20. t_grade_import_log (新增导入日志) =====
emitComment('20. 成绩导入日志表扩展 (t_grade_import_log)');
const importLogRows = [];
for (let i = 0; i < 100; i++) {
  const tId = pick(allTeacherIds);
  const classId = randInt(11, totalTeachingClasses);
  const total = randInt(10, 50);
  const success = randInt(total - 5, total);
  const fail = total - success;
  const fileName = `grade_import_${i + 6}.xlsx`;
  const errMsg = fail > 0 ? `有${fail}行数据导入失败` : 'NULL';
  importLogRows.push([tId, classId, fileName, `/uploads/grade/${fileName}`, total, success, fail, errMsg]);
}
emitInsert('t_grade_import_log', 'teacher_id, class_id, file_name, file_url, total_count, success_count, fail_count, error_message', importLogRows);
console.error(`Generated ${importLogRows.length} grade_import_log records`);

// ===== 21. t_notice (新增通知公告) =====
emitComment('21. 通知公告表扩展 (t_notice)');
const noticeTypes = ['homework', 'exam', 'course', 'system'];
const noticeTitles = [
  ['课程更新提醒', '课程内容已更新，请查看最新章节。'],
  ['作业截止提醒', '作业即将截止，请尽快提交。'],
  ['考试安排通知', '考试时间安排已发布，请查看详情。'],
  ['成绩发布通知', '最新成绩已发布，请登录查看。'],
  ['调课通知', '因故课程时间调整，请留意新的上课时间。'],
  ['答疑安排', '本周答疑时间和地点安排如下。'],
  ['教学资料更新', '新的教学资料已上传至平台。'],
  ['竞赛报名通知', '学科竞赛报名已开始，欢迎踊跃参加。'],
  ['学术讲座预告', '本周学术讲座安排，欢迎参加。'],
  ['选课提醒', '选课系统即将关闭，请及时完成选课。'],
  ['系统维护通知', '平台将进行例行维护，届时暂停访问。'],
  ['放假通知', '节假日安排及调课说明。'],
  ['补考通知', '补考报名及考试安排已发布。'],
  ['实习通知', '实习安排及相关要求已发布。'],
  ['毕业设计通知', '毕设选题及进度安排请关注。'],
];
const noticeRows = [];
for (let i = 0; i < 100; i++) {
  const nt = pick(noticeTitles);
  const pId = pick(allUserIds);
  const classId = Math.random() > 0.15 ? randInt(1, totalTeachingClasses) : 'NULL';
  const nType = pick(noticeTypes);
  const status = Math.random() > 0.1 ? 1 : 0;
  noticeRows.push([pId, classId, `${nt[0]}${i + 12}`, `${nt[1]}（通知编号 ${i + 12}）`, nType, status]);
}
emitInsert('t_notice', 'publisher_id, class_id, title, content, notice_type, status', noticeRows);
console.error(`Generated ${noticeRows.length} notices`);

// ===== 22. t_operation_log (新增操作日志) =====
emitComment('22. 操作日志表扩展 (t_operation_log)');
const operations = [
  ['用户登录', 'POST', '/auth/login'],
  ['查看课程列表', 'GET', '/api/courses'],
  ['创建课程', 'POST', '/api/courses'],
  ['编辑课程', 'PUT', '/api/courses/'],
  ['删除课程', 'DELETE', '/api/courses/'],
  ['查看章节', 'GET', '/api/chapters'],
  ['提交作业', 'POST', '/api/homework/submit'],
  ['批改作业', 'PUT', '/api/homework/grade/'],
  ['创建试卷', 'POST', '/api/exam-papers'],
  ['发布考试', 'PUT', '/api/exam-sessions/publish/'],
  ['开始考试', 'POST', '/api/exam/start/'],
  ['提交答案', 'POST', '/api/exam/submit/'],
  ['查看成绩', 'GET', '/api/grades'],
  ['导入成绩', 'POST', '/api/grades/import'],
  ['导出成绩', 'GET', '/api/grades/export'],
  ['查看学习进度', 'GET', '/api/progress'],
  ['创建通知', 'POST', '/api/notices'],
  ['创建题目', 'POST', '/api/questions'],
  ['编辑题目', 'PUT', '/api/questions/'],
  ['用户注册', 'POST', '/auth/register'],
  ['更新资料', 'PUT', '/api/user/profile'],
  ['修改密码', 'PUT', '/api/user/password'],
  ['查看题库', 'GET', '/api/questions'],
  ['查看考试安排', 'GET', '/api/exam-sessions'],
  ['查看教学班', 'GET', '/api/teaching-classes'],
  ['添加学生', 'POST', '/api/class-students'],
  ['移除学生', 'DELETE', '/api/class-students/'],
  ['查看操作日志', 'GET', '/api/logs'],
];
const ipPrefixes = ['192.168.1.', '10.0.0.', '172.16.0.', '192.168.2.', '10.10.0.'];
const logRows = [];
for (let i = 0; i < 100; i++) {
  const op = pick(operations);
  const uId = pick(allUserIds);
  const url = op[2] + (op[2].endsWith('/') ? randInt(1, 100) : '');
  const params = `{"userId":${uId},"time":"${new Date().toISOString()}"}`;
  const ip = pick(ipPrefixes) + randInt(1, 255);
  logRows.push([uId, op[0], op[1], url, params, ip]);
}
emitInsert('t_operation_log', 'user_id, operation, method, url, params, ip', logRows);
console.error(`Generated ${logRows.length} operation logs`);

// ===== 23. t_agent (新增Agent) =====
emitComment('23. Agent定义表扩展 (t_agent)');
const agentData = [
  ['course_recommender', '课程推荐助手', '根据学生兴趣和历史数据推荐课程', '你是AI智学平台的课程推荐助手，请根据学生的专业方向、学习历史和兴趣偏好，推荐最适合的课程，并给出推荐理由。', 'gpt-4', 0.8, 1],
  ['homework_grader', '作业批改助手', '自动批改学生提交的作业', '你是作业批改助手，请根据参考答案和评分标准，公正客观地批改学生作业，给出分数和改进建议。', 'gpt-4', 0.3, 1],
  ['knowledge_qa', '知识问答助手', '回答学生关于课程内容的疑问', '你是课程知识问答助手，请根据教材和课程内容，准确回答学生的问题，必要时引用资料出处。', 'gpt-3.5-turbo', 0.5, 1],
  ['study_planner', '学习计划制定助手', '帮助学生制定个性化学习计划', '你是学习计划制定助手，请根据学生的课程安排、学习目标和可用时间，制定合理的学习计划。', 'gpt-4', 0.7, 1],
  ['exam_preparer', '考前复习助手', '帮助学生进行考前系统复习', '你是考前复习助手，请根据考试大纲和学生学习薄弱点，生成针对性复习方案和练习题。', 'gpt-4', 0.6, 1],
  ['code_reviewer', '代码审查助手', '对学生提交的代码进行审查和优化建议', '你是代码审查助手，请从代码规范、性能优化、安全性等方面对学生代码进行全面审查。', 'gpt-4', 0.4, 1],
  ['paper_search', '论文检索助手', '帮助学生检索和推荐学术论文', '你是论文检索助手，请根据学生的研究方向和关键词，检索相关高质量论文并给出摘要和推荐理由。', 'gpt-4', 0.7, 1],
  ['career_advisor', '职业规划助手', '为学生提供职业发展建议', '你是职业规划助手，请根据学生的专业背景和个人兴趣，提供行业分析和职业发展建议。', 'gpt-4', 0.8, 1],
  ['english_tutor', '英语学习助手', '辅助学生进行英语听说读写训练', '你是英语学习助手，请通过对话练习、语法讲解和写作指导等方式帮助学生提高英语水平。', 'gpt-4', 0.7, 1],
  ['math_solver', '数学解题助手', '解答数学问题并展示解题过程', '你是数学解题助手，请分步骤展示数学问题的解题过程，注重思路引导和方法总结。', 'gpt-4', 0.5, 1],
  ['data_analyzer', '数据分析助手', '辅助学生进行数据分析和可视化', '你是数据分析助手，请帮助学生选择合适的分析方法和工具，解读分析结果并给出建议。', 'gpt-4', 0.6, 1],
  ['writing_assistant', '写作辅助助手', '帮助学生改进学术写作', '你是学术写作助手，请从结构、逻辑、表达和格式等方面提供写作改进建议。', 'gpt-4', 0.5, 1],
  ['interview_coach', '面试训练助手', '帮助学生准备技术面试', '你是面试训练助手，请模拟技术面试场景，从技术问题、行为问题和软技能等方面进行训练。', 'gpt-4', 0.7, 1],
  ['project_advisor', '项目指导助手', '为学生的项目开发提供指导', '你是项目指导助手，请从技术选型、架构设计、项目管理等方面为学生提供项目开发建议。', 'gpt-4', 0.6, 1],
  ['plagiarism_checker', '学术诚信检查助手', '检查学术不端行为并提醒学生', '你是学术诚信检查助手，请提醒学生遵守学术规范，检查作业和论文中的潜在不端行为。', 'gpt-4', 0.3, 1],
];

// We already have 1 agent, need 99 more. Add realistic agents + fill rest
const fullAgentRows = [];
for (const a of agentData) {
  fullAgentRows.push(a);
}

// Add more agents to reach 100+
const moreAgents = [];
const agentSubjects = ['Python','Java','C++','数据结构','算法','数据库','网络','操作系统','软件工程','人工智能','机器学习','深度学习','前端开发','后端开发','移动开发','云计算','大数据','区块链','物联网','网络安全','嵌入式','图形学','编译原理','计算机组成','离散数学'];
const tools = ['["search_knowledge_base"]', '["search_knowledge_base","web_search"]', '["code_interpreter"]', '["calculator"]', '["web_search"]', '["search_knowledge_base","code_interpreter","web_search"]'];

for (let i = 0; i < 85; i++) {
  const subj = pick(agentSubjects);
  const code = `${subj.toLowerCase().replace(/[\s\/]/g, '_')}_agent${i + 15}`;
  const name = `${subj}学习助手${i + 1}`;
  const desc = `专注${subj}领域的智能问答和学习辅导`;
  const prompt = `你是${subj}学习助手，专注于${subj}领域的知识解答和学习指导。请用清晰、准确的语言回答，结合实例帮助理解。`;
  const model = pick(['gpt-4', 'gpt-3.5-turbo', 'claude-3', 'deepseek-chat']);
  const temp = randFloat(0.3, 0.9, 2);
  const status = Math.random() > 0.1 ? 1 : 0;
  const t = pick(tools);
  const kbIds = Math.random() > 0.3 ? `[${randInt(1, 10)}]` : 'NULL';
  fullAgentRows.push([code, name, desc, prompt, model, temp, kbIds, t, status]);
}

emitInsert('t_agent', 'code, name, description, system_prompt, model, temperature, knowledge_base_ids, enabled_tools, status', fullAgentRows);
const totalAgents = 1 + fullAgentRows.length;
console.error(`Generated ${fullAgentRows.length} agents (total: ${totalAgents})`);

// ===== 24. t_agent_session (新增Agent会话) =====
emitComment('24. Agent会话表扩展 (t_agent_session)');
const sessionData = [];
for (let i = 0; i < 100; i++) {
  const agentId = randInt(1, totalAgents);
  const uId = pick(allUserIds);
  sessionData.push([agentId, uId, `会话${i + 1}`, 1]);
}
emitInsert('t_agent_session', 'agent_id, user_id, title, status', sessionData);
console.error(`Generated ${sessionData.length} agent sessions`);

// ===== 25. t_agent_message (新增Agent消息) =====
emitComment('25. Agent消息表扩展 (t_agent_message)');
const msgData = [];
const roles = ['user', 'assistant', 'user', 'assistant', 'user', 'assistant', 'system'];
for (let i = 0; i < 100; i++) {
  const sessionId = randInt(1, 100);
  const role = pick(roles);
  const content = role === 'user' ? `请问如何理解${pick(['封装','继承','多态','递归','循环','指针','线程','进程','同步','异步'])}的概念？` : 
                 role === 'assistant' ? `${pick(['让我们来详细解释这个概念。','这是一个很好的问题。','我来为你解答这个疑问。'])}${pick(['首先需要理解基本定义，','我们可以从应用场景入手，','从原理层面来看，'])}${pick(['这个概念在实践中有广泛的应用。','掌握这个知识点对后续学习很重要。','理解这个原理有助于解决实际问题。'])}` :
                 '系统提示信息';
  msgData.push([sessionId, role, content, 'NULL', 'NULL', 'NULL']);
}
emitInsert('t_agent_message', 'session_id, role, content, tool_name, tool_args, tool_result', msgData);
console.error(`Generated ${msgData.length} agent messages`);

// ===== 26-27. t_paper, t_paper_author (新增论文和作者) =====
emitComment('26. 论文表扩展 (t_paper)');

const paperData = [
  ['2006.16668', 'Scaling Vision Transformers', 'We present a study of scaling Vision Transformers (ViT) and demonstrate that ViT can achieve competitive performance with state-of-the-art convolutional networks when properly scaled.', 2022, 'https://arxiv.org/abs/2006.16668', 'https://arxiv.org/pdf/2006.16668.pdf', 'CVPR 2022', 4500, 'arxiv'],
  ['2107.03374', 'What Makes for Good Unsupervised Data Augmentation?', 'We systematically study the effect of data augmentation strategies in unsupervised learning and propose a framework for understanding when and why augmentation helps.', 2021, 'https://arxiv.org/abs/2107.03374', 'https://arxiv.org/pdf/2107.03374.pdf', 'ICLR 2022', 3200, 'arxiv'],
  ['2203.02155', 'Training Data-Efficient Image Transformers via Distillation', 'We propose a method for training vision transformers that requires significantly less training data by leveraging knowledge distillation from convolutional neural networks.', 2022, 'https://arxiv.org/abs/2203.02155', 'https://arxiv.org/pdf/2203.02155.pdf', 'NeurIPS 2022', 2800, 'arxiv'],
  ['2211.11559', 'Segment Anything', 'We introduce the Segment Anything (SA) project: a new task, model, and dataset for image segmentation. Our model is designed to be promptable so it can transfer to new image distributions and tasks.', 2023, 'https://arxiv.org/abs/2211.11559', 'https://arxiv.org/pdf/2211.11559.pdf', 'ICCV 2023', 15000, 'arxiv'],
  ['2303.08774', 'GPT-4 Technical Report', 'We report the development of GPT-4, a large multimodal model capable of processing image and text inputs and producing text outputs. GPT-4 exhibits human-level performance on various professional benchmarks.', 2023, 'https://arxiv.org/abs/2303.08774', 'https://arxiv.org/pdf/2303.08774.pdf', 'arXiv preprint', 35000, 'arxiv'],
  ['2306.03307', 'Tree of Thoughts: Deliberate Problem Solving with Large Language Models', 'We introduce Tree of Thoughts (ToT), a framework that generalizes chain-of-thought prompting by enabling exploration over coherent units of text (thoughts) that serve as intermediate steps for problem solving.', 2023, 'https://arxiv.org/abs/2306.03307', 'https://arxiv.org/pdf/2306.03307.pdf', 'NeurIPS 2023', 8500, 'arxiv'],
  ['2305.18290', 'Direct Preference Optimization', 'We introduce Direct Preference Optimization (DPO), a new paradigm for aligning language models with human preferences without reinforcement learning.', 2023, 'https://arxiv.org/abs/2305.18290', 'https://arxiv.org/pdf/2305.18290.pdf', 'NeurIPS 2023', 12000, 'arxiv'],
  ['2310.06825', 'Mixture of Experts Explained', 'We provide a comprehensive explanation of Mixture of Experts (MoE) architectures in deep learning, covering routing mechanisms, load balancing, and training stability.', 2023, 'https://arxiv.org/abs/2310.06825', 'https://arxiv.org/pdf/2310.06825.pdf', 'arXiv preprint', 2100, 'arxiv'],
  ['2401.04088', 'An Introduction to Vision-Language Models', 'We present a comprehensive survey of vision-language models, covering their architecture, training objectives, and applications across various multimodal tasks.', 2024, 'https://arxiv.org/abs/2401.04088', 'https://arxiv.org/pdf/2401.04088.pdf', 'arXiv preprint', 1800, 'arxiv'],
  ['2403.08763', 'Graph Neural Networks: A Comprehensive Survey', 'This survey provides a comprehensive overview of graph neural networks, covering foundational concepts, various architectures, training methods, and diverse applications.', 2024, 'https://arxiv.org/abs/2403.08763', 'https://arxiv.org/pdf/2403.08763.pdf', 'ACM Computing Surveys', 3200, 'arxiv'],
  ['1901.02985', 'Generative Pre-Training with Weak Supervision', 'We explore the effectiveness of generative pre-training combined with weak supervision for natural language understanding tasks.', 2019, 'https://arxiv.org/abs/1901.02985', 'https://arxiv.org/pdf/1901.02985.pdf', 'NAACL 2019', 8900, 'arxiv'],
  ['1907.02893', 'RoBERTa: A Robustly Optimized BERT Pretraining Approach', 'We present a replication study of BERT pretraining that carefully measures the impact of many key hyperparameters and training data size.', 2019, 'https://arxiv.org/abs/1907.02893', 'https://arxiv.org/pdf/1907.02893.pdf', 'arXiv preprint', 45000, 'arxiv'],
  ['1909.13719', 'ALBERT: A Lite BERT for Self-Supervised Learning', 'We present ALBERT, a lite version of BERT that achieves lower memory consumption and faster training speed through parameter-reduction techniques.', 2019, 'https://arxiv.org/abs/1909.13719', 'https://arxiv.org/pdf/1909.13719.pdf', 'ICLR 2020', 12000, 'arxiv'],
  ['2005.01910', 'Teaching with Language Models', 'We explore how language models can be used as teaching tools to help students learn complex concepts through interactive dialogue and personalized instruction.', 2020, 'https://arxiv.org/abs/2005.01910', 'https://arxiv.org/pdf/2005.01910.pdf', 'arXiv preprint', 1500, 'arxiv'],
  ['2006.09517', 'Intelligible and Explainable Machine Learning', 'We present methods for making machine learning models more interpretable and explainable, focusing on post-hoc explanations and inherently interpretable models.', 2020, 'https://arxiv.org/abs/2006.09517', 'https://arxiv.org/pdf/2006.09517.pdf', 'Machine Learning Journal', 2800, 'arxiv'],
  ['2010.11929', 'An Image is Worth 16x16 Words: Transformers for Image Recognition', 'We show that a pure transformer applied directly to sequences of image patches can perform very well on image classification tasks.', 2020, 'https://arxiv.org/abs/2010.11929', 'https://arxiv.org/pdf/2010.11929.pdf', 'ICLR 2021', 38000, 'arxiv'],
  ['2101.03961', 'Switch Transformers: Scaling to Trillion Parameter Models', 'We propose Switch Transformers, a sparse mixture-of-experts model that reduces communication and computation costs while scaling to trillions of parameters.', 2021, 'https://arxiv.org/abs/2101.03961', 'https://arxiv.org/pdf/2101.03961.pdf', 'ICML 2021', 9500, 'arxiv'],
  ['2105.02264', 'Limitations of Language Models in Educational Assessment', 'We analyze the limitations of current language models when applied to automated educational assessment tasks, including grading and feedback generation.', 2021, 'https://arxiv.org/abs/2105.02264', 'https://arxiv.org/pdf/2105.02264.pdf', 'AIED 2021', 890, 'arxiv'],
  ['2201.11903', 'Chain-of-Thought Prompting Elicits Reasoning', 'We explore how chain-of-thought reasoning prompts can significantly improve the ability of language models to perform complex reasoning tasks.', 2022, 'https://arxiv.org/abs/2201.11903', 'https://arxiv.org/pdf/2201.11903.pdf', 'NeurIPS 2022', 22000, 'arxiv'],
  ['2203.15556', 'Large Language Models are Human-Level Prompt Engineers', 'We show that large language models can automatically generate effective prompts for a wide range of tasks, matching or exceeding human performance.', 2022, 'https://arxiv.org/abs/2203.15556', 'https://arxiv.org/pdf/2203.15556.pdf', 'ICLR 2023', 4500, 'arxiv'],
  ['2205.10625', 'Adaptive Learning Systems: A Survey', 'This survey provides a comprehensive overview of adaptive learning systems in education, covering learner modeling, content adaptation, and evaluation methods.', 2022, 'https://arxiv.org/abs/2205.10625', 'https://arxiv.org/pdf/2205.10625.pdf', 'IEEE TLT', 1200, 'arxiv'],
  ['2206.04615', 'Learning Analytics in Higher Education', 'We review the current state of learning analytics in higher education, discussing data sources, analytical methods, privacy concerns, and practical applications.', 2022, 'https://arxiv.org/abs/2206.04615', 'https://arxiv.org/pdf/2206.04615.pdf', 'Computers & Education', 2100, 'arxiv'],
  ['2301.12597', 'Towards Automated Student Feedback Generation', 'We investigate the use of large language models for generating personalized feedback on student assignments and discuss the opportunities and challenges.', 2023, 'https://arxiv.org/abs/2301.12597', 'https://arxiv.org/pdf/2301.12597.pdf', 'AIED 2023', 670, 'arxiv'],
  ['2306.07951', 'LoRA: Low-Rank Adaptation of Large Language Models', 'We propose Low-Rank Adaptation (LoRA), a method that significantly reduces the number of trainable parameters for fine-tuning large language models while maintaining quality.', 2023, 'https://arxiv.org/abs/2306.07951', 'https://arxiv.org/pdf/2306.07951.pdf', 'ICLR 2023', 25000, 'arxiv'],
  ['2310.01564', 'Generative AI in Education: Opportunities and Challenges', 'This paper examines the potential transformative impact of generative AI on education, discussing both the opportunities and the challenges that need to be addressed.', 2023, 'https://arxiv.org/abs/2310.01564', 'https://arxiv.org/pdf/2310.01564.pdf', 'arXiv preprint', 3400, 'arxiv'],
  ['2312.02115', 'Enhancing Programming Education with AI-Powered Tools', 'We present an AI-powered programming education tool that provides real-time feedback and personalized learning paths for students learning to code.', 2023, 'https://arxiv.org/abs/2312.02115', 'https://arxiv.org/pdf/2312.02115.pdf', 'SIGCSE 2024', 450, 'arxiv'],
  ['2402.06070', 'Retrieval-Augmented Generation for Knowledge-Intensive Tasks', 'We propose retrieval-augmented generation (RAG) methods that combine pre-trained language models with external knowledge retrieval for knowledge-intensive tasks.', 2024, 'https://arxiv.org/abs/2402.06070', 'https://arxiv.org/pdf/2402.06070.pdf', 'arXiv preprint', 18000, 'arxiv'],
  ['2404.07123', 'Automated Essay Scoring with Large Language Models', 'We investigate the effectiveness of large language models for automated essay scoring and demonstrate competitive performance with traditional approaches.', 2024, 'https://arxiv.org/abs/2404.07123', 'https://arxiv.org/pdf/2404.07123.pdf', 'arXiv preprint', 230, 'arxiv'],
];

const paperRows2 = [];
paperData.forEach((p, idx) => {
  paperRows2.push([p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8]]);
});

// Add more papers to reach 100+
const venues = ['NeurIPS','ICML','ICLR','CVPR','ICCV','ACL','EMNLP','NAACL','AAAI','IJCAI','SIGIR','WWW','KDD','VLDB','OSDI','SOSP','PLDI','POPL','OOPSLA','ISSTA','FSE','ASE','ICSE','STOC','FOCS','SODA','PODS','SIGMOD','ICDE','ECCV','ICRA','IROS','RSS','ACM Computing Surveys','IEEE TPAMI','IEEE TNNLS','JMLR','AI Journal','Nature Machine Intelligence','Science Robotics','arXiv preprint'];
const venues2 = ['AIED','EDM','ITS','LAK','ICALT','ICLS','CSCL','ICCE','ECTEL','IEEE TLT','BJET','Computers & Education','Educational Technology Research'];

for (let i = 8 + paperData.length; i < 108; i++) {
  const year = randInt(2018, 2025);
  const prefix = String(year).slice(2);
  const num = String(randInt(10000, 99999)).padStart(5, '0');
  const arxivId = `${prefix}${num}`;
  const title = `Research Paper ${i + 1}: ${pick(['Deep Learning', 'Machine Learning', 'AI', 'Data Science', 'Computer Vision', 'NLP', 'Education Technology', 'Knowledge Representation', 'Pattern Recognition', 'Neural Networks', 'Reinforcement Learning', 'Transfer Learning', 'Federated Learning', 'Explainable AI', 'Edge Computing', 'Cloud Computing', 'Software Engineering', 'Formal Methods', 'Human-Computer Interaction'])} ${pick(['Methods', 'Applications', 'Survey', 'Analysis', 'Systems', 'Approaches', 'Frameworks', 'Techniques', 'Models', 'Architectures'])}`;
  const abstract = `This paper presents a novel approach to ${title.toLowerCase()}. We propose new methods and evaluate them extensively on benchmark datasets. Our results show significant improvements over existing approaches.`;
  const url = `https://arxiv.org/abs/${arxivId}`;
  const pdfUrl = `https://arxiv.org/pdf/${arxivId}.pdf`;
  const venue = i % 2 === 0 ? pick(venues) : pick(venues2);
  const citationCount = randInt(10, 5000);
  paperRows2.push([arxivId, title, abstract, year, url, pdfUrl, venue, citationCount, 'arxiv']);
}

emitInsert('t_paper', 'arxiv_id, title, abstract_text, year, url, pdf_url, venue, citation_count, source', paperRows2);
const totalPapers2 = 8 + paperRows2.length;
console.error(`Generated ${paperRows2.length} papers (total: ${totalPapers2})`);

// ===== 27. t_paper_author (新增论文作者) =====
emitComment('27. 论文作者表扩展 (t_paper_author)');
const authorNames = [
  'Ashish Vaswani', 'Noam Shazeer', 'Niki Parmar', 'Jakob Uszkoreit', 'Llion Jones',
  'Aidan N. Gomez', 'Lukasz Kaiser', 'Illia Polosukhin', 'Kaiming He', 'Xiangyu Zhang',
  'Shaoqing Ren', 'Jian Sun', 'Jacob Devlin', 'Ming-Wei Chang', 'Kenton Lee',
  'Kristina Toutanova', 'Ian J. Goodfellow', 'Jean Pouget-Abadie', 'Mehdi Mirza', 'Bing Xu',
  'David Warde-Farley', 'Sherjil Ozair', 'Aaron Courville', 'Yoshua Bengio', 'Alec Radford',
  'Jong Wook Kim', 'Chris Hallacy', 'Aditya Ramesh', 'Gabriel Goh', 'Ilya Sutskever',
  'Tom B. Brown', 'Benjamin Mann', 'Nick Ryder', 'Melanie Subbiah', 'Jared Kaplan',
  'Prafulla Dhariwal', 'Dario Amodei', 'Daniel M. Ziegler', 'Yonghui Wu', 'Mike Schuster',
  'Zhifeng Chen', 'Quoc V. Le', 'Mingxing Tan', 'Andrej Karpathy', 'Fei-Fei Li',
  'Yann LeCun', 'Geoffrey Hinton', 'Andrew Ng', 'Michael I. Jordan', 'Christopher Manning',
  'Dan Jurafsky', 'Percy Liang', 'Daphne Koller', 'Sebastian Thrun', 'Pieter Abbeel',
  'Sergey Levine', 'Chelsea Finn', 'John Schulman', 'Richard S. Sutton', 'David Silver',
  'Volodymyr Mnih', 'Demis Hassabis', 'Shane Legg', 'Oriol Vinyals', 'Koray Kavukcuoglu',
  'Ruslan Salakhutdinov', 'Joshua B. Tenenbaum', 'Brendan Frey', 'Zoubin Ghahramani', 'Neil D. Lawrence',
  'Bernhard Scholkopf', 'Nando de Freitas', 'Raia Hadsell', 'Joelle Pineau', 'Doina Precup',
  'Yoshua Bengio', 'Aaron Courville', 'Pascal Vincent', 'Hugo Larochelle', 'Nicolas Le Roi',
  'Jason Weston', 'Antoine Bordes', 'Tomas Mikolov', 'Kyunghyun Cho', 'Dzmitry Bahdanau',
  'Bowman Samuel', 'Christopher D. Manning', 'Danqi Chen', 'Richard Socher', 'James W. Pennebaker',
  'Luke Zettlemoyer', 'Noah A. Smith', 'Kevin Gimpel', 'Matt Gardner', 'Jonathan Berant',
  'Zhong Zhang', 'Wei Wang', 'Yu Sun', 'Li Deng', 'Xiao Li',
  'Jianfeng Gao', 'Yelong Shen', 'Ming Zhou', 'Tao Qin', 'Tie-Yan Liu',
];
const authorRows2 = [];
for (let paperId = 9; paperId <= totalPapers2; paperId++) {
  const numAuthors = randInt(1, 6);
  const selected = pickN(authorNames, numAuthors);
  selected.forEach((name, idx) => {
    authorRows2.push([paperId, name, idx + 1]);
  });
}
emitInsert('t_paper_author', 'paper_id, name, sort_order', authorRows2);
console.error(`Generated ${authorRows2.length} paper_authors`);

// ============ FINAL OUTPUT ============
// The outputLines already contains all the SQL

// Print the result
console.log(outputLines.join('\n'));

console.error('\n=== Generation Summary ===');
console.error(`Roles: ${3 + roleData.length}`);
console.error(`Users: ${20 + newUsers.length}`);
console.error(`UserRoles: ${20 + newUserRoles.length}`);
console.error(`Categories: ${10 + categoryNames.length}`);
console.error(`Courses: ${13 + uniqueCourses.length}`);
console.error(`Chapters: ${27 + actualChapterRows.length}`);
console.error(`Lessons: ${44 + actualLessonRows.length}`);
console.error(`TeachingClasses: ${10 + teachingClasses.length}`);
console.error(`ClassStudents: ${36 + classStudentRows.length}`);
console.error(`LearningProgress: ${19 + progressRows.length}`);
console.error(`Homeworks: ${8 + homeworkRows.length}`);
console.error(`HomeworkSubmits: ${9 + hwSubmitRows.length}`);
console.error(`Questions: ${38 + questionRows.length}`);
console.error(`ExamPapers: ${9 + paperRows.length}`);
console.error(`PaperQuestions: ${36 + epqRows.length}`);
console.error(`ExamSessions: ${8 + sessionRows.length}`);
console.error(`ExamRecords: ${12 + recordRows.length}`);
console.error(`ExamAnswers: ${33 + answerRows.length}`);
console.error(`GradeRecords: ${18 + gradeRows.length}`);
console.error(`GradeImportLogs: ${5 + importLogRows.length}`);
console.error(`Notices: ${11 + noticeRows.length}`);
console.error(`OperationLogs: ${21 + logRows.length}`);
console.error(`Agents: ${1 + fullAgentRows.length}`);
console.error(`AgentSessions: ${sessionData.length}`);
console.error(`AgentMessages: ${msgData.length}`);
console.error(`Papers: ${8 + paperRows2.length}`);
console.error(`PaperAuthors: ${46 + authorRows2.length}`);
