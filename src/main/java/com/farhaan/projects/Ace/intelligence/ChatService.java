package com.farhaan.projects.Ace.intelligence;

import com.farhaan.projects.Ace.Dto.chat.ChatMessageResponse;
import com.farhaan.projects.Ace.config.AceProperties;
import com.farhaan.projects.Ace.entities.ChatMessage;
import com.farhaan.projects.Ace.entities.ChatSession;
import com.farhaan.projects.Ace.enums.MessageRole;
import com.farhaan.projects.Ace.enums.ProjectRole;
import com.farhaan.projects.Ace.repositories.ChatMessageRepository;
import com.farhaan.projects.Ace.repositories.ChatSessionRepository;
import com.farhaan.projects.Ace.repositories.ProjectRepository;
import com.farhaan.projects.Ace.repositories.UserRepository;
import com.farhaan.projects.Ace.services.ProjectAccessService;
import com.farhaan.projects.Ace.util.Json;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The "chat-service": stores the conversation so the model gets the "past 10 chat messages" as session history. */
@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService access;
    private final AceProperties props;

    /** One session per (project, user). Created on first use. */
    @Transactional
    public ChatSession getOrCreateSession(Long projectId, Long userId) {
        return sessionRepository.findFirstByProjectIdAndUserIdAndDeletedAtIsNull(projectId, userId)
                .orElseGet(() -> sessionRepository.save(ChatSession.builder()
                        .project(projectRepository.getReferenceById(projectId))
                        .user(userRepository.getReferenceById(userId))
                        .title("Chat")
                        .build()));
    }

    /** The last N messages, oldest first (the order the model needs). */
    @Transactional(readOnly = true)
    public List<ChatMessage> recentMessages(Long sessionId) {
        List<ChatMessage> newestFirst = messageRepository.findRecent(sessionId, PageRequest.of(0, props.getLlm().getHistoryMessages()));
        List<ChatMessage> result = new ArrayList<>(newestFirst);
        Collections.reverse(result);
        return result;
    }

    @Transactional
    public void addMessage(Long sessionId, MessageRole role, String content, String toolCallsJson, Integer tokens) {
        messageRepository.save(ChatMessage.builder()
                .chatSession(sessionRepository.getReferenceById(sessionId))
                .role(role).content(content).toolCalls(toolCallsJson).tokensUsed(tokens)
                .build());
    }

    /** Whole history for the UI (not only the last 10). */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> history(Long projectId, Long userId) {
        access.requireRole(projectId, userId, ProjectRole.VIEWER);
        return sessionRepository.findFirstByProjectIdAndUserIdAndDeletedAtIsNull(projectId, userId)
                .map(session -> {
                    List<ChatMessage> all = new ArrayList<>(messageRepository.findRecent(session.getId(), PageRequest.of(0, 200)));
                    Collections.reverse(all);
                    return all.stream().map(m -> new ChatMessageResponse(
                            m.getId(), m.getRole().name(), m.getContent(), filesOf(m.getToolCalls()), m.getCreatedAt())).toList();
                })
                .orElse(List.of());
    }

    private static List<String> filesOf(String toolCallsJson) {
        if (toolCallsJson == null || toolCallsJson.isBlank()) return List.of();
        try {
            List<String> files = new ArrayList<>();
            for (JsonNode f : Json.read(toolCallsJson).path("files")) files.add(f.asText());
            return files;
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
