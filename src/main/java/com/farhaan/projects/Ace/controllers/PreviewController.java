package com.farhaan.projects.Ace.controllers;

import com.farhaan.projects.Ace.Dto.preview.PreviewResponse;
import com.farhaan.projects.Ace.execution.ExecutionService;
import com.farhaan.projects.Ace.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/preview")
public class PreviewController {
    private final ExecutionService executionService;

    @GetMapping
    public ResponseEntity<PreviewResponse> get(@PathVariable Long projectId) {
        return ResponseEntity.ok(executionService.getPreview(projectId, CurrentUser.id()));
    }

    @PostMapping
    public ResponseEntity<PreviewResponse> start(@PathVariable Long projectId) {
        return ResponseEntity.accepted().body(executionService.startPreview(projectId, CurrentUser.id()));
    }

    @DeleteMapping
    public ResponseEntity<PreviewResponse> stop(@PathVariable Long projectId) {
        return ResponseEntity.ok(executionService.stopPreview(projectId, CurrentUser.id()));
    }

    @GetMapping(value = "/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> logs(@PathVariable Long projectId) {
        return ResponseEntity.ok(executionService.logs(projectId, CurrentUser.id()));
    }
}
