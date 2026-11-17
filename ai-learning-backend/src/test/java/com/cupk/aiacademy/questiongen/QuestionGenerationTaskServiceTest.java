package com.cupk.aiacademy.questiongen;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.GenerateQuestionsFromKbRequest;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.dto.QuestionResponseDTO;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.rag.NativeRagService;
import com.cupk.aiacademy.service.PermissionService;
import com.cupk.aiacademy.service.QuestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real H2 task storage; question generation and the question bank are controlled. */
class QuestionGenerationTaskServiceTest {
    private final QuestionService questions = mock(QuestionService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final NativeRagService rag = mock(NativeRagService.class);
    private final List<Runnable> queued = new ArrayList<>();
    private final List<Integer> requestedCounts = new ArrayList<>();
    private final AtomicInteger made = new AtomicInteger();
    private final Set<Integer> failingCalls = new HashSet<>();
    private QuestionGenerationTaskService service;
    private final String kb = UUID.randomUUID().toString();

    @BeforeEach
    void setup() {
        JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
        service = new QuestionGenerationTaskService(db, questions, permissions, rag, new ObjectMapper(), queued::add);
        service.initialize();
        login(7);
        Course course = new Course();
        course.setId(3);
        course.setTeacherId(7);
        when(permissions.getCourse(3)).thenReturn(course);
        when(permissions.resolveActiveUser(anyInt())).thenAnswer(call -> user(call.getArgument(0)));
        when(questions.generateFromKnowledgeBase(any(), eq(7L))).thenAnswer(call -> {
            GenerateQuestionsFromKbRequest request = call.getArgument(0);
            requestedCounts.add(request.getCount());
            if (failingCalls.remove(requestedCounts.size())) throw new BusinessException("模型暂不可用");
            List<QuestionRequestDTO> batch = new ArrayList<>();
            for (int i = 0; i < request.getCount(); i++) batch.add(question("题目 " + made.incrementAndGet()));
            return batch;
        });
    }

    @AfterEach
    void clear() { UserContext.clear(); }

    @Test
    void finishedBatchesSurviveAFailureAndResumingGeneratesOnlyTheRemainder() {
        failingCalls.add(2);
        String id = id(service.start(request(12)));
        runQueued();

        Map<String, Object> interrupted = service.get(id);
        assertEquals("INCOMPLETE", interrupted.get("status"));
        assertEquals(5, interrupted.get("generated"));
        assertTrue(interrupted.get("errorMessage").toString().contains("模型暂不可用"));

        service.resume(id);
        runQueued();
        Map<String, Object> done = service.get(id);
        assertEquals("READY", done.get("status"));
        assertEquals(12, done.get("generated"));
        assertEquals(List.of(5, 5, 5, 2), requestedCounts);
        assertEquals(12, drafts(done).stream().map(item -> item.get("content")).distinct().count());
        assertNull(UserContext.get(), "the worker must not leave an identity behind");
        login(7);
    }

    @Test
    void savingTwiceNeverWritesTheSameQuestionTwice() {
        String id = id(service.start(request(3)));
        runQueued();
        AtomicInteger creates = new AtomicInteger();
        when(questions.create(any())).thenAnswer(call -> {
            if (creates.incrementAndGet() == 2) throw new BusinessException("答案缺失");
            QuestionResponseDTO created = new QuestionResponseDTO();
            created.setId((long) creates.get());
            return created;
        });

        Map<String, Object> first = service.save(id);
        assertEquals(2, first.get("savedNow"));
        assertEquals(1, first.get("failedNow"));
        assertEquals("READY", service.get(id).get("status"));

        Map<String, Object> second = service.save(id);
        assertEquals(1, second.get("savedNow"));
        assertEquals(4, creates.get());
        assertEquals("SAVED", service.get(id).get("status"));
        assertEquals(0, service.save(id).get("savedNow"));
        assertEquals(4, creates.get());
    }

    @Test
    void editsReplaceUnsavedDraftsButNeverChangeSavedOnes() {
        String id = id(service.start(request(3)));
        runQueued();
        when(questions.create(any())).thenAnswer(call -> {
            if (((QuestionRequestDTO) call.getArgument(0)).getContent().equals("题目 1")) {
                QuestionResponseDTO created = new QuestionResponseDTO();
                created.setId(1L);
                return created;
            }
            throw new BusinessException("稍后再存");
        });
        service.save(id);

        List<Map<String, Object>> edited = new ArrayList<>();
        for (Map<String, Object> draft : drafts(service.get(id))) edited.add(new LinkedHashMap<>(draft));
        edited.getFirst().put("content", "试图修改已入库的题");
        edited.get(1).put("content", "改写后的第二题");
        edited.remove(2);
        List<Map<String, Object>> stored = drafts(service.updateDrafts(id, edited));

        assertEquals(List.of("题目 1", "改写后的第二题"), stored.stream().map(item -> item.get("content")).toList());
        assertEquals(true, stored.getFirst().get("saved"));
    }

    @Test
    void tasksBelongToTheTeacherWhoStartedThem() {
        String id = id(service.start(request(2)));
        login(8);
        assertThrows(ForbiddenException.class, () -> service.get(id));
        assertTrue(service.list(kb).isEmpty());
        login(7);
        assertEquals(1, service.list(kb).size());
    }

    private void runQueued() {
        while (!queued.isEmpty()) queued.removeFirst().run();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> drafts(Map<String, Object> view) {
        return (List<Map<String, Object>>) view.get("questions");
    }

    private String id(Map<String, Object> view) { return view.get("id").toString(); }

    private GenerateQuestionsFromKbRequest request(int count) {
        GenerateQuestionsFromKbRequest request = new GenerateQuestionsFromKbRequest();
        request.setKnowledgeBaseId(kb);
        request.setCourseId(3L);
        request.setCount(count);
        return request;
    }

    private QuestionRequestDTO question(String content) {
        QuestionRequestDTO question = new QuestionRequestDTO();
        question.setContent(content);
        question.setType(QuestionType.SHORT_ANSWER);
        question.setCourseId(3L);
        question.setTeacherId(7L);
        return question;
    }

    private AuthUtils.LoginUser user(int id) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(id);
        user.setRoleCode("teacher");
        return user;
    }

    private void login(int id) {
        UserContext.set(user(id));
        when(permissions.currentUser()).thenReturn(user(id));
    }
}
