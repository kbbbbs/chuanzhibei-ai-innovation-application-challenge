package com.cupk.aiacademy.agent.kernel;

/** Public execution facts only: never prompts, arguments, credentials or reasoning. */
public record KernelEvent(long sequence, String state, String toolName, long elapsedMillis, ToolSummary result) {
    public KernelEvent(long sequence, String state, String toolName, long elapsedMillis) {
        this(sequence,state,toolName,elapsedMillis,null);
    }
    /** Public references only. Never forward the original tool payload or parameters. */
    public record ToolSummary(Integer itemCount, java.util.List<String> entityIds) {
        public ToolSummary { entityIds=java.util.List.copyOf(entityIds); }
    }
}
