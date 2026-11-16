package com.farhaan.projects.Ace.Dto.preview;

import java.time.Instant;

public record PreviewResponse(
        String status,      // NONE | CREATING | RUNNING | FAILED | TERMINATED
        String url,
        String message,
        Instant startedAt,
        Instant updatedAt
) {
}
