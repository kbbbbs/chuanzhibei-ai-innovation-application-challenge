package com.cupk.aiacademy.preparation;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.pojo.CourseCategory;
import com.cupk.aiacademy.rag.NativeRagService;
import com.cupk.aiacademy.rag.RagSearchHit;
import com.cupk.aiacademy.rag.RagSearchResponse;
import com.cupk.aiacademy.service.PermissionService;
import com.cupk.aiacademy.service.QuestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PreparationNativeKnowledgeTest {
    private final ObjectMapper json = new ObjectMapper();
    private final PreparationMapper mapper = mock(PreparationMapper.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final NativeRagService rag = mock(NativeRagService.class);
    private final PreparationModelGateway model = mock(PreparationModelGateway.class);
    private final CourseCategoryMapper categories = mock(CourseCategoryMapper.class);
    private final List<Runnable> queued = new ArrayList<>();
    private final PreparationService service = new PreparationService(mapper, mock(CourseMapper.class), categories,
            mock(ChapterMapper.class), mock(LessonMapper.class), mock(QuestionService.class), permissions,
            rag, model, json, queued::add, 10);

    @AfterEach
    void clearLogin() { UserContext.clear(); }

    @Test
    void asynchronousGenerationPassesPersistedOwnerWithoutAThreadLocalLogin() throws Exception {
        PreparationProject project = project();
        requestOwner(project);
        when(rag.searchForOwner(eq(List.of("kb-1")), anyString(), eq(7), eq(10))).thenAnswer(call -> {
            assertNull(UserContext.get(), "worker must not rely on request-thread identity");
            return Result.success(new RagSearchResponse(List.of(source()), "bm25", ""));
        });
        when(model.generateOutline(anyString(), anyString())).thenReturn(outline());

        service.startOutline(1L);
        assertEquals(1, queued.size());
        UserContext.clear();
        runWorker();

        verify(rag).searchForOwner(eq(List.of("kb-1")), anyString(), eq(7), eq(10));
        verify(model).generateOutline(anyString(), contains("文档ID：doc-1；片段ID：chunk-1；第2页"));
        assertUpdateContains("OUTLINE_REVIEW");
    }

    @Test
    void privateForeignBaseIsRejectedBeforeAJobIsQueued() throws Exception {
        PreparationProject project = project();
        requestOwner(project);
        doThrow(new BusinessException("无权访问知识库"))
                .when(rag).requireReadableBaseIdsForOwner(List.of("kb-1"), 7);

        assertThrows(BusinessException.class, () -> service.startOutline(1L));

        assertTrue(queued.isEmpty());
        verify(mapper, never()).updateById(any(PreparationProject.class));
        verifyNoInteractions(model);
    }

    @Test
    void accessRevokedAfterQueueingIsCheckedAgainInsideTheWorker() throws Exception {
        PreparationProject project = project();
        requestOwner(project);
        when(rag.requireReadableBaseIdsForOwner(List.of("kb-1"), 7))
                .thenReturn(List.of("kb-1")).thenThrow(new BusinessException("资料共享已撤销"));

        service.startOutline(1L);
        runWorker();

        verifyNoInteractions(model);
        verify(rag, never()).searchForOwner(anyList(), anyString(), anyInt(), anyInt());
        assertUpdateContains("FAILED");
        assertUpdateContains("资料共享已撤销");
    }

    @Test
    void retrievalFailureMarksTheJobFailedWithoutGeneratingUnsubstantiatedContent() throws Exception {
        PreparationProject project = project();
        requestOwner(project);
        when(rag.searchForOwner(anyList(), anyString(), eq(7), eq(10)))
                .thenReturn(Result.error("503", "索引服务不可用"));

        service.startOutline(1L);
        runWorker();

        verifyNoInteractions(model);
        assertUpdateContains("FAILED");
        assertUpdateContains("索引服务不可用");
    }

    @Test
    void unmatchingCourseQueryUsesRealAuthorizedExcerptsWithAnExplicitStrategy() throws Exception {
        PreparationProject project = project();
        requestOwner(project);
        when(rag.searchForOwner(anyList(), anyString(), eq(7), eq(10)))
                .thenReturn(Result.success(new RagSearchResponse(List.of(), "bm25", "")));
        when(rag.listKnowledgeForOwner("kb-1", 7, 200, 0))
                .thenReturn(Result.success(List.of(Map.of("id", "doc-1", "parseStatus", "success"))));
        when(rag.contentForOwner("doc-1", 7)).thenReturn(Result.success(Map.of("chunks", List.of(source()))));
        when(model.generateOutline(anyString(), anyString())).thenReturn(outline());

        service.startOutline(1L);
        runWorker();

        verify(model).generateOutline(anyString(), contains("这些片段不是问题相关性命中"));
        verify(rag).contentForOwner("doc-1", 7);
        assertUpdateContains("OUTLINE_REVIEW");
    }

    @Test
    void configurationCannotBindAnUnreadableBase() throws Exception {
        PreparationProject project = project();
        requestOwner(project);
        when(categories.selectById(1)).thenReturn(new CourseCategory());
        doThrow(new BusinessException("知识库不存在或不可读"))
                .when(rag).requireReadableBaseIdsForOwner(List.of("kb-1"), 7);

        assertThrows(BusinessException.class, () -> service.updateConfig(1L, config()));
        assertThrows(BusinessException.class, () -> service.create(config()));

        verify(mapper, never()).insert(any(PreparationProject.class));
        verify(mapper, never()).updateById(any(PreparationProject.class));
    }

    @Test
    void generatedLessonStoresExactlyTheSourceContextActuallyGivenToTheModel() throws Exception {
        PreparationProject project = project();
        project.setOutlineJson(json.writeValueAsString(outline()));
        requestOwner(project);
        when(rag.searchForOwner(anyList(), anyString(), eq(7), eq(10)))
                .thenReturn(Result.success(new RagSearchResponse(List.of(source()), "bm25", "")));
        when(model.generateMarkdown(anyString(), anyString())).thenReturn("# 模型生成的真实课时\n\n教学内容。");

        service.startLessons(1L);
        runWorker();

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(model).generateMarkdown(anyString(), prompt.capture());
        assertTrue(prompt.getValue().contains("片段ID：chunk-1"));
        assertUpdateContains("生成参考资料（供教师核验）");
        assertUpdateContains("片段ID：chunk-1");
    }

    @Test
    void nativeUploadTracksTheActualDocumentAndDeduplicatesRepeatedSourceFiles() throws Exception {
        PreparationProject project = project();
        project.setOwnedKnowledgeBaseId("kb-owned");
        requestOwner(project);
        when(rag.uploadFile(eq("kb-owned"), any(byte[].class), eq("教材.md")))
                .thenReturn(Result.success(Map.of("id", "doc-1", "parseStatus", "success", "parseProgress", 100)));
        when(rag.listKnowledgeForOwner("kb-owned", 7, 200, 0))
                .thenReturn(Result.success(List.of(Map.of("id", "doc-1", "title", "教材.md", "parseStatus", "success"))));
        var file = new MockMultipartFile("file", "教材.md", "text/markdown", "真实教材内容".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        service.uploadSources(1L, List.of(file));
        PreparationModels.View view = service.uploadSources(1L, List.of(file));

        assertTrue(view.isSourcesReady());
        assertEquals(1, view.getSourceFiles().size());
        assertEquals("doc-1", view.getSourceFiles().getFirst().getKnowledgeId());
        assertEquals("success", view.getSourceFiles().getFirst().getParseStatus());
        verify(rag, times(2)).listKnowledgeForOwner("kb-owned", 7, 200, 0);
        verifyNoInteractions(model);
    }

    @Test
    void deletedUploadedSourceCannotRemainReadyOrWaitForeverForParsing() throws Exception {
        PreparationProject project = project();
        project.setOwnedKnowledgeBaseId("kb-owned");
        project.setSourceFilesJson("[{\"name\":\"教材.md\",\"knowledgeId\":\"deleted-doc\",\"parseStatus\":\"success\"}]");
        requestOwner(project);
        when(rag.listKnowledgeForOwner("kb-owned", 7, 200, 0)).thenReturn(Result.success(List.of()));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.startOutline(1L));

        assertTrue(failure.getMessage().contains("原资料已删除"));
        assertTrue(queued.isEmpty());
        verifyNoInteractions(model);
    }

    @Test
    void sharingRevokedDuringModelCallPreventsPersistingTheCompletedDraft() throws Exception {
        PreparationProject project = project();
        requestOwner(project);
        when(rag.searchForOwner(anyList(), anyString(), eq(7), eq(10)))
                .thenReturn(Result.success(new RagSearchResponse(List.of(source()), "bm25", "")));
        when(model.generateOutline(anyString(), anyString())).thenAnswer(call -> {
            doThrow(new BusinessException("资料共享已撤销")).when(rag).requireReadableBaseIdsForOwner(List.of("kb-1"), 7);
            return outline();
        });

        service.startOutline(1L);
        runWorker();

        assertUpdateContains("FAILED");
        assertUpdateContains("资料共享已撤销");
        assertUpdateDoesNotContain("OUTLINE_REVIEW");
    }

    @Test
    void providerErrorsCannotBeStoredVerbatimInTheUserVisibleFailureStatus() throws Exception {
        PreparationProject project = project();
        requestOwner(project);
        when(rag.searchForOwner(anyList(), anyString(), eq(7), eq(10)))
                .thenReturn(Result.success(new RagSearchResponse(List.of(source()), "bm25", "")));
        when(model.generateOutline(anyString(), anyString())).thenThrow(new BusinessException("PRIVATE_PROVIDER_RESPONSE"));

        service.startOutline(1L);
        runWorker();

        assertUpdateContains("FAILED");
        assertUpdateContains("检查模型配置");
        assertUpdateDoesNotContain("PRIVATE_PROVIDER_RESPONSE");
    }

    private PreparationProject project() throws Exception {
        PreparationProject project = new PreparationProject();
        project.setId(1L);
        project.setTeacherId(7);
        project.setTitle("人工智能入门");
        project.setConfigJson(json.writeValueAsString(config()));
        project.setKnowledgeBaseIdsJson("[\"kb-1\"]");
        project.setSourceFilesJson("[]");
        project.setLessonsJson("[]");
        project.setQuestionsJson("[]");
        project.setProjectStatus("DRAFT");
        project.setCurrentStage("CONFIG");
        project.setGenerationStatus("IDLE");
        project.setProgress(0);
        project.setVersion(0);
        return project;
    }

    private PreparationModels.Config config() {
        PreparationModels.Config config = new PreparationModels.Config();
        config.setTitle("人工智能入门");
        config.setCategoryId(1);
        config.setAudience("本科生");
        config.setChapterCount(1);
        config.setLessonsPerChapter(1);
        config.setKnowledgeBaseIds(List.of("kb-1"));
        return config;
    }

    private PreparationModels.Outline outline() {
        PreparationModels.LessonDraft lesson = new PreparationModels.LessonDraft();
        lesson.setLessonKey("lesson-1");
        lesson.setTitle("智能的定义");
        PreparationModels.ChapterDraft chapter = new PreparationModels.ChapterDraft();
        chapter.setChapterKey("chapter-1");
        chapter.setTitle("人工智能绪论");
        chapter.setLessons(new ArrayList<>(List.of(lesson)));
        PreparationModels.Outline outline = new PreparationModels.Outline();
        outline.setChapters(new ArrayList<>(List.of(chapter)));
        return outline;
    }

    private RagSearchHit source() {
        return new RagSearchHit("chunk-1", "doc-1", "kb-1", "教材.pdf",
                "图灵测试用于讨论机器的智能行为。", 2, 0, 1, "[1]");
    }

    private void requestOwner(PreparationProject project) {
        Thread requestThread = Thread.currentThread();
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(7);
        user.setRoleCode("teacher");
        UserContext.set(user);
        when(permissions.currentUser()).thenAnswer(call -> {
            assertSame(requestThread, Thread.currentThread(), "worker must use persisted project ownership");
            return user;
        });
        when(mapper.selectById(1L)).thenReturn(project);
        when(mapper.updateById(any(PreparationProject.class))).thenReturn(1);
    }

    private void runWorker() throws InterruptedException {
        Thread worker = new Thread(queued.getFirst(), "preparation-native-test-worker");
        worker.start();
        worker.join(5000);
        assertFalse(worker.isAlive(), "background test should finish deterministically");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertUpdateContains(String expected) {
        ArgumentCaptor<UpdateWrapper<PreparationProject>> updates = ArgumentCaptor.forClass((Class) UpdateWrapper.class);
        verify(mapper, atLeastOnce()).update(isNull(), updates.capture());
        assertTrue(updates.getAllValues().stream().flatMap(wrapper -> wrapper.getParamNameValuePairs().values().stream())
                .anyMatch(value -> String.valueOf(value).contains(expected)), "stored update should contain " + expected);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertUpdateDoesNotContain(String unexpected) {
        ArgumentCaptor<UpdateWrapper<PreparationProject>> updates = ArgumentCaptor.forClass((Class) UpdateWrapper.class);
        verify(mapper, atLeastOnce()).update(isNull(), updates.capture());
        assertTrue(updates.getAllValues().stream().flatMap(wrapper -> wrapper.getParamNameValuePairs().values().stream())
                .noneMatch(value -> String.valueOf(value).contains(unexpected)), "stored update must not contain " + unexpected);
    }
}
