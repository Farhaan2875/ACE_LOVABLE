package com.farhaan.projects.Ace.services.impl;

import com.farhaan.projects.Ace.Dto.project.FileContentResponse;
import com.farhaan.projects.Ace.Dto.project.FileNode;
import com.farhaan.projects.Ace.entities.ProjectFile;
import com.farhaan.projects.Ace.enums.ProjectRole;
import com.farhaan.projects.Ace.error.ResourceNotFoundException;
import com.farhaan.projects.Ace.intelligence.RagService;
import com.farhaan.projects.Ace.repositories.ProjectFileRepository;
import com.farhaan.projects.Ace.repositories.ProjectRepository;
import com.farhaan.projects.Ace.repositories.UserRepository;
import com.farhaan.projects.Ace.services.FileService;
import com.farhaan.projects.Ace.services.ProjectAccessService;
import com.farhaan.projects.Ace.storage.ObjectStorage;
import com.farhaan.projects.Ace.util.PathUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    /** Files of the starter app (they live in src/main/resources/scaffold). */
    private static final List<String> SCAFFOLD_FILES = List.of(
            "package.json", "vite.config.js", "index.html", "src/main.jsx", "src/App.jsx", "src/index.css");

    private final ProjectFileRepository projectFileRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ObjectStorage storage;
    private final ProjectAccessService access;
    private final RagService ragService;

    // ------------------------------------------------------------------ controller-facing

    @Override
    @Transactional(readOnly = true)
    public List<FileNode> getFileTree(Long projectId, Long userId) {
        access.requireRole(projectId, userId, ProjectRole.VIEWER);
        return listFiles(projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public FileContentResponse getFileContent(Long projectId, String path, Long userId) {
        access.requireRole(projectId, userId, ProjectRole.VIEWER);
        String clean = PathUtil.normalize(path);   // "/{*path}" arrives with a leading slash
        String content = readFile(projectId, clean)
                .orElseThrow(() -> new ResourceNotFoundException("File", clean));
        return new FileContentResponse(clean, content);
    }

    // ------------------------------------------------------------------ internal

    @Override
    @Transactional(readOnly = true)
    public List<FileNode> listFiles(Long projectId) {
        return projectFileRepository.findByProjectIdOrderByPathAsc(projectId).stream()
                .map(f -> new FileNode(f.getPath(), f.getUpdatedAt(), f.getSize(), PathUtil.extension(f.getPath())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> readFile(Long projectId, String path) {
        return projectFileRepository.findByProjectIdAndPath(projectId, path)
                .map(f -> new String(storage.get(f.getMinioObjectKey()), StandardCharsets.UTF_8));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> readAllFiles(Long projectId) {
        Map<String, String> result = new LinkedHashMap<>();
        for (ProjectFile f : projectFileRepository.findByProjectIdOrderByPathAsc(projectId)) {
            result.put(f.getPath(), new String(storage.get(f.getMinioObjectKey()), StandardCharsets.UTF_8));
        }
        return result;
    }

    @Override
    @Transactional
    public void writeFile(Long projectId, String path, String content, Long userId) {
        String clean = PathUtil.normalize(path);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String key = "projects/" + projectId + "/" + clean;

        storage.put(key, bytes, contentTypeFor(clean));   // 1) "Store the file index.html" in MinIO

        ProjectFile file = projectFileRepository.findByProjectIdAndPath(projectId, clean)   // 2) remember it in the DB
                .orElseGet(() -> ProjectFile.builder()
                        .project(projectRepository.getReferenceById(projectId))
                        .path(clean)
                        .createdBy(userRepository.getReferenceById(userId))
                        .build());
        file.setMinioObjectKey(key);
        file.setSize((long) bytes.length);
        file.setUpdatedBy(userRepository.getReferenceById(userId));
        projectFileRepository.save(file);
    }

    @Override
    public void scaffold(Long projectId, Long userId, String projectName) {
        String safeName = projectName.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (safeName.isEmpty()) safeName = "My App";

        Map<String, String> written = new LinkedHashMap<>();
        for (String path : SCAFFOLD_FILES) {
            String content = loadScaffold(path).replace("{{NAME}}", safeName);
            writeFile(projectId, path, content, userId);
            written.put(path, content);
        }
        ragService.indexFilesAsync(projectId, written);   // starter code is searchable too
    }

    private String loadScaffold(String path) {
        try (InputStream in = new ClassPathResource("scaffold/" + path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing scaffold file: " + path, e);
        }
    }

    private String contentTypeFor(String path) {
        return switch (PathUtil.extension(path)) {
            case "html" -> "text/html";
            case "css" -> "text/css";
            case "json" -> "application/json";
            case "js", "jsx", "mjs" -> "text/javascript";
            case "svg" -> "image/svg+xml";
            default -> "text/plain";
        };
    }
}
