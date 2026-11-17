<template>
  <div class="preparation-page workspace-page">
    <header v-if="!courseId" class="preparation-header">
      <div class="preparation-heading">
        <span class="preparation-heading-icon" aria-hidden="true"><el-icon><Reading /></el-icon></span>
        <div class="preparation-heading-copy">
          <div class="preparation-heading-meta">
            <span class="preparation-eyebrow">AI 备课</span>
            <span
              v-if="headerSaveStatus"
              class="draft-save-status"
              :class="`is-${headerSaveStatus.state}`"
              :data-testid="headerSaveStatus.testId"
              role="status"
            >
              <el-icon v-if="headerSaveStatus.state === 'saved'" aria-hidden="true"><Check /></el-icon>
              {{ headerSaveStatus.label }}
            </span>
          </div>
          <h1>{{ courseId ? '准备课程内容' : '智能备课工作台' }}</h1>
          <p>{{ courseId ? '用课程资料生成备课草稿，审核后应用到当前课程。' : '先生成草稿、逐阶段审核，最后写入课程、课时与题库。' }}</p>
        </div>
      </div>
      <div class="preparation-header-controls">
        <el-select v-model="selectedDraftId" class="draft-select" clearable filterable :disabled="contextBusy" aria-label="继续最近的备课草稿" placeholder="继续最近的备课草稿" @change="resumeDraft">
          <el-option
            v-for="draft in recentDrafts"
            :key="draft.id"
            :label="`${draft.title || draft.config?.title || '未命名草稿'} · ${stageLabel(draft.currentStage)}`"
            :value="draft.id"
          />
        </el-select>
        <div class="preparation-header-actions">
          <el-button :icon="Refresh" :loading="draftsLoading" @click="loadRecentDrafts">刷新</el-button>
          <el-button type="primary" plain :icon="Plus" :disabled="contextBusy" @click="startNewDraft">新备课</el-button>
          <slot name="secondary-actions" />
        </div>
      </div>
    </header>

    <div v-if="courseId" class="course-preparation-actions"><slot name="secondary-actions" /></div>

    <p v-if="contextBusy" class="context-lock-note" role="status">正在提交当前草稿，完成前不能切换草稿或新建备课。</p>

    <ErrorNotice
      v-if="visibleWriteError"
      :title="visibleWriteError.title"
      :message="visibleWriteError.message"
    >
      <template #actions><el-button size="small" @click="writeError = null">知道了</el-button></template>
    </ErrorNotice>
    <ErrorNotice
      v-if="draftsFailed"
      title="备课草稿列表未加载"
      :message="recentDrafts.length ? '下拉列表显示的是上次读取的草稿，当前编辑内容不受影响。' : '这不代表没有草稿，当前编辑内容不受影响。'"
      retry-label="重新加载"
      :busy="draftsLoading"
      @retry="loadRecentDrafts"
    />
    <ErrorNotice
      v-if="projectFailed"
      title="备课草稿未读取"
      message="当前页面内容已保留，没有被替换。"
      retry-label="重新读取"
      :busy="projectLoading"
      @retry="retryProject"
    />

    <el-alert
      v-if="project?.generationStatus === 'FAILED'"
      :title="project.errorMessage || '生成失败，可保留当前草稿并重试本阶段'"
      type="error"
      show-icon
      :closable="false"
    >
      <el-button v-if="project.outline?.chapters?.length" size="small" type="primary" :loading="regenerating" :disabled="contextBusy" data-testid="resume-generation" @click="resumeGeneration">继续生成未完成部分</el-button>
    </el-alert>

    <section class="content-panel workspace-card" aria-label="备课流程">
      <nav aria-label="备课步骤">
        <ol class="stage-steps">
          <li v-for="(step, index) in preparationSteps" :key="step.title" :class="{ 'is-complete': index < projectStep }">
            <button
              type="button"
              class="stage-step"
              :aria-current="activeStep === index ? 'step' : undefined"
              :aria-label="step.title"
              :disabled="changingStep || contextBusy || projectLoading || (creationMode && index === 4 && project?.projectStatus !== 'PUBLISHED')"
              @click="goToStep(index)"
            >
              <span class="stage-number" aria-hidden="true">{{ index + 1 }}</span>
              <span class="stage-title">{{ step.title }}</span>
              <span class="stage-description">{{ step.description }}</span>
            </button>
          </li>
        </ol>
      </nav>

      <div v-if="isGenerating" class="generation-banner" role="status">
        <div>
          <strong>{{ generatingLabel }}</strong>
          <span>可以离开页面，生成进度会保存在草稿中。</span>
        </div>
        <el-progress :percentage="project?.progress || 0" :stroke-width="10" />
      </div>

      <div v-if="!creationMode && readyForApplication && activeStep > 0 && activeStep < 4" class="stage-actions">
        <el-button :disabled="changingStep || contextBusy" @click="goToStep(4)">返回应用前检查</el-button>
      </div>

      <section v-if="activeStep === 0" class="stage-panel config-stage">
        <div class="section-title">
          <div><h3>{{ courseId ? '设置生成内容' : '定义新课程' }}</h3></div>
          <p>{{ courseId ? '课程信息和参考资料已自动带入，可补充本次教学要求。' : '课程仅在最终发布时创建，当前填写内容都保存在智能备课草稿中。' }}</p>
        </div>

        <ErrorNotice
          v-if="categoriesFailed"
          class="stage-notice"
          title="课程分类未加载"
          :message="config.categoryId ? '已选分类保持不变，可以继续生成。' : '课程分类是必填项，加载成功后才能生成。当前填写内容已保留。'"
          retry-label="重新加载"
          :busy="categoriesLoading"
          @retry="loadCategories"
        />

        <el-form ref="configFormRef" :model="config" :rules="configRules" :disabled="configLocked || changingStep" label-position="top">
          <div class="form-grid">
            <el-form-item v-if="!courseId" label="课程名称" prop="title">
              <el-input v-model="config.title" maxlength="200" show-word-limit placeholder="例如：数据结构与算法基础" />
            </el-form-item>
            <el-form-item v-if="!courseId" label="课程分类" prop="categoryId">
              <el-select v-model="config.categoryId" filterable :loading="categoriesLoading" placeholder="选择课程分类">
                <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
              <p v-if="categoriesEmpty" class="field-hint">暂无课程分类，请先在分类管理中创建。</p>
            </el-form-item>
            <el-form-item label="目标对象" prop="audience">
              <el-input v-model="config.audience" placeholder="例如：计算机专业大二本科生" />
            </el-form-item>
            <el-form-item v-if="!courseId" label="课程难度">
              <el-select v-model="config.difficulty">
                <el-option label="初级" value="beginner" />
                <el-option label="中级" value="intermediate" />
                <el-option label="高级" value="advanced" />
              </el-select>
            </el-form-item>
            <el-form-item label="总学时">
              <el-input-number v-model="config.totalHours" :min="1" :max="1000" />
            </el-form-item>
            <el-form-item label="期望章节数">
              <el-input-number v-model="config.chapterCount" :min="1" :max="20" />
            </el-form-item>
            <el-form-item label="每章课时数">
              <el-input-number v-model="config.lessonsPerChapter" :min="1" :max="10" />
            </el-form-item>
            <el-form-item label="每章题目数">
              <el-input-number v-model="config.questionConfig.countPerChapter" :disabled="!!courseId && !config.includeQuestions" :min="1" :max="20" />
            </el-form-item>
          </div>
          <el-form-item v-if="!courseId" label="课程简介">
            <el-input v-model="config.description" type="textarea" :rows="2" placeholder="课程定位与主要内容" />
          </el-form-item>
          <el-form-item label="教学目标">
            <el-input v-model="config.teachingGoals" type="textarea" :rows="3" placeholder="学生完成课程后应掌握的知识与能力" />
          </el-form-item>
          <el-form-item label="备课补充要求">
            <el-input v-model="config.extraInstructions" type="textarea" :rows="3" placeholder="例如：案例驱动，包含代码示例与课堂活动" />
          </el-form-item>

          <div v-if="!courseId" class="subsection" data-testid="generation-options">
            <div class="subsection-heading"><h4>生成方式与交付内容</h4><span>默认一次生成完整草稿，完成后统一检查</span></div>
            <el-form-item label="生成方式">
              <el-radio-group v-model="config.generationMode">
                <el-radio value="CONTINUOUS">一次生成完整草稿（可离开页面）</el-radio>
                <el-radio value="STEP">逐步审核（每个阶段确认后再继续）</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="交付内容">
              <el-checkbox :model-value="true" disabled>章节与课时正文</el-checkbox>
              <el-checkbox v-model="config.includeQuestions">章节题目</el-checkbox>
            </el-form-item>
          </div>

          <div v-if="courseId || config.includeQuestions" class="subsection">
            <div class="subsection-heading"><h4>题目生成设置</h4><span>{{ config.includeQuestions ? '题目将在课时完成后按章节生成' : '当前草稿未选择生成章节题目' }}</span></div>
            <el-form-item label="题型">
              <el-checkbox-group v-model="config.questionConfig.types" :disabled="!config.includeQuestions">
                <el-checkbox v-for="item in questionTypes" :key="item.value" :label="item.value">{{ item.label }}</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <div class="form-grid two-columns">
              <el-form-item label="默认难度">
                <el-select v-model="config.questionConfig.difficulty" :disabled="!config.includeQuestions">
                  <el-option label="简单" value="EASY" /><el-option label="中等" value="MEDIUM" /><el-option label="困难" value="HARD" />
                </el-select>
              </el-form-item>
              <el-form-item label="题目补充要求">
                <el-input v-model="config.questionConfig.instructions" :disabled="!config.includeQuestions" placeholder="例如：侧重理解与综合应用" />
              </el-form-item>
            </div>
          </div>

          <div v-if="!courseId" class="subsection source-section">
            <div class="subsection-heading"><h4>参考资料与知识库</h4><span>均为可选；不提供资料时按表单信息生成</span></div>
            <ErrorNotice
              v-if="knowledgeBasesFailed"
              class="stage-notice"
              title="知识库列表未加载"
              message="知识库是可选项，可以不选直接生成；已选的知识库不会被清除。"
              retry-label="重新加载"
              :busy="knowledgeBasesLoading"
              @retry="loadKnowledgeBases"
            />
            <el-form-item label="已有知识库">
              <el-select v-model="config.knowledgeBaseIds" multiple filterable collapse-tags :loading="knowledgeBasesLoading" placeholder="选择一个或多个已有知识库">
                <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name || kb.title || kb.id" :value="String(kb.id)" />
              </el-select>
            </el-form-item>
            <el-upload
              :auto-upload="false"
              :file-list="uploadFileList"
              :on-change="handleUploadChange"
              :on-remove="handleUploadRemove"
              multiple
              accept=".pdf,.ppt,.pptx,.doc,.docx,.txt,.md"
            >
              <el-button :icon="Upload">选择教材或课件</el-button>
              <template #tip><div class="el-upload__tip">首次上传时会自动创建本备课项目的专属知识库。</div></template>
            </el-upload>
            <div v-if="project?.sourceFiles?.length" class="source-status-list" data-testid="source-status-list" aria-live="polite">
              <p v-if="waitingForSources" class="source-wait">资料处理中，有可用内容后会自动开始生成，可以留在本页等待。</p>
              <div v-for="file in project.sourceFiles" :key="file.knowledgeId || file.name">
                <span>{{ file.name }}</span>
                <el-tag size="small" :type="sourceStatus(file).type">{{ sourceStatus(file).label }}</el-tag>
                <small v-if="file.availability === 'FAILED' && file.errorMessage" class="source-error">{{ file.errorMessage }}</small>
              </div>
              <div class="source-links">
                <el-button size="small" link type="primary" @click="refreshProject">刷新状态</el-button>
                <el-button v-if="project.ownedKnowledgeBaseId && project.sourceFiles.some(file => ['FAILED', 'PARTIAL'].includes(file.availability))" size="small" link type="primary" @click="openCourseMaterials">到知识库补处理</el-button>
              </div>
            </div>
          </div>
          <section v-if="courseId" class="subsection" data-testid="course-preparation-sources">
            <div class="subsection-heading"><h4>本课程参考资料</h4><el-button link type="primary" @click="openCourseMaterials">管理课程资料</el-button></div>
            <p v-if="courseSourceLoading">正在读取课程资料…</p>
            <el-alert v-else-if="courseSourceError" :title="courseSourceError" type="error" :closable="false"><el-button @click="loadCourseContext">重新加载</el-button></el-alert>
            <p v-else-if="!courseSourceFiles.length" class="field-hint">尚未上传资料，将根据课程信息和教学要求生成。</p>
            <div v-for="file in courseSourceFiles" :key="file.id" class="course-source-row"><span>{{ file.title || file.file_name }}</span><el-tag :type="file.active_index_version || ['success','partial'].includes(file.parse_status) ? 'success' : 'info'">{{ file.active_index_version || ['success','partial'].includes(file.parse_status) ? '有可用内容' : file.document_state === 'FAILED' || file.parse_status === 'failed' ? '未取得文字' : '处理中' }}</el-tag></div>
            <el-checkbox v-if="courseSourceFiles.length" v-model="config.useCourseInfoOnly">本次仅根据课程信息生成</el-checkbox>
          </section>
        </el-form>

        <div class="stage-actions">
          <el-button type="primary" size="large" :loading="submitting" :disabled="configLocked || changingStep || categoryUnavailable || (courseId && (courseSourceLoading || !!courseSourceError))" @click="saveConfigAndGenerateOutline">
            <el-icon><Lightning /></el-icon> {{ config.generationMode === 'STEP' ? '保存并生成章节骨架' : '保存并生成完整草稿' }}
          </el-button>
        </div>
      </section>

      <section v-else-if="activeStep === 1" class="stage-panel">
        <div class="section-title inline-title">
          <div><span>STEP 02</span><h3>审核章节与课时骨架</h3></div>
          <el-button :icon="Plus" :disabled="stageReadOnly" @click="addChapter">添加章节</el-button>
        </div>
        <div v-if="outline.chapters.length" class="outline-list">
          <el-card v-for="(chapter, chapterIndex) in outline.chapters" :key="chapter.chapterKey" class="chapter-card" shadow="never">
            <div class="chapter-editor">
              <span class="order-badge">{{ chapterIndex + 1 }}</span>
              <el-input v-model="chapter.title" :disabled="stageReadOnly" class="chapter-title-input" placeholder="章节名称" />
              <el-button text type="danger" :icon="Delete" :disabled="stageReadOnly" @click="removeChapter(chapterIndex)">删除章节</el-button>
            </div>
            <el-input v-model="chapter.description" :disabled="stageReadOnly" type="textarea" :rows="2" placeholder="章节说明" />
            <div class="lesson-skeleton-list">
              <div v-for="(lesson, lessonIndex) in chapter.lessons" :key="lesson.lessonKey" class="lesson-skeleton">
                <span>{{ chapterIndex + 1 }}.{{ lessonIndex + 1 }}</span>
                <el-input v-model="lesson.title" :disabled="stageReadOnly" placeholder="课时名称" />
                <el-input v-model="lesson.objectives" :disabled="stageReadOnly" placeholder="课时目标" />
                <el-input-number v-model="lesson.durationMinutes" :disabled="stageReadOnly" :min="10" :max="240" />
                <el-button text type="danger" :icon="Delete" :disabled="stageReadOnly" @click="removeLesson(chapter, lessonIndex)" />
              </div>
              <el-button text type="primary" :icon="Plus" :disabled="stageReadOnly" @click="addLesson(chapter)">添加课时</el-button>
            </div>
          </el-card>
        </div>
        <el-empty v-else description="尚未生成章节骨架" />
        <div class="stage-actions between-actions">
          <el-button @click="goToStep(0)">返回配置</el-button>
          <div><el-button :loading="regenerating" :disabled="stageActionDisabled" @click="retryOutline">重新生成</el-button><el-button type="primary" :loading="submitting" :disabled="stageActionDisabled" @click="approveOutlineAndGenerateLessons">确认骨架并生成课时</el-button></div>
        </div>
      </section>

      <section v-else-if="activeStep === 2" class="stage-panel lesson-stage">
        <div class="section-title"><div><span>STEP 03</span><h3>审核课时 Markdown</h3></div><p>修改会自动保存；改过的课时在重新生成时保留，可单独重写。</p></div>
        <el-tabs v-if="lessons.length" v-model="activeLessonKey" tab-position="left" class="lesson-tabs">
          <el-tab-pane v-for="lesson in lessons" :key="lesson.lessonKey" :name="lesson.lessonKey" :label="lessonTabLabel(lesson)">
            <div class="lesson-editor-heading">
              <el-input v-model="lesson.title" :disabled="stageReadOnly" placeholder="课时名称" />
              <el-tag>{{ lesson.durationMinutes || 45 }} 分钟</el-tag>
              <el-tag v-if="lesson.edited" type="info">已手工修改</el-tag>
              <el-button size="small" :disabled="stageActionDisabled" :data-testid="`rewrite-lesson-${lesson.lessonKey}`" @click="rewriteLesson(lesson)">{{ lesson.status === 'FAILED' || !lesson.contentMd?.trim() ? '重试本课时' : '重写本课时' }}</el-button>
            </div>
            <el-alert v-if="lesson.status === 'FAILED'" class="item-alert" type="warning" :closable="false" show-icon :title="`本课时未生成成功：${lesson.errorMessage || '请重试'}`" />
            <details v-if="lesson.previousContentMd" class="previous-version">
              <summary>上一版本</summary>
              <pre>{{ lesson.previousContentMd }}</pre>
              <el-button size="small" :disabled="stageReadOnly" @click="restorePreviousLesson(lesson)">恢复为上一版本</el-button>
            </details>
            <!-- Remount on completion because the editor retains its initial read-only document state. -->
            <MdEditor
              v-if="activeLessonKey === lesson.lessonKey"
               :key="`${project.id}-${lesson.lessonKey}-${draftEditable}`"
              v-model="lesson.contentMd"
              :editor-id="`preparation-${project.id}-lesson-${lesson.lessonKey}`"
               :read-only="!draftEditable"
              language="zh-CN"
              :theme="resolvedTheme"
              :toolbars-exclude="['github']"
              class="markdown-editor"
            />
          </el-tab-pane>
        </el-tabs>
        <el-empty v-else description="尚未生成课时内容" />
        <div class="stage-actions between-actions">
          <el-button @click="goToStep(1)">返回骨架</el-button>
          <div><el-button v-if="missingLessons.length" type="warning" plain :loading="regenerating" :disabled="stageActionDisabled" data-testid="fill-missing-lessons" @click="fillMissingLessons">补齐未完成的 {{ missingLessons.length }} 个课时</el-button><el-button :loading="regenerating" :disabled="stageActionDisabled" @click="retryLessons">重新生成未修改的课时</el-button><el-button type="primary" :loading="submitting" :disabled="stageActionDisabled" @click="approveLessonsAndGenerateQuestions">{{ config.includeQuestions ? '确认课时并生成题目' : creationMode ? '确认并完成备课' : '确认课时，进入发布' }}</el-button></div>
        </div>
      </section>

      <section v-else-if="activeStep === 3" class="stage-panel">
        <div class="section-title"><div><span>STEP 04</span><h3>审核章节题目</h3></div><p>题目最终统一关联新课程，章节分组只用于本次审核。</p></div>
        <el-collapse v-if="questionGroups.length" v-model="openQuestionGroups">
          <el-collapse-item v-for="group in questionGroups" :key="group.chapterKey" :name="group.chapterKey">
            <template #title><strong>{{ group.chapterTitle }}</strong><el-tag size="small" class="question-count">{{ group.questions.length }} 题</el-tag><el-tag v-if="group.status === 'FAILED'" size="small" type="warning" class="question-count">未生成成功</el-tag><el-tag v-else-if="group.edited" size="small" type="info" class="question-count">已手工修改</el-tag></template>
            <el-alert v-if="group.status === 'FAILED'" class="item-alert" type="warning" :closable="false" show-icon :title="`本章题目未生成成功：${group.errorMessage || '请重试'}`" />
            <div class="group-actions">
              <el-button size="small" :disabled="stageActionDisabled" :data-testid="`rewrite-questions-${group.chapterKey}`" @click="rewriteQuestions(group)">{{ group.status === 'FAILED' || !group.questions.length ? '重试本章题目' : '重写本章题目' }}</el-button>
              <el-button v-if="group.previousQuestions?.length" size="small" :disabled="stageReadOnly" @click="restorePreviousQuestions(group)">恢复上一版本（{{ group.previousQuestions.length }} 题）</el-button>
            </div>
            <el-table :data="group.questions" stripe>
              <el-table-column prop="type" label="题型" width="130"><template #default="{ row }">{{ typeLabel(row.type) }}</template></el-table-column>
              <el-table-column prop="content" label="题干" min-width="320" show-overflow-tooltip />
              <el-table-column prop="difficulty" label="难度" width="90" />
              <el-table-column prop="knowledgePoint" label="知识点" width="160" show-overflow-tooltip />
              <el-table-column label="操作" :width="narrow ? ROW_SHEET_COLUMN_WIDTH : 150" fixed="right">
                <template #default="{ row, $index }">
                  <RowActions :label="row.content || `第 ${$index + 1} 题`">
                    <el-button size="small" :disabled="stageReadOnly" @click="editQuestion(group, row)">编辑</el-button>
                    <el-button size="small" type="danger" :disabled="stageReadOnly" @click="group.questions.splice($index, 1)">删除</el-button>
                  </RowActions>
                </template>
              </el-table-column>
            </el-table>
            <el-button text type="primary" :icon="Plus" :disabled="stageReadOnly" @click="addQuestion(group)">添加题目</el-button>
          </el-collapse-item>
        </el-collapse>
        <el-empty v-else :description="config.includeQuestions ? '尚未生成题目' : '当前草稿未选择生成章节题目'" />
        <div class="stage-actions between-actions">
          <el-button @click="goToStep(2)">返回课时</el-button>
          <div v-if="config.includeQuestions"><el-button v-if="missingQuestionGroups.length" type="warning" plain :loading="regenerating" :disabled="stageActionDisabled" @click="fillMissingQuestions">补齐未完成的 {{ missingQuestionGroups.length }} 章题目</el-button><el-button :loading="regenerating" :disabled="stageActionDisabled" @click="retryQuestions">重新生成未修改的章节题目</el-button><el-button type="primary" :loading="submitting" :disabled="stageActionDisabled" @click="approveQuestions">{{ creationMode ? '确认并完成备课' : '确认题目并进入发布' }}</el-button></div>
        </div>
      </section>

      <section v-else class="stage-panel publish-stage">
        <el-result v-if="creationMode && project?.projectStatus === 'PUBLISHED' && lastApplicationId && !applicationRolledBack" class="creation-complete" icon="success" title="生成完成" />
        <div v-else-if="readyForApplication" class="publish-summary">
          <el-icon><Check /></el-icon>
          <h3>{{ courseId ? '应用前检查' : '发布前检查' }}</h3>
          <p v-if="courseId">生成结果将应用到当前课程。默认追加，也可以在预览中明确选择要替换的课时。</p>
          <p v-else>发布时将一次创建课程草稿、章节和课时{{ config.includeQuestions ? '，以及题库题目' : '；本次未选择生成题目，不会创建题目' }}。可以返回前面的步骤查看和修改各项草稿。</p>
          <div class="summary-grid">
            <div><strong>{{ outline.chapters.length }}</strong><span>章节</span></div>
            <div><strong>{{ lessons.length }}</strong><span>课时</span></div>
            <div><strong>{{ config.includeQuestions ? totalQuestions : '—' }}</strong><span>{{ config.includeQuestions ? '题目' : '题目（未选择）' }}</span></div>
            <div><strong>{{ courseId && Number(courseInfo?.status) === 1 ? '已发布' : '草稿' }}</strong><span>课程状态</span></div>
          </div>
          <div class="stage-actions">
            <el-button v-if="outline.chapters.length" @click="goToStep(1)">查看章节骨架</el-button>
            <el-button v-if="lessons.length" @click="goToStep(2)">查看课时正文</el-button>
            <el-button v-if="config.includeQuestions && questionGroups.length" @click="goToStep(3)">查看章节题目</el-button>
          </div>
          <el-button type="primary" size="large" :loading="publishing" :disabled="changingStep || contextBusy" @click="courseId ? previewApplication() : publishCourse()">{{ courseId ? '预览并应用到课程' : '发布到课程管理' }}</el-button>
        </div>
        <el-result v-else-if="project?.projectStatus === 'PUBLISHED'" :icon="applicationRolledBack ? 'info' : 'success'" :title="applicationResultTitle" :sub-title="creationMode ? (applicationRolledBack ? '可以新建备课草稿，或更换课程内容准备方式。' : '完成创建后，可在课程详情中查看并维护教学内容。') : `课程 ID：${project.publishedCourseId}`">
          <template #extra><el-button v-if="!creationMode" type="primary" :disabled="contextBusy" @click="openPublishedCourse">查看课程内容</el-button><el-button v-if="courseId && lastApplicationId" :loading="publishing" @click="rollbackApplication">回退本次应用</el-button><el-button v-if="!creationMode" :disabled="contextBusy" @click="startNewDraft">继续新备课</el-button></template>
        </el-result>
        <el-empty v-else description="草稿尚未准备完成">
          <p class="field-hint">请先完成生成并检查所需内容。</p>
        </el-empty>
      </section>
    </section>

    <el-dialog v-model="applicationVisible" title="应用生成内容" width="min(900px, 96vw)" :close-on-click-modal="false">
      <el-alert v-if="Number(courseInfo?.status) === 1" title="课程已经发布，应用后学生会看到更新后的内容。" type="warning" :closable="false" show-icon />
      <p>默认将生成内容追加到课程。需要替换时，请为对应课时选择明确目标，原课时的视频及学习进度会保留。</p>
      <el-table :data="applicationLessons" max-height="420">
        <el-table-column prop="title" label="生成课时" />
        <el-table-column label="应用方式" min-width="260"><template #default="{row}"><el-select v-model="replacementTargets[row.lessonKey]" clearable placeholder="追加为新课时" style="width:100%"><el-option v-for="lesson in existingCourseLessons" :key="lesson.id" :value="lesson.id" :label="`替换 ${lesson.chapterTitle} / ${lesson.title}`" /></el-select></template></el-table-column>
      </el-table>
      <p v-if="config.includeQuestions">本次同时追加 {{ totalQuestions }} 道题目到本课程题库。</p>
      <template #footer><el-button @click="applicationVisible = false">返回检查</el-button><el-button type="primary" :loading="publishing" @click="applyApplication">确认应用到当前课程</el-button></template>
    </el-dialog>

    <el-dialog v-model="questionDialogVisible" :title="editingQuestionIsNew ? '添加题目' : '编辑题目'" width="760px">
      <el-form v-if="editingQuestion" label-position="top">
        <div class="form-grid two-columns">
          <el-form-item label="题型"><el-select v-model="editingQuestion.type" @change="resetQuestionShape"><el-option v-for="item in questionTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="难度"><el-select v-model="editingQuestion.difficulty"><el-option label="简单" value="EASY" /><el-option label="中等" value="MEDIUM" /><el-option label="困难" value="HARD" /></el-select></el-form-item>
        </div>
        <el-form-item label="题干"><el-input v-model="editingQuestion.content" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="知识点"><el-input v-model="editingQuestion.knowledgePoint" maxlength="200" /></el-form-item>
        <el-form-item v-if="isChoiceQuestion(editingQuestion.type)" label="选项">
          <div class="option-editor"><div v-for="(option, index) in editingQuestion.options" :key="index"><el-input v-model="option.optionKey" class="option-key" /><el-input v-model="option.optionContent" /><el-button text type="danger" :icon="Delete" @click="editingQuestion.options.splice(index, 1)" /></div><el-button text type="primary" :icon="Plus" @click="addOption">添加选项</el-button></div>
        </el-form-item>
        <el-form-item label="答案"><el-input v-model="editingAnswerText" type="textarea" :rows="2" :placeholder="editingQuestion.type === 'MULTIPLE_CHOICE' ? '每行一个正确选项，例如 A' : '输入答案；选择题填写选项字母'" /></el-form-item>
        <el-form-item label="解析"><el-input v-model="editingQuestion.analysis" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="questionDialogVisible = false">取消</el-button><el-button type="primary" @click="saveQuestionEdit">保存题目</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useAutosave } from '@/composables/useAutosave'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Delete, Lightning, Plus, Reading, Refresh, Upload } from '@element-plus/icons-vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import RowActions from '@/components/ui/RowActions.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { ROW_SHEET_COLUMN_WIDTH, useNarrowScreen } from '@/composables/useNarrowScreen'
import { useTheme } from '@/composables/useTheme'
import { requireSuccess } from '@/utils/apiHelpers'
import { getCategories, getCourseById } from '@/utils/course.js'
import { listKnowledgeBases } from '@/api/knowledgeBase.js'
import { getCourseKnowledgeBase, listDocuments, ragList, unwrapRag } from '@/api/rag'
import {
  createPreparation, generatePreparationLessons, generatePreparationOutline, generatePreparationQuestions,
  getPreparation, listPreparations, publishPreparation, savePreparationLessons, savePreparationOutline,
  savePreparationQuestions, updatePreparationConfig, uploadPreparationSources, generatePreparationAll,
  markPreparationReady, regeneratePreparationLesson, regeneratePreparationQuestions, previewPreparationApplication, applyPreparationToCourse, rollbackPreparationApplication
} from '@/api/preparation.js'

const props = defineProps({ courseId: Number, embedded: Boolean, creationMode: Boolean, initialProjectId: Number })
const emit = defineEmits(['applied', 'project-change', 'materials'])
const courseInfo = ref(null), courseSourceFiles = ref([]), courseSourceError = ref(''), courseSourceLoading = ref(false)
const applicationVisible = ref(false), applicationPreview = ref(null), replacementTargets = reactive({}), lastApplicationId = ref(null)
const applicationRolledBack = ref(false)
let applicationRead = 0
const applicationResultTitle = computed(() => {
  if (!props.courseId) return '课程草稿已发布'
  if (applicationRolledBack.value) return '本次应用已回退'
  return lastApplicationId.value ? '生成内容已应用' : '正在确认应用状态'
})
const applicationLessons = computed(() => lessons.value.map(lesson => ({ lessonKey: lesson.lessonKey, title: lesson.title })))
const existingCourseLessons = computed(() => applicationPreview.value?.existingLessons || [])
const router = useRouter()
const route = useRoute()
const narrow = useNarrowScreen()
const { resolvedTheme } = useTheme()
const configFormRef = ref()
const project = ref(null)
const projectStep = computed(() => {
  if (props.creationMode && project.value?.currentStage === 'READY_TO_PUBLISH') return project.value.config?.includeQuestions === false ? 2 : 3
  return stepFromStage(project.value?.currentStage)
})
const selectedStep = ref(null)
const activeStep = computed(() => selectedStep.value ?? projectStep.value)
const changingStep = ref(false)
const STEP_STAGES = { 1: 'OUTLINE_REVIEW', 2: 'LESSON_REVIEW', 3: 'QUESTION_REVIEW' }
// The step on screen decides which draft is saved, so a finished draft can still be revised before publishing.
const viewedStage = computed(() => STEP_STAGES[activeStep.value] || null)
const draftEditable = computed(() => !!project.value?.id && project.value.projectStatus === 'DRAFT'
  && !isGenerating.value && !!viewedStage.value && projectStep.value >= activeStep.value
  && (activeStep.value !== 3 || config.includeQuestions))
const stageReadOnly = computed(() => !draftEditable.value || changingStep.value)
const stageActionDisabled = computed(() => stageReadOnly.value || contextBusy.value)
let applyingProject = false
const waitingForSources = ref(false)
let sourceWaitTimer = null, sourceWaitRun = 0
const recentDrafts = ref([])
const selectedDraftId = ref(null)
const categories = ref([])
const knowledgeBases = ref([])
const uploadFiles = ref([])
const uploadFileList = ref([])
const outline = reactive({ courseDescription: '', chapters: [] })
const lessons = ref([])
const questionGroups = ref([])
const activeLessonKey = ref('')
const openQuestionGroups = ref([])
const submitting = ref(false)
const publishing = ref(false)
const regenerating = ref(false)
const writeError = ref(null)
let pollTimer = null
let pollRun = 0
let lastShownGenerationErrorKey = ''
let lastProjectReadId = null
// Changes whenever the page moves to another draft, so A -> B -> A does not look unchanged.
let editingContext = 0

// Read failures here only add a notice: none of them may touch the draft being edited.
const { loading: categoriesLoading, loadFailed: categoriesFailed, guarded: guardedCategories } = useGuardedLoad()
const { loading: knowledgeBasesLoading, loadFailed: knowledgeBasesFailed, guarded: guardedKnowledgeBases } = useGuardedLoad()
const { loading: draftsLoading, loadFailed: draftsFailed, guarded: guardedDrafts } = useGuardedLoad()
const {
  loading: projectLoading,
  loadFailed: projectFailed,
  guarded: guardedProject,
  reset: resetProjectRead,
} = useGuardedLoad()

const defaultConfig = () => ({
  title: '', categoryId: null, description: '', difficulty: 'beginner', audience: '', totalHours: 32,
  chapterCount: 6, lessonsPerChapter: 3, teachingGoals: '', extraInstructions: '', knowledgeBaseIds: [],
  questionConfig: { countPerChapter: 5, types: ['SINGLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER'], difficulty: 'MEDIUM', instructions: '' },
  includeQuestions: true, generationMode: 'CONTINUOUS', targetCourseId: props.courseId || null, useCourseInfoOnly: false
})
const config = reactive(defaultConfig())
const preparationSteps = computed(() => [
  { title: '生成设置', description: '确定生成依据' },
  { title: '章节骨架', description: '审核课程结构' },
  { title: '课时 Markdown', description: '审核教学正文' },
  { title: '章节题目', description: config.includeQuestions ? '审核题库内容' : '本次不生成' },
  { title: props.courseId ? '生成完成' : '发布草稿', description: props.creationMode ? '课程内容已就绪' : '集中检查后写入' },
])
const configurationReady = ref(false)
const configurationStorageKey = computed(() => props.courseId ? `eduloop:course-preparation-config:${props.courseId}` : '')
const configSave = useAutosave({
  key: () => configurationStorageKey.value,
  snapshot: () => ({ projectId: project.value?.id || null, config: JSON.parse(JSON.stringify(config)) }),
  save: async () => {
    if (!props.courseId || activeStep.value !== 0 || isGenerating.value || contextBusy.value) return true
    const id = project.value?.id
    const response = id ? await updatePreparationConfig(id, config) : await createPreparation(config)
    if (response.code !== '200') return false
    project.value = { ...project.value, ...response.data }
    selectedDraftId.value = response.data.id
    return true
  }
})
watch(config, () => {
  if (!props.courseId || !configurationReady.value || applyingProject || activeStep.value !== 0 || isGenerating.value) return
  try { localStorage.setItem(configurationStorageKey.value, JSON.stringify({ at: Date.now(), value: { projectId: project.value?.id || null, config: JSON.parse(JSON.stringify(config)) } })) } catch { /* in-memory editing remains available */ }
  if (config.audience?.trim() && config.title?.trim() && config.categoryId) configSave.touch()
}, { deep: true })
const configRules = {
  title: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择课程分类', trigger: 'change' }],
  audience: [{ required: true, message: '请输入目标对象', trigger: 'blur' }]
}
const questionTypes = [
  { label: '单选题', value: 'SINGLE_CHOICE' }, { label: '多选题', value: 'MULTIPLE_CHOICE' },
  { label: '判断题', value: 'TRUE_FALSE' }, { label: '填空题', value: 'FILL_BLANK' }, { label: '简答题', value: 'SHORT_ANSWER' }
]

const isGenerating = computed(() => ['RUNNING', 'WAITING'].includes(project.value?.generationStatus))
const configLocked = computed(() => isGenerating.value || project.value?.projectStatus === 'PUBLISHED')
const generatingLabel = computed(() => {
  if (project.value?.generationStatus === 'WAITING') return '等待课程资料处理，有可用内容后自动生成'
  const stage = project.value?.currentStage
  if (stage === 'CONFIG') return '正在设计课程骨架'
  if (stage === 'OUTLINE_REVIEW') return '正在生成课时 Markdown'
  if (stage === 'LESSON_REVIEW') return `正在生成课时与题目（已完成 ${lessons.value.filter(lesson => lesson.status === 'GENERATED').length}/${lessons.value.length} 个课时）`
  if (stage === 'QUESTION_REVIEW') return '正在按章节生成题目'
  return '正在重写所选内容'
})
const missingLessons = computed(() => lessons.value.filter(lesson => lesson.status === 'FAILED' || !lesson.contentMd?.trim()))
const missingQuestionGroups = computed(() => questionGroups.value.filter(group => group.status === 'FAILED' || !group.questions?.length))
const readyForApplication = computed(() => project.value?.projectStatus === 'DRAFT'
  && project.value.currentStage === 'READY_TO_PUBLISH' && !isGenerating.value
  && outline.chapters.length && lessons.value.length && !missingLessons.value.length
  && (!config.includeQuestions || (questionGroups.value.length && !missingQuestionGroups.value.length)))
function lessonTabLabel(lesson) {
  if (lesson.status === 'FAILED' || !lesson.contentMd?.trim()) return `⚠ ${lesson.title}`
  return lesson.edited ? `${lesson.title}（已改）` : lesson.title
}
const totalQuestions = computed(() => questionGroups.value.reduce((sum, group) => sum + (group.questions?.length || 0), 0))
// A draft that already has a category can still generate; only a required, unknown choice blocks.
const categoryUnavailable = computed(() => categoriesFailed.value && !config.categoryId)
const categoriesEmpty = computed(() => !categoriesLoading.value && !categoriesFailed.value && !categories.value.length)
// While a write is out, the page stays on the draft it belongs to.
const contextBusy = computed(() => submitting.value || publishing.value || regenerating.value)
const visibleWriteError = computed(() => (
  writeError.value?.projectId === (project.value?.id ?? null) ? writeError.value : null
))

function records(data) { return Array.isArray(data) ? data : data?.records || data?.items || data?.list || [] }
function newKey(prefix) { return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}` }
function stageLabel(stage) {
  if (stage?.startsWith('WAITING_SOURCES_')) return '等待资料处理'
  return ({
    CONFIG: '生成设置',
    OUTLINE_REVIEW: '审核骨架',
    LESSON_REVIEW: '审核课时',
    QUESTION_REVIEW: '审核题目',
    READY_TO_PUBLISH: props.courseId ? '待应用' : '待发布',
    PUBLISHED: props.courseId ? '已应用' : '已发布',
  })[stage] || '备课草稿'
}
function stepFromStage(stage) {
  return ({ CONFIG: 0, OUTLINE_REVIEW: 1, LESSON_REVIEW: 2, QUESTION_REVIEW: 3, READY_TO_PUBLISH: 4, PUBLISHED: 4 })[stage] ?? 0
}
function sourceStatus(file) {
  const missing = file.missingPages?.length ? ` · 第 ${file.missingPages.join('、')} 页未取得文字` : ''
  return ({
    USABLE: { label: '可用', type: 'success' },
    PARTIAL: { label: `部分可用${missing}`, type: 'warning' },
    FAILED: { label: '未取得文字，生成时不使用', type: 'danger' },
  })[file.availability] || { label: `处理中 ${file.parseProgress || 0}%`, type: 'info' }
}
function typeLabel(type) { return questionTypes.find(item => item.value === type)?.label || type }

function loadCategories() {
  return guardedCategories(async (isCurrent) => {
    const res = requireSuccess(await getCategories())
    if (isCurrent()) categories.value = records(res.data)
  })
}
function loadKnowledgeBases() {
  return guardedKnowledgeBases(async (isCurrent) => {
    const res = requireSuccess(await listKnowledgeBases())
    if (isCurrent()) knowledgeBases.value = records(res.data)
  })
}
function loadRecentDrafts() {
  return guardedDrafts(async (isCurrent) => {
    const res = requireSuccess(await listPreparations(props.courseId ? { courseId: props.courseId } : {}))
    if (isCurrent()) recentDrafts.value = records(res.data).slice(0, 20)
  })
}
async function loadInitialData() {
  await Promise.all([loadCategories(), props.courseId ? loadCourseContext() : loadKnowledgeBases(), loadRecentDrafts()])
  if (props.courseId) {
    applyCourseConfig()
    const latest = recentDrafts.value.find(draft => draft.projectStatus === 'DRAFT')
    const requestedId = Number(props.initialProjectId || route.query.projectId || route.query.draftId)
    if (Number.isInteger(requestedId) && requestedId > 0) await resumeDraft(requestedId)
    else if (latest) await resumeDraft(latest.id)
    const local = configSave.readLocal()
    if (local && (!local.projectId || Number(local.projectId) === Number(project.value?.id)) && activeStep.value === 0) {
      Object.assign(config, defaultConfig(), local.config || {})
      applyCourseConfig()
    }
    await nextTick()
    configurationReady.value = true
  }
}
function applyCourseConfig() {
  if (!props.courseId || !courseInfo.value) return
  Object.assign(config, { targetCourseId: props.courseId, title: courseInfo.value.title, categoryId: courseInfo.value.categoryId, description: courseInfo.value.description || '', difficulty: courseInfo.value.difficulty || 'beginner' })
}
async function loadCourseContext() {
  courseSourceLoading.value = true; courseSourceError.value = ''
  try {
    const [course, kb] = await Promise.all([getCourseById(props.courseId), getCourseKnowledgeBase(props.courseId)])
    courseInfo.value = requireSuccess(course).data
    const library = unwrapRag(kb)
    courseSourceFiles.value = ragList(await listDocuments(library.id))
    applyCourseConfig()
  } catch (problem) { courseSourceError.value = problem?.message || '课程资料读取失败' }
  finally { courseSourceLoading.value = false }
}
function openCourseMaterials() {
  if (props.creationMode) { emit('materials'); return }
  router.push(props.courseId ? { path: `/courses/${props.courseId}`, query: { tab: 'materials' } } : { path: '/teacher-knowledge-base', query: { kbId: project.value?.ownedKnowledgeBaseId } })
}

function readProject(id) {
  lastProjectReadId = id
  return guardedProject(async (isCurrent) => {
    const res = requireSuccess(await getPreparation(id))
    if (isCurrent()) {
      const targetCourseId = res.data.config?.targetCourseId ?? res.data.targetCourseId
      if (props.courseId && Number(targetCourseId) !== props.courseId) throw new Error('该备课草稿属于其他课程')
      applyProject(res.data)
    }
  })
}
function retryProject() {
  return readProject(lastProjectReadId)
}
async function resumeDraft(id) {
  if (!id) return
  if (props.courseId && configurationReady.value && !await configSave.flush()) return
  configSave.reset()
  await readProject(id)
  // Keep the picker on the draft actually on screen.
  if (projectFailed.value) selectedDraftId.value = project.value?.id ?? null
}
// Called before a write. A draft read still in flight would swap the page under that write,
// so it is dropped, and the returned target stays fixed for the whole request.
function pinEditingContext() {
  resetProjectRead()
  selectedDraftId.value = project.value?.id ?? null
  writeError.value = null
  return { id: project.value?.id ?? null, context: editingContext }
}
function isSameContext(target) {
  return target.context === editingContext
}
function reportWriteFailure(title, failure) {
  // Business failures arrive as resolved responses; only a thrown error can lack an answer.
  const answer = failure?.response?.data ?? (failure instanceof Error ? null : failure)
  const outcome = answer
    ? answer.msg || answer.message || '请求失败'
    : '没有收到服务器结果，操作可能已完成，也可能没有完成，系统不会自动重试'
  writeError.value = { projectId: project.value?.id ?? null, title, message: `${outcome}。当前页面内容已保留。` }
}
function normalizeLessons(items) {
  // Pending lessons and older local drafts can have no generated text yet.
  return structuredClone(items || []).map(lesson => ({ ...lesson, contentMd: String(lesson.contentMd ?? '') }))
}
function applyProject(data, { preserveStep = false } = {}) {
  applyingProject = true
  const sameProject = data.id === project.value?.id
  if (!sameProject) editingContext += 1
  project.value = data
  lastApplicationId.value = data.applicationId || (sameProject && data.projectStatus === 'PUBLISHED' ? lastApplicationId.value : null)
  if (!sameProject || data.projectStatus !== 'PUBLISHED') applicationRolledBack.value = false
  const applicationRun = ++applicationRead
  if (props.courseId && data.projectStatus === 'PUBLISHED') {
    previewPreparationApplication(data.id).then(response => {
      if (project.value?.id !== data.id || applicationRun !== applicationRead) return
      requireSuccess(response)
      applicationRolledBack.value = Boolean(response.data.rolledBack)
      lastApplicationId.value = applicationRolledBack.value ? null : response.data.applicationId || null
    }).catch(problem => { if (project.value?.id === data.id && applicationRun === applicationRead) reportWriteFailure('应用状态未读取', problem) })
  }
  selectedDraftId.value = data.id
  Object.assign(config, defaultConfig(), data.config || {})
  config.questionConfig = { ...defaultConfig().questionConfig, ...(data.config?.questionConfig || {}) }
  applyCourseConfig()
  config.knowledgeBaseIds = [...(data.knowledgeBaseIds || data.config?.knowledgeBaseIds || [])]
  Object.assign(outline, { courseDescription: data.outline?.courseDescription || '', chapters: structuredClone(data.outline?.chapters || []) })
  lessons.value = normalizeLessons(data.lessons)
  questionGroups.value = structuredClone(data.questions || [])
  if (!sameProject || !lessons.value.some(lesson => lesson.lessonKey === activeLessonKey.value)) {
    activeLessonKey.value = lessons.value[0]?.lessonKey || ''
  }
  openQuestionGroups.value = questionGroups.value.map(group => group.chapterKey)
  if (!sameProject || !preserveStep) selectedStep.value = null
  if (['RUNNING', 'WAITING'].includes(data.generationStatus)) {
    lastShownGenerationErrorKey = ''
    schedulePoll()
  }
  nextTick(() => {
    applyingProject = false
    restoreStageDraft()
  })
}
watch(() => [project.value?.id, project.value?.projectStatus, lastApplicationId.value, applicationRolledBack.value], ([id, status, applicationId, rolledBack]) => {
  emit('project-change', { id: id || null, applied: status === 'PUBLISHED' && !!applicationId && !rolledBack })
})
function stageContent(stage = viewedStage.value) {
  if (stage === 'OUTLINE_REVIEW') return JSON.parse(JSON.stringify({ courseDescription: outline.courseDescription, chapters: outline.chapters }))
  if (stage === 'LESSON_REVIEW') return JSON.parse(JSON.stringify(lessons.value))
  if (stage === 'QUESTION_REVIEW') return JSON.parse(JSON.stringify(questionGroups.value))
  return null
}
function saveStageDraft(id, stage, content) {
  const config = { silent: true }
  if (stage === 'OUTLINE_REVIEW') return savePreparationOutline(id, content, config)
  if (stage === 'LESSON_REVIEW') return savePreparationLessons(id, content, config)
  return savePreparationQuestions(id, content, config)
}
const draftSave = useAutosave({
  key: () => draftEditable.value ? `eduloop:preparation:${project.value.id}:${viewedStage.value}` : '',
  snapshot: () => stageContent(),
  save: async () => {
    // The stage moved on (approve or generation); the explicit step already saved its content.
    if (!draftEditable.value) return true
    const id = project.value.id, stage = viewedStage.value
    const res = await saveStageDraft(id, stage, stageContent(stage))
    // Saving a changed outline can send the draft back to outline review.
    if (res?.code === '200' && project.value?.id === id) {
      project.value = {
        ...project.value,
        currentStage: res.data?.currentStage ?? project.value.currentStage,
        version: res.data?.version,
        updatedAt: res.data?.updatedAt,
      }
    }
    return res?.code === '200'
  }
})
const headerSaveStatus = computed(() => {
  if (props.courseId && activeStep.value === 0 && configSave.label.value) {
    return { label: configSave.label.value, state: configSave.state.value }
  }
  if (draftEditable.value && draftSave.label.value) {
    return { label: draftSave.label.value, state: draftSave.state.value, testId: 'draft-save-status' }
  }
  return null
})
// A local copy that differs from the server draft is newer: put it back and send it.
function restoreStageDraft() {
  if (!draftEditable.value) return
  const local = draftSave.readLocal()
  if (!local || JSON.stringify(local) === JSON.stringify(stageContent())) { draftSave.clearLocal(); return }
  applyingProject = true
  if (viewedStage.value === 'OUTLINE_REVIEW') Object.assign(outline, { courseDescription: local.courseDescription || '', chapters: local.chapters || [] })
  else if (viewedStage.value === 'LESSON_REVIEW') { lessons.value = normalizeLessons(local); activeLessonKey.value = lessons.value[0]?.lessonKey || '' }
  else questionGroups.value = local
  nextTick(() => { applyingProject = false; draftSave.touch() })
  ElMessage.info('已恢复上次未保存的修改')
}
watch([outline, lessons, questionGroups], () => {
  if (!applyingProject && draftEditable.value) draftSave.touch()
}, { deep: true })
// Pending edits belong to the stage they were typed in, so they are sent before the view changes.
async function goToStep(step) {
  if (step === activeStep.value || changingStep.value || contextBusy.value || projectLoading.value) return
  const context = editingContext
  changingStep.value = true
  try {
    if (props.courseId && activeStep.value === 0 && !await configSave.flush()) return
    if (!await draftSave.flush() || context !== editingContext) return
    selectedStep.value = step
    await nextTick()
    restoreStageDraft()
  } finally {
    changingStep.value = false
  }
}
const warnBeforeLeave = event => {
  if (['pending', 'saving', 'error', 'offline'].includes(draftSave.state.value)) { event.preventDefault(); event.returnValue = '' }
}
window.addEventListener('beforeunload', warnBeforeLeave)
async function startNewDraft() {
  if (contextBusy.value) return
  if (props.courseId && !await configSave.flush()) return
  configSave.clearLocal(); configSave.reset()
  lastApplicationId.value = null
  stopPoll(); stopSourceWait(); resetProjectRead(); project.value = null; selectedDraftId.value = null; selectedStep.value = null
  editingContext += 1
  writeError.value = null
  lastShownGenerationErrorKey = ''
  Object.assign(config, defaultConfig()); Object.assign(outline, { courseDescription: '', chapters: [] })
  lessons.value = []; questionGroups.value = []; uploadFiles.value = []; uploadFileList.value = []
  applyCourseConfig()
}
async function refreshProject() {
  if (project.value?.id) await readProject(project.value.id)
}
function schedulePoll() {
  stopPoll()
  const id = project.value?.id
  const run = pollRun
  pollTimer = window.setTimeout(async () => {
    try {
      const res = await getPreparation(id)
      // A poll already in flight must not bring back a draft the user has left.
      if (run !== pollRun) return
      if (res.code !== '200') {
        schedulePoll()
        return
      }
      applyProject(res.data, { preserveStep: true })
      if (res.data.generationStatus === 'FAILED') {
        const message = res.data.errorMessage || '生成失败'
        const errorKey = `${res.data.id}:${res.data.updatedAt || ''}:${message}`
        if (errorKey !== lastShownGenerationErrorKey) {
          lastShownGenerationErrorKey = errorKey
          ElMessage.error(message)
        }
      }
    } catch { if (run === pollRun) schedulePoll() }
  }, 1600)
}
function stopPoll() { pollRun += 1; if (pollTimer) window.clearTimeout(pollTimer); pollTimer = null }
// The teacher already asked for an outline: follow material processing and start once nothing is still pending.
// Only the source list is refreshed meanwhile, so form edits on screen are never overwritten.
function startConfiguredGeneration(id, restart) {
  return config.generationMode === 'STEP' ? generatePreparationOutline(id) : generatePreparationAll(id, restart)
}
function waitForSourcesThenGenerate(target, restart = false) {
  stopSourceWait()
  waitingForSources.value = true
  const run = ++sourceWaitRun
  const tick = async () => {
    if (run !== sourceWaitRun || !isSameContext(target)) return
    try {
      const res = await getPreparation(target.id)
      if (run !== sourceWaitRun || !isSameContext(target)) return
      if (res.code === '200') {
        project.value = { ...project.value, sourceFiles: res.data.sourceFiles, sourcesReady: res.data.sourcesReady }
        if (!res.data.sourceFiles?.some(file => file.availability === 'PROCESSING')) {
          stopSourceWait()
          if (!res.data.sourcesReady) return ElMessage.error('上传的资料都没有取得可用文字，可到知识库补处理，或移除资料后继续')
          const started = await startConfiguredGeneration(target.id, restart)
          if (started.code !== '200') return reportWriteFailure('生成未开始', started)
          if (isSameContext(target)) { applyProject(started.data); schedulePoll() }
          return
        }
      }
    } catch { /* transient: keep following */ }
    sourceWaitTimer = window.setTimeout(tick, 3000)
  }
  sourceWaitTimer = window.setTimeout(tick, 1500)
}
function stopSourceWait() { sourceWaitRun += 1; if (sourceWaitTimer) window.clearTimeout(sourceWaitTimer); sourceWaitTimer = null; waitingForSources.value = false }

function handleUploadChange(file) {
  if (file.raw && !uploadFiles.value.some(item => item.uid === file.uid)) uploadFiles.value.push(file.raw)
  uploadFileList.value = uploadFiles.value.map((raw, index) => ({ name: raw.name, uid: index, raw }))
}
function handleUploadRemove(file) {
  uploadFiles.value = uploadFiles.value.filter(raw => raw !== file.raw && raw.name !== file.name)
  uploadFileList.value = uploadFiles.value.map((raw, index) => ({ name: raw.name, uid: index, raw }))
}
async function saveConfigAndGenerateOutline() {
  try { await configFormRef.value.validate() } catch { return }
  if (config.includeQuestions && !config.questionConfig.types.length) return ElMessage.warning('请至少选择一种题型')
  if (props.courseId && !await configSave.flush()) return ElMessage.error('备课设置尚未保存，请稍后重试')
  pinEditingContext()
  submitting.value = true
  try {
    let res = project.value?.id ? await updatePreparationConfig(project.value.id, config) : await createPreparation(config)
    if (res.code !== '200') return
    applyProject(res.data)
    if (uploadFiles.value.length) {
      res = await uploadPreparationSources(project.value.id, uploadFiles.value)
      if (res.code !== '200') return
      applyProject(res.data); uploadFiles.value = []; uploadFileList.value = []
    }
    const target = { id: project.value.id, context: editingContext }
    let restart = false
    if (config.generationMode !== 'STEP' && project.value.outline?.chapters?.length) {
      try { await ElMessageBox.confirm('将按当前设置重新生成完整草稿，现有骨架、课时和题目会被替换。', '重新生成完整草稿', { type: 'warning', confirmButtonText: '重新生成', cancelButtonText: '取消' }) }
      catch { return }
      restart = true
    } else restart = config.generationMode !== 'STEP'
    if (!props.courseId && project.value.sourceFiles?.some(file => file.availability === 'PROCESSING')) return waitForSourcesThenGenerate(target, restart)
    res = await startConfiguredGeneration(target.id, restart)
    if (res.code === '200') { applyProject(res.data); schedulePoll() }
  } catch (error) {
    reportWriteFailure('配置未保存或骨架未开始生成', error)
  } finally { submitting.value = false; loadRecentDrafts() }
}
// Regeneration is always a click: a failed or unanswered request is reported, never re-sent.
async function regenerate(generate, title) {
  if (contextBusy.value) return
  const target = pinEditingContext()
  if (!target.id) return
  regenerating.value = true
  try {
    const res = await generate(target.id)
    if (res.code !== '200') reportWriteFailure(title, res)
    else if (isSameContext(target)) { applyProject(res.data); schedulePoll() }
  } catch (error) {
    reportWriteFailure(title, error)
  } finally { regenerating.value = false }
}
const retryOutline = () => regenerate(generatePreparationOutline, '课程骨架未重新生成')
const retryLessons = () => regenerate(id => generatePreparationLessons(id, 'all'), '课时未重新生成')
const retryQuestions = () => regenerate(id => generatePreparationQuestions(id, 'all'), '章节题目未重新生成')
const fillMissingLessons = () => regenerate(id => generatePreparationLessons(id, 'missing'), '未完成的课时没有开始生成')
const fillMissingQuestions = () => regenerate(id => generatePreparationQuestions(id, 'missing'), '未完成的章节题目没有开始生成')
// Pending edits are saved first so a rewrite never starts from, or overwrites, unsent text.
async function rewriteLesson(lesson) {
  await draftSave.flush()
  await regenerate(id => regeneratePreparationLesson(id, lesson.lessonKey), `课时「${lesson.title}」未开始重写`)
}
async function rewriteQuestions(group) {
  await draftSave.flush()
  await regenerate(id => regeneratePreparationQuestions(id, group.chapterKey), `「${group.chapterTitle}」的题目未开始重写`)
}
function restorePreviousLesson(lesson) { lesson.contentMd = lesson.previousContentMd }
function restorePreviousQuestions(group) { group.questions = structuredClone(group.previousQuestions) }
// After a failure: the continuous flow picks up where it stopped; the step flow refills the stage on screen.
function resumeGeneration() {
  if (config.generationMode !== 'STEP') return regenerate(id => generatePreparationAll(id, false), '没有继续生成')
  if (project.value.currentStage === 'QUESTION_REVIEW') return fillMissingQuestions()
  if (project.value.currentStage === 'LESSON_REVIEW' || project.value.currentStage === 'OUTLINE_REVIEW') return fillMissingLessons()
  return retryOutline()
}

function addChapter() { outline.chapters.push({ chapterKey: newKey('chapter'), title: '新章节', description: '', lessons: [{ lessonKey: newKey('lesson'), title: '新课时', objectives: '', knowledgePoints: [], durationMinutes: 45 }] }) }
function removeChapter(index) { if (outline.chapters.length <= 1) return ElMessage.warning('至少保留一个章节'); outline.chapters.splice(index, 1) }
function addLesson(chapter) { chapter.lessons.push({ lessonKey: newKey('lesson'), title: '新课时', objectives: '', knowledgePoints: [], durationMinutes: 45 }) }
function removeLesson(chapter, index) { if (chapter.lessons.length <= 1) return ElMessage.warning('每章至少保留一个课时'); chapter.lessons.splice(index, 1) }
async function approveOutlineAndGenerateLessons() {
  if (!outline.chapters.length || outline.chapters.some(chapter => !chapter.title?.trim() || !chapter.lessons?.length || chapter.lessons.some(lesson => !lesson.title?.trim()))) return ElMessage.warning('请补全章节和课时名称')
  const target = pinEditingContext()
  submitting.value = true
  try {
    let res = await savePreparationOutline(target.id, outline)
    if (res.code !== '200') return
    draftSave.clearLocal()
    res = await generatePreparationLessons(target.id)
    if (res.code === '200' && isSameContext(target)) { applyProject(res.data); schedulePoll() }
  } catch (error) {
    reportWriteFailure('骨架未保存或课时未开始生成', error)
  } finally { submitting.value = false }
}
async function approveLessonsAndGenerateQuestions() {
  if (!lessons.value.length || lessons.value.some(lesson => !lesson.contentMd?.trim())) return ElMessage.warning('请确保每个课时都有 Markdown 内容')
  const target = pinEditingContext()
  submitting.value = true
  try {
    let res = await savePreparationLessons(target.id, lessons.value)
    if (res.code !== '200') return
    draftSave.clearLocal()
    res = config.includeQuestions ? await generatePreparationQuestions(target.id, 'missing') : await markPreparationReady(target.id)
    if (res.code === '200' && isSameContext(target)) {
      applyProject(res.data)
      if (props.creationMode && !config.includeQuestions) await finishCreationPreparation(target)
      else schedulePoll()
    }
  } catch (error) {
    reportWriteFailure('课时未保存或题目未开始生成', error)
  } finally { submitting.value = false }
}
async function approveQuestions() {
  if (!questionGroups.value.length || questionGroups.value.some(group => !group.questions?.length)) return ElMessage.warning('每个章节至少保留一道题')
  const target = pinEditingContext()
  submitting.value = true
  try {
    let res = await savePreparationQuestions(target.id, questionGroups.value)
    if (res.code !== '200') return
    draftSave.clearLocal()
    res = await markPreparationReady(target.id)
    if (res.code === '200' && isSameContext(target)) {
      applyProject(res.data)
      if (props.creationMode) await finishCreationPreparation(target)
    }
  } catch (error) {
    reportWriteFailure('题目未保存', error)
  } finally { submitting.value = false }
}

const questionDialogVisible = ref(false)
const editingQuestion = ref(null)
const editingQuestionGroup = ref(null)
const editingQuestionIndex = ref(-1)
const editingQuestionIsNew = ref(false)
const editingAnswerText = ref('')
function isChoiceQuestion(type) { return ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(type) }
function editQuestion(group, question) {
  editingQuestionGroup.value = group; editingQuestionIndex.value = group.questions.indexOf(question); editingQuestionIsNew.value = false; editingQuestion.value = structuredClone(question)
  editingQuestion.value.options ||= []; editingAnswerText.value = (editingQuestion.value.answers || []).map(item => item.answerContent).join('\n'); questionDialogVisible.value = true
}
function addQuestion(group) {
  editingQuestionGroup.value = group; editingQuestionIndex.value = -1; editingQuestionIsNew.value = true
  editingQuestion.value = { content: '', type: 'SHORT_ANSWER', difficulty: 'MEDIUM', knowledgePoint: group.chapterTitle, analysis: '', options: [], answers: [] }
  editingAnswerText.value = ''; questionDialogVisible.value = true
}
function resetQuestionShape(type) {
  if (type === 'TRUE_FALSE') editingQuestion.value.options = [{ optionKey: 'A', optionContent: '正确', sortOrder: 1 }, { optionKey: 'B', optionContent: '错误', sortOrder: 2 }]
  else if (['SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(type) && !editingQuestion.value.options.length) editingQuestion.value.options = ['A', 'B', 'C', 'D'].map((key, index) => ({ optionKey: key, optionContent: '', sortOrder: index + 1 }))
  else if (!isChoiceQuestion(type)) editingQuestion.value.options = []
}
function addOption() { const index = editingQuestion.value.options.length; editingQuestion.value.options.push({ optionKey: String.fromCharCode(65 + index), optionContent: '', sortOrder: index + 1 }) }
function saveQuestionEdit() {
  const question = editingQuestion.value
  if (!question.content?.trim()) return ElMessage.warning('请输入题干')
  const answers = editingAnswerText.value.split(/\r?\n|,/).map(item => item.trim()).filter(Boolean).map((answerContent, index) => ({ answerContent, sortOrder: index + 1 }))
  if (!answers.length) return ElMessage.warning('请输入答案')
  question.answers = answers
  question.options = isChoiceQuestion(question.type) ? (question.options || []).filter(item => item.optionContent?.trim()).map((item, index) => ({ ...item, sortOrder: index + 1 })) : []
  if (isChoiceQuestion(question.type) && question.options.length < 2) return ElMessage.warning('选择题至少需要两个选项')
  if (editingQuestionIsNew.value) editingQuestionGroup.value.questions.push(question)
  else if (editingQuestionIndex.value >= 0) editingQuestionGroup.value.questions.splice(editingQuestionIndex.value, 1, question)
  questionDialogVisible.value = false
}

// The final review button explicitly confirms an append to the course being created.
// The existing application endpoint retains version checks and idempotency.
async function finishCreationPreparation(target) {
  try {
    if (!await draftSave.flush() || !isSameContext(target)) return
    applicationPreview.value = requireSuccess(await previewPreparationApplication(target.id)).data
    if (!isSameContext(target)) return
    Object.keys(replacementTargets).forEach(key => delete replacementTargets[key])
    if (applicationPreview.value.alreadyApplied) {
      await readProject(target.id)
      return
    }
    await applyApplication()
  } catch (problem) {
    reportWriteFailure('内容写入未完成，请重试确认', problem)
  }
}

async function previewApplication() {
  if (!readyForApplication.value || contextBusy.value) return
  const target = pinEditingContext()
  if (!target.id) return
  publishing.value = true
  try {
    if (!await draftSave.flush()) return
    applicationPreview.value = requireSuccess(await previewPreparationApplication(target.id)).data
    courseInfo.value = requireSuccess(await getCourseById(props.courseId)).data
    Object.keys(replacementTargets).forEach(key => delete replacementTargets[key])
    if (applicationPreview.value.alreadyApplied) {
      lastApplicationId.value = applicationPreview.value.applicationId
      await readProject(target.id)
      return
    }
    applicationVisible.value = true
  } catch (problem) { reportWriteFailure('应用预览未加载', problem) }
  finally { publishing.value = false }
}
async function applyApplication() {
  const replacements = Object.entries(replacementTargets).filter(([, id]) => id).map(([lessonKey, targetLessonId]) => ({ lessonKey, targetLessonId }))
  if (new Set(replacements.map(item => item.targetLessonId)).size !== replacements.length) return ElMessage.warning('同一个原课时只能选择一次')
  publishing.value = true
  try {
    const result = requireSuccess(await applyPreparationToCourse(project.value.id, { courseVersion: applicationPreview.value.courseVersion, replacements }))
    lastApplicationId.value = result.data.applicationId
    applicationVisible.value = false
    project.value = { ...project.value, projectStatus: 'PUBLISHED', publishedCourseId: props.courseId }
    emit('applied', result.data)
    await readProject(project.value.id)
    await loadRecentDrafts()
    ElMessage.success('生成内容已应用到当前课程')
  } catch (problem) { reportWriteFailure(props.creationMode ? '内容写入未完成，请重试确认' : '内容未应用，请重新预览后重试', problem) }
  finally { publishing.value = false }
}
async function rollbackApplication() {
  try { await ElMessageBox.confirm('恢复应用前的课时内容，并撤回本次新增的内容？', '回退本次应用', { type: 'warning' }) } catch { return }
  publishing.value = true
  try {
    requireSuccess(await rollbackPreparationApplication(project.value.id, lastApplicationId.value))
    applicationRead += 1
    lastApplicationId.value = null
    applicationRolledBack.value = true
    emit('applied')
    await readProject(project.value.id)
    ElMessage.success('已回退本次应用')
  } catch (problem) { reportWriteFailure('回退未完成', problem) }
  finally { publishing.value = false }
}

async function publishCourse() {
  if (!readyForApplication.value || contextBusy.value) return
  const target = pinEditingContext()
  if (!target.id) return
  publishing.value = true
  try {
    const res = await publishPreparation(target.id)
    if (res.code !== '200') {
      reportWriteFailure('课程草稿未发布', res)
      return
    }
    ElMessage.success(`课程草稿已发布：${res.data.chaptersCreated}章 / ${res.data.lessonsCreated}课时 / ${res.data.questionsCreated}题`)
    // This result belongs to the target draft, so it may only change the page if that is still shown.
    if (!isSameContext(target)) return
    // Recorded before the re-read: if that fails, the publish button must not invite a second publish.
    project.value = { ...project.value, projectStatus: 'PUBLISHED', publishedCourseId: res.data.courseId ?? project.value.publishedCourseId }
    await readProject(target.id)
  } catch (error) {
    reportWriteFailure('课程草稿发布结果未确认', error)
  } finally { publishing.value = false }
}
function openPublishedCourse() { router.push({ path: `/courses/${props.courseId || project.value.publishedCourseId}`, query: { tab: 'content' } }) }

defineExpose({
  async flushDraft() {
    if (contextBusy.value || changingStep.value) return false
    const saved = await configSave.flush() && await draftSave.flush()
    await nextTick()
    return saved
  }
})

onMounted(loadInitialData)
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', warnBeforeLeave)
  stopPoll()
  stopSourceWait()
  editingContext += 1
})
</script>

<style scoped>
.preparation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-4) var(--space-5);
  padding: var(--space-5);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-base);
  box-shadow: var(--shadow-1);
}
.preparation-heading { display: flex; align-items: flex-start; gap: var(--space-3); flex: 1 1 320px; min-width: 0; }
.preparation-heading-icon {
  display: grid;
  place-items: center;
  flex: 0 0 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: var(--surface-accent);
  color: var(--accent);
  font-size: 22px;
}
.preparation-heading-copy { min-width: 0; }
.preparation-heading-meta { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-1) var(--space-3); margin-bottom: var(--space-1); }
.preparation-eyebrow { color: var(--accent); font-size: var(--text-xs); font-weight: 650; }
.preparation-heading h1 { margin: 0; color: var(--text-strong); font-size: var(--text-xl); line-height: 1.3; }
.preparation-heading p { margin: var(--space-2) 0 0; color: var(--text-subtle); font-size: var(--text-sm); line-height: 1.55; }
.preparation-header-controls { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-2); flex: 0 1 auto; min-width: 0; }
.draft-select { width: clamp(190px, 18vw, 260px); max-width: 100%; }
.preparation-header-actions { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-2); }
.preparation-header-actions :deep(.el-button + .el-button) { margin-left: 0; }
.item-alert { margin: var(--space-2) 0; }
.previous-version { margin: var(--space-2) 0; font-size: var(--text-sm); }
.previous-version pre { max-height: 240px; overflow: auto; white-space: pre-wrap; padding: var(--space-3); background: var(--surface-sunken); border-radius: var(--radius-md); }
.group-actions { display: flex; flex-wrap: wrap; gap: var(--space-2); margin-bottom: var(--space-2); }
.group-actions .el-button + .el-button { margin-left: 0; }
.context-lock-note { margin: 0; color: var(--text-subtle); font-size: var(--text-sm); }
.stage-steps {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  list-style: none;
  margin: 0;
  padding: 0;
}
.stage-steps li { position: relative; min-width: 0; }
.stage-steps li:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 13px;
  left: calc(50% + 13px);
  width: calc(100% - 26px);
  border-top: 1px solid var(--line-default);
  pointer-events: none;
}
.stage-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  gap: 4px;
  padding: 0 var(--space-1) var(--space-1);
  border: none;
  background: none;
  color: var(--text-body);
  font: inherit;
  text-align: center;
  cursor: pointer;
}
.stage-step:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.stage-step:disabled { cursor: wait; }
.stage-number {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  font-size: var(--text-sm);
}
.stage-title { font-weight: 600; }
.stage-description { color: var(--text-subtle); font-size: var(--text-xs); }
.stage-steps .is-complete .stage-number { color: var(--positive); border-color: var(--positive); }
.stage-step:hover:not(:disabled), .stage-step[aria-current="step"] { color: var(--accent); }
.stage-step[aria-current="step"] .stage-number {
  color: var(--accent);
  border-color: var(--accent);
  background: var(--surface-accent);
}
.stage-panel { margin-top: var(--space-6); min-height: 480px; }
.stage-notice { margin-bottom: var(--space-4); }
.section-title { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: var(--space-4); margin-bottom: var(--space-5); }
.section-title span { color: var(--accent); font-size: var(--text-xs); font-weight: 650; }
.section-title h3 { margin: var(--space-1) 0 0; font-size: var(--text-xl); }
.section-title p { margin: 0; color: var(--text-subtle); }
.inline-title { align-items: center; }
.form-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0 var(--space-4); }
.form-grid.two-columns { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.subsection { margin-top: var(--space-4); padding: var(--space-5); border: 1px solid var(--line-subtle); border-radius: var(--radius-lg); background: var(--surface-canvas); }
.subsection-heading { display: flex; align-items: baseline; flex-wrap: wrap; gap: var(--space-2) var(--space-3); margin-bottom: var(--space-4); }
.subsection-heading h4 { margin: 0; font-size: var(--text-lg); }
.subsection-heading span { color: var(--text-subtle); font-size: var(--text-sm); }
.source-status-list { margin-top: var(--space-3); padding: var(--space-3); background: var(--surface-base); border: 1px solid var(--line-subtle); border-radius: var(--radius-md); }
.source-wait { margin: 0 0 var(--space-2); color: var(--text-subtle); font-size: var(--text-sm); }
.source-error { flex-basis: 100%; color: var(--critical); }
.source-status-list > div { display: flex; justify-content: space-between; flex-wrap: wrap; gap: var(--space-2); margin-bottom: var(--space-2); overflow-wrap: anywhere; }
.draft-save-status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 2px var(--space-2);
  border-radius: var(--radius-pill);
  background: var(--surface-sunken);
  color: var(--text-subtle);
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  line-height: 1.4;
}
.draft-save-status.is-saved .el-icon { color: var(--positive); }
.draft-save-status.is-error, .draft-save-status.is-offline { color: var(--caution); }
.stage-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: var(--space-2); padding: var(--space-6) 0 var(--space-2); }
.between-actions { justify-content: space-between; }
.between-actions > div { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.between-actions :deep(.el-button + .el-button) { margin-left: 0; }
.generation-banner { display: grid; grid-template-columns: minmax(250px, .7fr) 1.3fr; align-items: center; gap: var(--space-6); margin-top: var(--space-5); padding: var(--space-4) var(--space-5); background: var(--surface-accent); border: 1px solid var(--line-accent); border-radius: var(--radius-lg); }
.generation-banner strong, .generation-banner span { display: block; }
.generation-banner strong { color: var(--text-strong); }
.generation-banner span { margin-top: 2px; color: var(--text-body); font-size: var(--text-sm); }
.outline-list { display: grid; gap: var(--space-3); }
.chapter-editor { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; margin-bottom: var(--space-3); }
.chapter-title-input { flex: 1 1 220px; max-width: 520px; }
.order-badge { flex: 0 0 auto; width: 32px; height: 32px; display: grid; place-items: center; color: #fff; background: var(--accent-solid); border-radius: var(--radius-md); font-weight: 650; }
.lesson-skeleton-list { margin-top: var(--space-3); padding-left: 44px; }
.lesson-skeleton { display: grid; grid-template-columns: 55px minmax(0, 1fr) minmax(0, 1.3fr) 130px 40px; gap: var(--space-2); align-items: center; margin-bottom: var(--space-2); color: var(--text-subtle); }
.lesson-tabs { min-height: 590px; }
.lesson-tabs :deep(.el-tabs__header) { width: 230px; }
.lesson-tabs :deep(.el-tabs__content) { min-width: 0; }
.lesson-editor-heading { display: flex; gap: var(--space-3); align-items: center; margin-bottom: var(--space-3); }
.markdown-editor { height: 560px; }
.question-count { margin-left: var(--space-2); }
.publish-stage { display: grid; place-items: center; }
.course-preparation-actions { display: flex; justify-content: flex-end; }
.course-preparation-actions:empty { display: none; }
.creation-complete { padding-block: 70px; }
.creation-complete :deep(.el-result__icon svg) { width: 88px; height: 88px; }
.publish-summary { max-width: 760px; text-align: center; }
.publish-summary > .el-icon { font-size: 56px; color: var(--positive); }
.publish-summary h3 { font-size: var(--text-2xl); margin: var(--space-3) 0 var(--space-2); }
.publish-summary p { color: var(--text-subtle); line-height: 1.7; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-3); margin: var(--space-6) 0; }
.summary-grid div { padding: var(--space-5) var(--space-3); background: var(--surface-canvas); border: 1px solid var(--line-subtle); border-radius: var(--radius-lg); }
.summary-grid strong, .summary-grid span { display: block; }
.summary-grid strong { color: var(--text-strong); font-size: var(--text-2xl); font-weight: 650; }
.summary-grid span { margin-top: 2px; color: var(--text-subtle); }
.option-editor { width: 100%; }
.option-editor > div { display: flex; gap: var(--space-2); margin-bottom: var(--space-2); }
.option-key { flex: 0 0 70px; width: 70px; }
@media (max-width: 1100px) {
  .form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .lesson-skeleton { grid-template-columns: 45px minmax(0, 1fr) minmax(0, 1fr); }
  .lesson-skeleton > :nth-child(n+4) { grid-column: auto; }
}
@media (max-width: 860px) {
  .stage-description { display: none; }
  .stage-title { font-size: var(--text-xs); line-height: 1.4; }
}
@media (max-width: 720px) {
  .form-grid, .form-grid.two-columns { grid-template-columns: minmax(0, 1fr); }
  .generation-banner { grid-template-columns: minmax(0, 1fr); gap: var(--space-3); }
  .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .lesson-skeleton-list { padding-left: 0; }
  .lesson-skeleton { grid-template-columns: minmax(0, 1fr); }
  .lesson-tabs :deep(.el-tabs__header) { width: 120px; }
  .preparation-header { padding: var(--space-4); }
  .preparation-header-controls { flex: 1 1 100%; }
  .draft-select { flex: 1 1 100%; width: 100%; }
}
</style>
