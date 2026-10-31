// ============================================
// 论文检索功能 - 路由配置
// 添加到 router/index.js 的 routes 数组中
// ============================================

{
  path: '/paper-search',
  name: 'PaperSearch',
  component: () => import('../views/PaperSearch.vue'),
  meta: { title: '学术论文搜索' }
}
