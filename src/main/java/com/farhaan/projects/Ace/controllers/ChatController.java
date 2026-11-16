package com.farhaan.projects.Ace.controllers;

import com.farhaan.projects.Ace.Dto.chat.ChatMessageResponse;
import com.farhaan.projects.Ace.Dto.chat.ChatRequest;
import com.farhaan.projects.Ace.intelligence.ChatOrchestrator;
import com.farhaan.projects.Ace.intelligence.ChatService;
import com.farhaan.projects.Ace.intelligence.SseWriter;
import com.farhaan.projects.Ace.security.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/chat")
public class ChatController {
    private final ChatOrchestrator orchestrator;
    private final ChatService chatService;

    @GetMapping("/messages")
    public ResponseEntity<List<ChatMessageResponse>> history(@PathVariable Long projectId) {
        return ResponseEntity.ok(chatService.history(projectId, CurrentUser.id()));
    }

    /**
     * Streams the answer as Server-Sent Events (text/event-stream).
     * Events: start, text, tool, file_start, file_delta, file_end, file_error, warning, error, done.
     * The method simply blocks while writing; Java 21 virtual threads make that cheap.
     */
    @PostMapping("/stream")
    public void stream(@PathVariable Long projectId,
                       @RequestBody @Valid ChatRequest request,
                       HttpServletResponse response) throws IOException {
        Long userId = CurrentUser.id();           // read it now: it is not available on other threads
        orchestrator.preflight(projectId, userId);

        response.setStatus(200);
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");   // tells nginx not to buffer the stream
        response.flushBuffer();

        orchestrator.run(projectId, userId, request.message(), new SseWriter(response));
    }
}
