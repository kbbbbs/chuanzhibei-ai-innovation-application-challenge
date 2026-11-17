package com.cupk.aiacademy.agent.kernel;

import java.util.*;
import java.util.function.Supplier;

/** Scoped observer for actual execution facts, never model reasoning or tool arguments. */
public final class AgentExecutionProgress {
    private static final ThreadLocal<Observer> OBSERVER = new ThreadLocal<>();
    private AgentExecutionProgress() {}
    @FunctionalInterface public interface Observer {
        void accept(String state, String toolName, KernelEvent.ToolSummary result);
    }
    public static void emit(String state, String toolName) { emit(state, toolName, null); }
    public static void emit(String state, String toolName, KernelEvent.ToolSummary result) {
        var listener = OBSERVER.get();
        if (listener != null) listener.accept(state, toolName, result);
    }
    public static <T> T observe(Observer observer, Supplier<T> work) {
        var previous = OBSERVER.get();
        OBSERVER.set(observer);
        try { return work.get(); }
        finally { if (previous == null) OBSERVER.remove(); else OBSERVER.set(previous); }
    }
    public static KernelEvent.ToolSummary summary(Map<String,Object> data){
        var items=data.get("items") instanceof List<?> list?list:List.of();
        Integer count=data.get("count") instanceof Number n&&n.longValue()>=0&&n.longValue()<=Integer.MAX_VALUE?n.intValue():data.containsKey("items")?items.size():null;
        var ids=new ArrayList<String>();
        for(Object item:items){
            if(item instanceof Map<?,?> row&&row.get("id")!=null){
                String id=Objects.toString(row.get("id"));
                if(id.matches("[A-Za-z0-9_-]{1,64}"))ids.add(id);
            }
            if(ids.size()==10)break;
        }
        return count==null&&ids.isEmpty()?null:new KernelEvent.ToolSummary(count,ids);
    }
}
