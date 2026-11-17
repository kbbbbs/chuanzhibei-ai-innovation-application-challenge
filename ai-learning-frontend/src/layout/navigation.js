// Task-oriented navigation per role. Titles and permissions stay on the route records;
// this file only decides how each role sees them.
const NAVIGATION = {
  student: [
    { id: 'overview', title: '概览', items: ['console'] },
    { id: 'study', title: '学习', items: ['my-classes', 'learning-routes', 'my-homeworks', 'my-exams', 'my-grades'] },
    { id: 'ai-companion', title: 'AI 学伴', items: ['knowledge-base', 'recommendation', 'paper-search'] },
    { id: 'campus', title: '校园事务', items: [{ path: 'notices', title: '通知公告' }, 'work-study-jobs', 'my-work-study'] },
  ],
  teacher: [
    { id: 'overview', title: '概览', items: ['console'] },
    { id: 'course', title: '课程与班级', items: ['courses', 'teaching-classes'] },
    { id: 'assessment', title: '作业与考试', items: ['questions', 'papers', 'homework-papers', 'homeworks', 'exams', 'grade-analysis'] },
    { id: 'ai-teaching', title: 'AI 备课', items: [{ path: 'knowledge-base', title: '智能助手' }, 'paper-search'] },
    { id: 'work-study', title: '勤工俭学', items: ['work-studies', 'work-study-students'] },
    { id: 'records', title: '通知与记录', items: ['notices', 'operation-logs'] },
  ],
  admin: [
    { id: 'overview', title: '概览', items: ['console'] },
    { id: 'system', title: '系统配置', items: ['settings/models', 'settings/assistants', 'users', 'teacher-applications', 'operation-logs'] },
    { id: 'teaching-ops', title: '教学运营', items: ['courses', 'teaching-classes', 'administrative-classes', 'categories', 'home-course-recommendations', 'notices'] },
    { id: 'assessment', title: '作业与考试', collapsed: true, items: ['questions', 'papers', 'homework-papers', 'homeworks', 'exams', 'grade-analysis'] },
    { id: 'ai-knowledge', title: '知识与 AI', collapsed: true, items: [{ path: 'knowledge-base', title: '智能助手' }, 'paper-search'] },
    { id: 'work-study', title: '勤工俭学', collapsed: true, items: ['work-studies', 'work-study-students'] },
  ],
}

// Detail routes have no menu entry of their own; they highlight and breadcrumb under a list page.
const DETAIL_PARENTS = {
  CourseCreate: 'courses',
  CourseDetail: 'courses',
  TeacherKnowledgeBase: 'courses',
  IntelligentResourceGen: 'courses',
  ExamGrading: 'exams',
  ExamResults: 'exams',
  HomeworkSubmissions: 'homeworks',
  LearningMap: 'learning-routes',
  CourseLearn: 'my-classes',
  KnowledgeGraph: 'knowledge-base',
}

function assessmentParent(route) {
  return route.query?.assessmentType === 'HOMEWORK' ? 'my-homeworks' : 'my-exams'
}

function menuPath(path) {
  return `/${path}`
}

export function buildNavigation(role, routes) {
  const records = new Map(routes.map(record => [record.path, record]))
  return (NAVIGATION[role] || [])
    .map(group => ({
      ...group,
      items: group.items.flatMap(item => {
        const entry = typeof item === 'string' ? { path: item } : item
        const record = records.get(entry.path)
        const allowed = record && (!record.meta?.roles || record.meta.roles.includes(role))
        if (!allowed) return []
        return [{
          path: menuPath(entry.path),
          title: entry.title || record.meta.title,
          icon: record.meta.icon || 'Menu',
        }]
      }),
    }))
    .filter(group => group.items.length)
}

export function resolveActivePath(route) {
  if (['ExamTaking', 'ExamResult'].includes(route.name)) return menuPath(assessmentParent(route))
  const parent = DETAIL_PARENTS[route.name]
  return parent ? menuPath(parent) : route.path
}

export function findNavigationEntry(groups, path) {
  for (const group of groups) {
    const item = group.items.find(entry => entry.path === path)
    if (item) return { group, item }
  }
  return null
}

export function filterNavigation(groups, keyword) {
  const value = keyword.trim().toLowerCase()
  if (!value) return groups
  return groups
    .map(group => ({
      ...group,
      collapsed: false,
      items: group.items.filter(item => `${group.title} ${item.title}`.toLowerCase().includes(value)),
    }))
    .filter(group => group.items.length)
}
