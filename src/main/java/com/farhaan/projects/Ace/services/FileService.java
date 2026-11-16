package com.farhaan.projects.Ace.services;

import com.farhaan.projects.Ace.Dto.project.FileContentResponse;
import com.farhaan.projects.Ace.Dto.project.FileNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface FileService {
    // ---- used by controllers (role is checked) ----
    List<FileNode> getFileTree(Long projectId, Long userId);

    FileContentResponse getFileContent(Long projectId, String path, Long userId);

    // ---- used internally by the AI / execution code (access was already checked by the caller) ----
    List<FileNode> listFiles(Long projectId);

    Optional<String> readFile(Long projectId, String path);

    /** path -> content for every file; used to build previews. */
    Map<String, String> readAllFiles(Long projectId);

    /** Creates or replaces a file: content goes to object storage, metadata to the database. */
    void writeFile(Long projectId, String path, String content, Long userId);

    /** Creates the starter Vite + React app for a brand-new project. */
    void scaffold(Long projectId, Long userId, String projectName);
}
