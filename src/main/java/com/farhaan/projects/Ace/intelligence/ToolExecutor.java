package com.farhaan.projects.Ace.intelligence;

import com.farhaan.projects.Ace.Dto.project.FileNode;
import com.farhaan.projects.Ace.services.FileService;
import com.farhaan.projects.Ace.util.PathUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/** Runs a tool the model asked for and returns text the model can read. Always scoped to ONE project. */
@Component
@RequiredArgsConstructor
public class ToolExecutor {
    private static final int MAX_CHARS = 20_000;   // keep the model's context small
    private final FileService fileService;

    public String execute(Long projectId, String name, JsonNode args) {
        try {
            return switch (name) {
                case "list_files" -> {
                    String list = fileService.listFiles(projectId).stream()
                            .map((FileNode f) -> f.path() + " (" + f.size() + " bytes)")
                            .collect(Collectors.joining("\n"));
                    yield list.isEmpty() ? "(the project has no files)" : list;
                }
                case "get_file_content" -> {
                    String path = PathUtil.normalizeOrNull(args.path("path").asText(""));
                    if (path == null) yield "error: invalid path";
                    String content = fileService.readFile(projectId, path).orElse(null);
                    if (content == null) yield "error: file not found: " + path;
                    yield content.length() > MAX_CHARS ? content.substring(0, MAX_CHARS) + "\n...(truncated)" : content;
                }
                default -> "error: unknown tool " + name;
            };
        } catch (RuntimeException e) {
            return "error: " + e.getMessage();
        }
    }
}
