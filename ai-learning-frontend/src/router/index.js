import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'

export const layoutChildren = [
  // Public route inside layout
  {
    path: '',
    name: 'PublicHome',
    component: () => import('../views/PublicHome.vue'),
    meta: { title: '首页', icon: 'House', noAuth: true }
  },

  // Internal Dashboard (Teacher/Admin default)
  {
    path: 'home',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: { title: '控制台', icon: 'House', requiresAuth: true }
  },

  // Teacher routes
  {
    path: 'courses',
    name: 'CourseList',
    component: () => import('../views/teacher/CourseList.vue'),
    meta: { title: '课程管理', icon: 'Reading', roles: ['admin', 'teacher'], requiresAuth: true }
  },
  {
    path: 'courses/create',
    name: 'CourseCreate',
    component: () => import('../views/teacher/CourseForm.vue'),
    meta: { title: '新建课程', hidden: true, roles: ['admin', 'teacher'], requiresAuth: true }
  },
  {
    path: 'courses/:id',
    name: 'CourseDetail',
    component: () => import('../views/teacher/CourseDetail.vue'),
    meta: { title: '课程详情', hidden: true, roles: ['admin', 'teacher', 'student'], requiresAuth: true }
  },
  {
    path: 'teaching-classes',
    name: 'TeachingClassList',
    component: () => import('../views/teacher/TeachingClassList.vue'),
    meta: { title: '教学班管理', icon: 'School', roles: ['admin', 'teacher'], requiresAuth: true }
  },
  {
    path: 'questions',
    name: 'QuestionList',
    component: () => import('../views/teacher/QuestionList.vue'),
    meta: { title: '题库管理', icon: 'Edit', roles: ['admin', 'teacher'], requiresAuth: true }
  },
  {
    path: 'papers',
    name: 'PaperList',
    component: () => import('../views/teacher/PaperList.vue'),
    meta: { title: '试卷管理', icon: 'Document', roles: ['admin', 'teacher'], requiresAuth: true }
  },
  {
    path: 'exams',
    name: 'ExamList',
    component: () => import('../views/teacher/ExamList.vue'),
    meta: { title: '考试管理', icon: 'Checked', roles: ['admin', 'teacher'], requiresAuth: true }
  },
  {
    path: 'homeworks',
    name: 'HomeworkList',
    component: () => import('../views/teacher/HomeworkList.vue'),
    meta: { title: '作业管理', icon: 'Tickets', roles: ['admin', 'teacher'], requiresAuth: true }
  },
  {
    path: 'homework/:id/submissions',
    name: 'HomeworkSubmissions',
    component: () => import('../views/teacher/HomeworkSubmissions.vue'),
    meta: { title: '作业批改', hidden: true, roles: ['admin', 'teacher'], requiresAuth: true }
  },
  {
    path: 'exams/:id/grading',
    name: 'ExamGrading',
    component: () => import('../views/teacher/ExamGrading.vue'),
    meta: { title: '考试批改', hidden: true, roles: ['admin', 'teacher'], requiresAuth: true }
  },
  {
    path: 'resource-gen',
    name: 'IntelligentResourceGen',
    component: () => import('../views/teacher/IntelligentResourceGen.vue'),
    meta: { title: '智能资源生成', icon: 'Lightning', roles: ['admin', 'teacher'], requiresAuth: true }
  },
  {
    path: 'teacher-knowledge-base',
    name: 'TeacherKnowledgeBase',
    component: () => import('../views/teacher/TeacherKnowledgeBase.vue'),
    meta: { title: '知识库管理', icon: 'Collection', roles: ['admin', 'teacher'], requiresAuth: true }
  },
  {
    path: 'agent-chat',
    name: 'AgentChat',
    component: () => import('../views/AiChat.vue'),
    meta: { title: 'Agent 助手', icon: 'Cpu', roles: ['admin', 'teacher', 'student'], requiresAuth: true, hidden: true, fullWidth: true }
  },

  // Student routes
  {
    path: 'my-classes',
    name: 'MyClassList',
    component: () => import('../views/student/MyClassList.vue'),
    meta: { title: '我的班级', icon: 'School', roles: ['admin', 'student'], requiresAuth: true }
  },
  {
    path: 'course-learn/:courseId',
    name: 'CourseLearn',
    component: () => import('../views/student/CourseLearn.vue'),
    meta: { title: '课程学习', hidden: true, roles: ['admin', 'student'], requiresAuth: true }
  },
  {
    path: 'my-homeworks',
    name: 'MyHomeworkList',
    component: () => import('../views/student/MyHomeworkList.vue'),
    meta: { title: '我的作业', icon: 'Tickets', roles: ['admin', 'student'], requiresAuth: true }
  },
  {
    path: 'my-exams',
    name: 'MyExamList',
    component: () => import('../views/student/MyExamList.vue'),
    meta: { title: '我的考试', icon: 'Checked', roles: ['admin', 'student'], requiresAuth: true }
  },
  {
    path: 'exam-taking/:examId',
    name: 'ExamTaking',
    component: () => import('../views/student/ExamTaking.vue'),
    meta: { title: '考试答题', hidden: true, roles: ['admin', 'student'], requiresAuth: true }
  },
  {
    path: 'exam-result/:submissionId',
    name: 'ExamResult',
    component: () => import('../views/student/ExamResult.vue'),
    meta: { title: '考试结果', hidden: true, roles: ['admin', 'student'], requiresAuth: true }
  },
  {
    path: 'my-grades',
    name: 'MyGrades',
    component: () => import('../views/student/MyGrades.vue'),
    meta: { title: '我的成绩', icon: 'DataLine', roles: ['admin', 'student'], requiresAuth: true }
  },
  {
    path: 'recommendation',
    name: 'IntelligentRecommendation',
    component: () => import('../views/student/IntelligentRecommendation.vue'),
    meta: { title: '智能推荐', icon: 'Aim', roles: ['admin', 'student'], requiresAuth: true }
  },
  {
    path: 'paper-search',
    name: 'PaperSearch',
    component: () => import('../views/student/PaperSearch.vue'),
    meta: { title: '论文查询', icon: 'Search', roles: ['admin', 'teacher', 'student'] }
  },
  {
    path: 'knowledge-base',
    name: 'KnowledgeBase',
    component: () => import('../views/student/KnowledgeBase.vue'),
    meta: { title: '智能学伴', icon: 'Reading', roles: ['admin', 'teacher', 'student'], requiresAuth: true }
  },
  {
    path: 'knowledge-graph',
    name: 'KnowledgeGraph',
    component: () => import('../views/student/KnowledgeGraph.vue'),
    meta: { title: '知识图谱', icon: 'Share', roles: ['admin', 'teacher', 'student'], requiresAuth: true }
  },
  {
    path: 'work-study-jobs',
    name: 'WorkStudyJobs',
    component: () => import('../views/student/WorkStudyList.vue'),
    meta: { title: '勤工俭学', icon: 'Suitcase', roles: ['admin', 'student'], requiresAuth: true }
  },
  {
    path: 'my-work-study',
    name: 'MyWorkStudy',
    component: () => import('../views/student/MyWorkStudy.vue'),
    meta: { title: '我的岗位', icon: 'Wallet', roles: ['admin', 'student'], requiresAuth: true }
  },

  // Admin routes
  {
    path: 'users',
    name: 'UserManage',
    component: () => import('../views/admin/UserManage.vue'),
    meta: { title: '用户管理', icon: 'User', roles: ['admin'], requiresAuth: true }
  },
  {
    path: 'categories',
    name: 'CategoryManage',
    component: () => import('../views/admin/CategoryManage.vue'),
    meta: { title: '分类管理', icon: 'Menu', roles: ['admin'], requiresAuth: true }
  },
  {
    path: 'work-studies',
    name: 'WorkStudyManage',
    component: () => import('../views/admin/WorkStudyManage.vue'),
    meta: { title: '勤工俭学岗位', icon: 'Suitcase', roles: ['admin', 'teacher'], requiresAuth: true }
  },
  {
    path: 'work-study-students',
    name: 'WorkStudyStudentManage',
    component: () => import('../views/admin/WorkStudyStudentManage.vue'),
    meta: { title: '劳务费名单', icon: 'Wallet', roles: ['admin', 'teacher'], requiresAuth: true }
  },
]

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { noAuth: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue'),
    meta: { noAuth: true }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    children: layoutChildren
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

function canAccess(to, userStore) {
  const roles = to.meta.roles
  if (!roles || roles.length === 0) return true
  if (userStore.roleCode === 'admin') return true
  return roles.includes(userStore.roleCode)
}

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const isPublic = to.meta.noAuth || to.matched.some(record => record.meta.noAuth)
  const requiresAuth = !isPublic && (to.meta.requiresAuth || to.matched.some(record => record.meta.requiresAuth))

  // Check authentication
  if (requiresAuth && !userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }

  // Check role permission
  if (requiresAuth && !canAccess(to, userStore)) {
    ElMessage.error('无权限访问此页面')
    return next('/')
  }

  next()
})

export default router
