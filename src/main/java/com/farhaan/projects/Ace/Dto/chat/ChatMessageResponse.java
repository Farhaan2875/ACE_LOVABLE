package com.farhaan.projects.Ace.Dto.chat;

import java.time.Instant;
import java.util.List;

public record ChatMessageResponse(
        Long id,
        String role,           // USER | ASSISTANT
        String content,
        List<String> files,    // files the assistant wrote in this answer
        Instant createdAt
) {
}
