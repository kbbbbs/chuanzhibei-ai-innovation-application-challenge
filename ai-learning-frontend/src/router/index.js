import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'

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
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('../views/Home.vue'),
        meta: { title: '首页', icon: 'House' }
      },

      // Teacher routes
      {
        path: 'courses',
        name: 'CourseList',
        component: () => import('../views/teacher/CourseList.vue'),
        meta: { title: '课程管理', icon: 'Reading', roles: ['admin', 'teacher'] }
      },
      {
        path: 'courses/create',
        name: 'CourseCreate',
        component: () => import('../views/teacher/CourseForm.vue'),
        meta: { title: '新建课程', hidden: true, roles: ['teacher'] }
      },
      {
        path: 'courses/:id',
        name: 'CourseDetail',
        component: () => import('../views/teacher/CourseDetail.vue'),
        meta: { title: '课程详情', hidden: true, roles: ['teacher'] }
      },
      {
        path: 'teaching-classes',
        name: 'TeachingClassList',
        component: () => import('../views/teacher/TeachingClassList.vue'),
        meta: { title: '教学班管理', icon: 'School', roles: ['admin', 'teacher'] }
      },
      {
        path: 'questions',
        name: 'QuestionList',
        component: () => import('../views/teacher/QuestionList.vue'),
        meta: { title: '题库管理', icon: 'Edit', roles: ['admin', 'teacher'] }
      },
      {
        path: 'papers',
        name: 'PaperList',
        component: () => import('../views/teacher/PaperList.vue'),
        meta: { title: '试卷管理', icon: 'Document', roles: ['admin', 'teacher'] }
      },
      {
        path: 'exams',
        name: 'ExamList',
        component: () => import('../views/teacher/ExamList.vue'),
        meta: { title: '考试管理', icon: 'Checked', roles: ['admin', 'teacher'] }
      },
      {
        path: 'homeworks',
        name: 'HomeworkList',
        component: () => import('../views/teacher/HomeworkList.vue'),
        meta: { title: '作业管理', icon: 'Tickets', roles: ['admin', 'teacher'] }
      },

      // Student routes
      {
        path: 'my-classes',
        name: 'MyClassList',
        component: () => import('../views/student/MyClassList.vue'),
        meta: { title: '我的班级', icon: 'School', roles: ['student'] }
      },
      {
        path: 'my-homeworks',
        name: 'MyHomeworkList',
        component: () => import('../views/student/MyHomeworkList.vue'),
        meta: { title: '我的作业', icon: 'Tickets', roles: ['student'] }
      },
      {
        path: 'my-exams',
        name: 'MyExamList',
        component: () => import('../views/student/MyExamList.vue'),
        meta: { title: '我的考试', icon: 'Checked', roles: ['student'] }
      },
      {
        path: 'my-grades',
        name: 'MyGrades',
        component: () => import('../views/student/MyGrades.vue'),
        meta: { title: '我的成绩', icon: 'DataLine', roles: ['student'] }
      },

      // Admin routes
      {
        path: 'users',
        name: 'UserManage',
        component: () => import('../views/admin/UserManage.vue'),
        meta: { title: '用户管理', icon: 'User', roles: ['admin'] }
      },
      {
        path: 'categories',
        name: 'CategoryManage',
        component: () => import('../views/admin/CategoryManage.vue'),
        meta: { title: '分类管理', icon: 'Menu', roles: ['admin'] }
      },
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()

  // Check authentication
  if (!to.meta.noAuth && !userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return next('/login')
  }

  // Check role permission
  if (to.meta.roles && !to.meta.roles.includes(userStore.roleCode)) {
    ElMessage.error('无权限访问此页面')
    return next('/')
  }

  next()
})

export default router
