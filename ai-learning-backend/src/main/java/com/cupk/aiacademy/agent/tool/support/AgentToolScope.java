package com.cupk.aiacademy.agent.tool.support;

import java.util.function.Supplier;

/** Server-owned course context for a single synchronous tool planning/execution turn. */
public final class AgentToolScope {
    private static final ThreadLocal<Integer> COURSE = new ThreadLocal<>();
    private AgentToolScope() {}

    public static Integer courseId() { return COURSE.get(); }

    public static <T> T inCourse(Integer courseId, Supplier<T> work) {
        Integer previous = COURSE.get();
        if (courseId == null) COURSE.remove(); else COURSE.set(courseId);
        try { return work.get(); }
        finally { if (previous == null) COURSE.remove(); else COURSE.set(previous); }
    }
}
