<template>
  <div class="workspace-page">
    <PageHeader
      title="知识图谱"
      description="选择课程查看资料中的知识点和关系。"
    />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <section class="content-panel" v-loading="loading">
      <el-select
        v-if="!user.isAdmin"
        v-model="selectedCourse"
        filterable
        placeholder="选择课程"
        aria-label="图谱课程"
        @change="openCourse"
      >
        <el-option
          v-for="course in courses"
          :key="course.id"
          :value="Number(course.id)"
          :label="course.title"
        />
      </el-select>
      <el-button
        @click="router.push(user.isStudent ? '/my-classes' : '/courses')"
        >{{ user.isStudent ? '我的班级' : '课程管理' }}</el-button
      >
    </section>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getCourses } from '@/utils/course'
import { getKnowledgeBase, unwrapRag } from '@/api/rag'
import { requireSuccess, getRecords, getTotal } from '@/utils/apiHelpers'
import PageHeader from '@/components/ui/PageHeader.vue'
const route = useRoute(),
  router = useRouter(),
  user = useUserStore()
const courses = ref([]),
  loading = ref(false),
  error = ref(''),
  selectedCourse = ref(null)
const openCourse = (courseId) =>
  router.replace({ path: `/courses/${courseId}`, query: { tab: 'graph' } })
onMounted(async () => {
  if (user.isAdmin) {
    router.replace('/courses')
    return
  }
  const courseId = Number(route.query.courseId)
  if (courseId > 0) {
    openCourse(courseId)
    return
  }
  loading.value = true
  try {
    if (route.query.kbId && user.isTeacher) {
      const base = unwrapRag(await getKnowledgeBase(String(route.query.kbId)))
      const assigned = Number(base.courseId || base.course_id)
      if (assigned > 0) {
        openCourse(assigned)
        return
      }
      router.replace({
        path: '/teacher-knowledge-base',
        query: { kbId: route.query.kbId },
      })
      return
    }
    let pageNum = 1,
      total
    do {
      const response = requireSuccess(
        await getCourses({
          pageNum,
          pageSize: 100,
          box: user.isTeacher ? 'all' : 'completed',
        }),
      )
      const rows = getRecords(response.data)
      courses.value.push(...rows)
      total = getTotal(response.data)
      pageNum++
      if (!rows.length) break
    } while (courses.value.length < total)
  } catch (problem) {
    error.value =
      problem?.response?.data?.msg || problem.message || '课程读取失败，请重试'
  } finally {
    loading.value = false
  }
})
</script>
<style scoped>
.content-panel {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 18px;
}
.el-select {
  width: min(360px, 100%);
}
</style>
