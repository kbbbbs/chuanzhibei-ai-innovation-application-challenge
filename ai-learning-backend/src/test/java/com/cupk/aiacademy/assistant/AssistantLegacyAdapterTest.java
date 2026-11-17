package com.cupk.aiacademy.assistant;

import com.cupk.aiacademy.agent.core.controller.ChatController;
import com.cupk.aiacademy.dto.AgentChatRequest;
import com.cupk.aiacademy.rag.chat.NativeRagChatController;
import com.cupk.aiacademy.rag.chat.RagChatRequest;
import com.cupk.aiacademy.common.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantLegacyAdapterTest {
    @Test void legacyOwnerCanDeleteAnUnavailableCourseHistoryWithoutReadingIt() {
        var service = mock(UnifiedAssistantService.class);
        var controller = new NativeRagChatController(service,new AssistantLegacyAdapter(service));
        when(service.legacyOwnedSession("old-uuid")).thenReturn(12);
        assertEquals("200",controller.delete("old-uuid").getCode());
        verify(service).deleteSession(12);
        verify(service,never()).legacySession(anyString());
    }
    @Test void legacyRequestCannotReuseASessionForAnotherCourse() {
        var service = mock(UnifiedAssistantService.class);
        var controller = new NativeRagChatController(service,new AssistantLegacyAdapter(service));
        when(service.legacySession("12")).thenReturn(12);
        doThrow(new BusinessException("资料范围已改变，请开始新对话"))
                .when(service).assertRequestedScope(12,999,null,List.of());
        assertThrows(BusinessException.class,()->controller.chat(new RagChatRequest(List.of(),"问题","12",999,null)));
        verify(service,never()).send(anyInt(),anyMap());
    }
    @Test void legacyRagCreationPreservesExplicitCourseAndLessonScope() {
        var service = mock(UnifiedAssistantService.class);
        var adapter = new AssistantLegacyAdapter(service);
        var controller = new NativeRagChatController(service, adapter);
        var input = Map.<String,Object>of("courseId",129,"lessonId",5,"title","课时问答");
        when(service.createSession(input)).thenReturn(Map.of("id",12,"courseId",129,"lessonId",5));
        assertEquals("200",controller.create(input).getCode());
        verify(service).createSession(input);
    }

    @Test void legacyRagUuidMessagesUseMappedUnifiedSession() {
        var service = mock(UnifiedAssistantService.class);
        var controller = new NativeRagChatController(service,new AssistantLegacyAdapter(service));
        when(service.legacySession("old-uuid")).thenReturn(12);
        when(service.messages(12)).thenReturn(List.of(Map.of("role","user","content","问题")));
        assertEquals("200",controller.messages("old-uuid").getCode());
        verify(service).messages(12);
    }

    @Test void legacyAgentChatUsesSameRequestPipelineAndReturnsOldContentField() {
        var service = mock(UnifiedAssistantService.class);
        var controller = new ChatController(service,new AssistantLegacyAdapter(service));
        var input = new AgentChatRequest(); input.setContent("什么是梯度？");
        when(service.send(eq(12),anyMap())).thenReturn(Map.of("requestId","r1","status","RUNNING"));
        when(service.request(12,"r1")).thenReturn(Map.of("status","COMPLETED","result",Map.of("content","梯度表示变化方向。","citations",List.of())));
        var result = (Map<?,?>)controller.chat(12,input).getData();
        assertEquals("梯度表示变化方向。",result.get("content"));
        assertEquals(12,result.get("sessionId"));
        verify(service).send(eq(12),anyMap());
    }
}
