package com.farhaan.projects.Ace.execution;

import com.farhaan.projects.Ace.Dto.preview.PreviewMapper;
import com.farhaan.projects.Ace.Dto.preview.PreviewResponse;
import com.farhaan.projects.Ace.config.AceProperties;
import com.farhaan.projects.Ace.entities.Plan;
import com.farhaan.projects.Ace.entities.Preview;
import com.farhaan.projects.Ace.entities.Project;
import com.farhaan.projects.Ace.enums.PreviewStatus;
import com.farhaan.projects.Ace.enums.ProjectRole;
import com.farhaan.projects.Ace.error.PlanLimitException;
import com.farhaan.projects.Ace.events.CodeGeneratedEvent;
import com.farhaan.projects.Ace.repositories.PreviewRepository;
import com.farhaan.projects.Ace.repositories.ProjectRepository;
import com.farhaan.projects.Ace.services.FileService;
import com.farhaan.projects.Ace.services.PlanService;
import com.farhaan.projects.Ace.services.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

/** The "execution-service": starts, refreshes and stops the live preview of a project. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {
    private static final List<PreviewStatus> ACTIVE = List.of(PreviewStatus.CREATING, PreviewStatus.RUNNING);

    private final PreviewRepository previewRepository;
    private final ProjectRepository projectRepository;
    private final FileService fileService;
    private final PreviewRunner runner;
    private final ExecutorService executor;
    private final AceProperties props;
    private final PlanService planService;
    private final ProjectAccessService access;

    /** One deployment per project at a time. */
    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ API used by controllers

    public PreviewResponse getPreview(Long projectId, Long userId) {
        access.requireRole(projectId, userId, ProjectRole.VIEWER);
        return previewRepository.findByProjectId(projectId).map(PreviewMapper::of).orElse(PreviewMapper.none());
    }

    public PreviewResponse startPreview(Long projectId, Long userId) {
        Project project = access.requireRole(projectId, userId, ProjectRole.EDITOR);
        return requestDeploy(project);
    }

    public PreviewResponse stopPreview(Long projectId, Long userId) {
        access.requireRole(projectId, userId, ProjectRole.EDITOR);
        runner.stop(projectId);
        Optional<Preview> found = previewRepository.findByProjectId(projectId);
        if (found.isEmpty()) return PreviewMapper.none();
        Preview pv = found.get();
        pv.setStatus(PreviewStatus.TERMINATED);
        pv.setStatusMessage("Stopped");
        pv.setTerminatedAt(Instant.now());
        return PreviewMapper.of(previewRepository.save(pv));
    }

    public String logs(Long projectId, Long userId) {
        access.requireRole(projectId, userId, ProjectRole.VIEWER);
        return runner.logs(projectId);
    }

    // ------------------------------------------------------------------ "code.generated" consumer

    /** Called for every code.generated event (from Kafka or in-process). */
    public void onCodeGenerated(CodeGeneratedEvent event) {
        Project project = projectRepository.findById(event.projectId()).orElse(null);
        if (project == null || project.getDeletedAt() != null) return;

        boolean previewActive = previewRepository.findByProjectId(event.projectId())
                .map(p -> ACTIVE.contains(p.getStatus())).orElse(false);
        if (!previewActive && !props.getExecution().isAutoPreview()) return;

        try {
            requestDeploy(project);
        } catch (PlanLimitException e) {
            log.info("Auto preview skipped for project {}: {}", event.projectId(), e.getMessage());
        }
    }

    // ------------------------------------------------------------------ project deletion

    public void destroyQuietly(Long projectId) {
        try {
            runner.destroy(projectId);
            previewRepository.findByProjectId(projectId).ifPresent(previewRepository::delete);
        } catch (Exception e) {
            log.warn("Could not clean up preview of project {}: {}", projectId, e.getMessage());
        }
    }

    /** Local processes die with the backend, so after a restart nothing is really running. */
    @EventListener(ApplicationReadyEvent.class)
    public void resetStalePreviews() {
        if (runner.survivesRestart()) return;
        for (Preview pv : previewRepository.findByStatusIn(ACTIVE)) {
            pv.setStatus(PreviewStatus.TERMINATED);
            pv.setStatusMessage("Server restarted");
            previewRepository.save(pv);
        }
    }

    // ------------------------------------------------------------------ internals

    private PreviewResponse requestDeploy(Project project) {
        Long projectId = project.getId();
        Preview pv = previewRepository.findByProjectId(projectId)
                .orElseGet(() -> Preview.builder().project(project).status(PreviewStatus.TERMINATED).build());

        boolean alreadyActive = ACTIVE.contains(pv.getStatus());
        if (!alreadyActive && project.getOwner() != null) {      // starting a NEW preview counts against the plan
            Plan plan = planService.getEffectivePlan(project.getOwner().getId());
            long active = previewRepository.countActiveForOwner(project.getOwner().getId(), ACTIVE);
            if (plan.getMaxPreviews() != null && active >= plan.getMaxPreviews()) {
                throw new PlanLimitException("Your " + plan.getName() + " plan allows " + plan.getMaxPreviews()
                        + " running preview(s). Stop another preview or upgrade.");
            }
        }
        pv.setStatus(PreviewStatus.CREATING);
        pv.setStatusMessage("Starting preview...");
        pv.setTerminatedAt(null);
        pv = previewRepository.save(pv);

        executor.submit(() -> deploy(projectId));
        return PreviewMapper.of(pv);
    }

    private void deploy(Long projectId) {
        ReentrantLock lock = locks.computeIfAbsent(projectId, id -> new ReentrantLock());
        lock.lock();
        try {
            Map<String, String> files = fileService.readAllFiles(projectId);
            RunResult result = runner.start(projectId, files);
            update(projectId, pv -> {
                pv.setStatus(PreviewStatus.RUNNING);
                pv.setPreviewUrl(result.url());
                pv.setNamespace(result.namespace());
                pv.setPodName(result.podName());
                pv.setStatusMessage(null);
                pv.setStartedAt(Instant.now());
            });
        } catch (Exception e) {
            log.warn("Preview of project {} failed: {}", projectId, e.getMessage());
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            update(projectId, pv -> {
                pv.setStatus(PreviewStatus.FAILED);
                pv.setStatusMessage(message.length() > 900 ? message.substring(0, 900) : message);
            });
        } finally {
            lock.unlock();
        }
    }

    private void update(Long projectId, java.util.function.Consumer<Preview> change) {
        previewRepository.findByProjectId(projectId).ifPresent(pv -> {
            change.accept(pv);
            previewRepository.save(pv);
        });
    }
}
