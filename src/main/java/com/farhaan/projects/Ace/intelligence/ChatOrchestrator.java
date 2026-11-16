package com.farhaan.projects.Ace.intelligence;

import com.farhaan.projects.Ace.Dto.project.FileNode;
import com.farhaan.projects.Ace.config.AceProperties;
import com.farhaan.projects.Ace.entities.ChatMessage;
import com.farhaan.projects.Ace.entities.ChatSession;
import com.farhaan.projects.Ace.enums.MessageRole;
import com.farhaan.projects.Ace.enums.ProjectRole;
import com.farhaan.projects.Ace.error.LlmException;
import com.farhaan.projects.Ace.events.CodeGeneratedEvent;
import com.farhaan.projects.Ace.events.CodeGeneratedPublisher;
import com.farhaan.projects.Ace.services.FileService;
import com.farhaan.projects.Ace.services.ProjectAccessService;
import com.farhaan.projects.Ace.services.UsageService;
import com.farhaan.projects.Ace.util.Json;
import com.farhaan.projects.Ace.util.PathUtil;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.util.*;

/**
 * The "intelligence-service". For one user prompt it:
 *   1. builds the context   (system prompt + file list + RAG search + last 10 messages)
 *   2. streams the open model's answer, running tool calls (list_files / get_file_content) when asked
 *   3. splits the stream into chat text and files, SSE-streams both to the browser
 *   4. stores every finished file ("Store the file index.html") and re-indexes it for RAG
 *   5. publishes "code.generated" so the execution-service refreshes the preview
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatOrchestrator {
    private final AceProperties props;
    private final ProjectAccessService access;
    private final UsageService usageService;
    private final ChatService chatService;
    private final FileService fileService;
    private final RagService ragService;
    private final OllamaClient ollama;
    private final ToolExecutor toolExecutor;
    private final PromptBuilder promptBuilder;
    private final CodeGeneratedPublisher publisher;

    /** Runs BEFORE the stream starts, so problems become normal HTTP errors (403, 404, 402) instead of SSE events. */
    public void preflight(Long projectId, Long userId) {
        access.requireRole(projectId, userId, ProjectRole.EDITOR);   // viewers may not use the AI
        usageService.assertCanUseAi(userId);
    }

    public void run(Long projectId, Long userId, String userMessage, SseWriter out) {
        long started = System.currentTimeMillis();
        ChatSession session = chatService.getOrCreateSession(projectId, userId);
        List<ChatMessage> history = chatService.recentMessages(session.getId());   // "past 10 chat messages"
        chatService.addMessage(session.getId(), MessageRole.USER, userMessage, null, null);

        StringBuilder visibleText = new StringBuilder();
        List<String> written = new ArrayList<>();
        Map<String, String> writtenContent = new LinkedHashMap<>();
        int promptTokens = 0, completionTokens = 0;
        boolean clientGone = false;
        String errorMessage = null;

        // splits the model's stream into text / files and reacts to each event
        StreamingFileParser parser = new StreamingFileParser(new StreamingFileParser.Listener() {
            private boolean skipping = false;   // true while the model writes to an illegal path

            @Override public void onText(String text) {
                visibleText.append(text);
                out.send("text", Map.of("delta", text));
            }

            @Override public void onFileStart(String path) {
                skipping = PathUtil.normalizeOrNull(path) == null;
                if (skipping) out.send("warning", Map.of("message", "Ignored a file with an illegal path: " + path));
                else out.send("file_start", Map.of("path", PathUtil.normalize(path)));      // "file created: index.html"
            }

            @Override public void onFileChunk(String path, String chunk) {
                if (!skipping) out.send("file_delta", Map.of("path", PathUtil.normalize(path), "delta", chunk));   // "content added"
            }

            @Override public void onFileEnd(String path, String content, boolean complete) {
                if (skipping) { skipping = false; return; }
                String clean = PathUtil.normalize(path);
                if (!complete) {
                    out.send("file_error", Map.of("path", clean, "message", "The model stopped before finishing this file, so it was not saved."));
                    return;
                }
                fileService.writeFile(projectId, clean, content, userId);                   // buffer finished -> store in MinIO
                if (!written.contains(clean)) written.add(clean);
                writtenContent.put(clean, content);
                out.send("file_end", Map.of("path", clean, "lines", content.lines().count(), "bytes", content.length()));
            }
        });

        try {
            out.send("start", Map.of("sessionId", session.getId(), "model", props.getLlm().getModel()));

            // ---- 1. context
            List<FileNode> files = fileService.listFiles(projectId);
            List<RagService.Snippet> snippets = ragService.search(projectId, userMessage);   // similarity search

            ArrayNode messages = Json.mapper().createArrayNode();
            messages.add(message("system", promptBuilder.build(files, snippets)));
            for (ChatMessage m : history) {
                messages.add(message(m.getRole() == MessageRole.ASSISTANT ? "assistant" : "user", m.getContent()));
            }
            messages.add(message("user", userMessage));

            // ---- 2. stream + tool loop
            boolean toolsAllowed = props.getLlm().isToolsEnabled();
            int maxRounds = props.getLlm().getMaxToolRounds();
            for (int round = 0; round <= maxRounds; round++) {
                boolean useTools = toolsAllowed && round < maxRounds;
                OllamaClient.ChatResult result;
                try {
                    result = ollama.chat(messages, useTools, parser::feed);
                } catch (LlmException e) {
                    if (useTools && e.getMessage().contains("does not support tools")) {   // some models have no tool calling: carry on without
                        toolsAllowed = false;
                        result = ollama.chat(messages, false, parser::feed);
                    } else {
                        throw e;
                    }
                }
                promptTokens += result.promptTokens();
                completionTokens += result.completionTokens();
                if (result.toolCalls().isEmpty()) break;

                // the model wants to look at the project first: run the tools and ask it again
                ObjectNode assistant = message("assistant", result.content());
                ArrayNode echoed = assistant.putArray("tool_calls");
                for (OllamaClient.ToolCall call : result.toolCalls()) {
                    ObjectNode fn = echoed.addObject().putObject("function");
                    fn.put("name", call.name());
                    fn.set("arguments", call.arguments());
                }
                messages.add(assistant);
                for (OllamaClient.ToolCall call : result.toolCalls()) {
                    out.send("tool", Map.of("name", call.name(), "arguments", call.arguments().toString()));
                    ObjectNode toolMessage = message("tool", toolExecutor.execute(projectId, call.name(), call.arguments()));
                    toolMessage.put("tool_name", call.name());
                    messages.add(toolMessage);
                }
            }
            parser.finish();
        } catch (UncheckedIOException e) {
            clientGone = true;                                    // browser closed the tab: stop quietly
            log.info("Client disconnected during chat for project {}", projectId);
        } catch (LlmException e) {
            errorMessage = e.getMessage();
        } catch (RuntimeException e) {
            log.error("Chat failed for project {}", projectId, e);
            errorMessage = "Something went wrong while generating: " + e.getMessage();
        }

        // ---- 4/5. remember what happened
        String answer = visibleText.toString().trim();
        if (answer.isEmpty() && !written.isEmpty()) answer = "Updated " + written.size() + " file(s).";
        if (answer.isEmpty() && errorMessage != null) answer = "(generation failed: " + errorMessage + ")";
        if (!answer.isEmpty()) {
            ObjectNode meta = Json.mapper().createObjectNode();
            ArrayNode filesNode = meta.putArray("files");
            written.forEach(filesNode::add);
            chatService.addMessage(session.getId(), MessageRole.ASSISTANT, answer, meta.toString(), promptTokens + completionTokens);
        }
        usageService.recordUsage(userId, projectId, "CHAT", promptTokens + completionTokens, System.currentTimeMillis() - started,
                Json.write(Map.of("model", props.getLlm().getModel(), "files", written.size())));

        if (!written.isEmpty()) {
            ragService.indexFilesAsync(projectId, writtenContent);                           // chunk + embed + ingest
            publisher.publish(new CodeGeneratedEvent(projectId, userId, List.copyOf(written)));   // "code.generated"
        }
        if (clientGone) return;

        try {
            if (errorMessage != null) out.send("error", Map.of("message", errorMessage));
            out.send("done", Map.of("files", written, "promptTokens", promptTokens, "completionTokens", completionTokens));
        } catch (UncheckedIOException ignored) {
            // client left while we were finishing
        }
    }

    private static ObjectNode message(String role, String content) {
        ObjectNode node = Json.mapper().createObjectNode();
        node.put("role", role);
        node.put("content", content == null ? "" : content);
        return node;
    }
}
