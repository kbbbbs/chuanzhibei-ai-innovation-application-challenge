package com.cupk.aiacademy.agent.query;

import java.time.LocalDateTime;

public record NaturalLanguageTimeRange(
        LocalDateTime start,
        LocalDateTime end,
        String source
) {
    public boolean present() {
        return start != null || end != null;
    }
}
