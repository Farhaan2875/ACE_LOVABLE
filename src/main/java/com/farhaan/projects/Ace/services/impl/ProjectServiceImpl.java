package com.farhaan.projects.Ace.services.impl;

import com.farhaan.projects.Ace.Dto.project.ProjectRequest;
import com.farhaan.projects.Ace.Dto.project.ProjectResponse;
import com.farhaan.projects.Ace.Dto.project.ProjectSummaryResponse;
import com.farhaan.projects.Ace.entities.Plan;
import com.farhaan.projects.Ace.entities.Project;
import com.farhaan.projects.Ace.entities.ProjectMember;
import com.farhaan.projects.Ace.entities.ProjectMemberId;
import com.farhaan.projects.Ace.entities.User;
import com.farhaan.projects.Ace.enums.ProjectRole;
import com.farhaan.projects.Ace.error.PlanLimitException;
import com.farhaan.projects.Ace.error.ResourceNotFoundException;
import com.farhaan.projects.Ace.execution.ExecutionService;
import com.farhaan.projects.Ace.mapper.ProjectMapper;
import com.farhaan.projects.Ace.repositories.ProjectMemberRepository;
import com.farhaan.projects.Ace.repositories.ProjectRepository;
import com.farhaan.projects.Ace.repositories.UserRepository;
import com.farhaan.projects.Ace.services.FileService;
import com.farhaan.projects.Ace.services.PlanService;
import com.farhaan.projects.Ace.services.ProjectAccessService;
import com.farhaan.projects.Ace.services.ProjectService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Transactional // if error occurs the row is not created and it is roll backed
public class ProjectServiceImpl implements ProjectService {

    ProjectRepository projectRepository;
    UserRepository userRepository;
    ProjectMapper projectMapper; // alternative to model mapper - automatically maps -> see generated classes in target
    // also maps nested fields by generating relevant methods -> like user to userProfileResponse
    ProjectMemberRepository projectMemberRepository;
    ProjectAccessService access;
    PlanService planService;
    FileService fileService;
    ExecutionService executionService;

    @Override
    public List<ProjectSummaryResponse> getUserProjects(Long userId) {
        return projectMapper.toProjectSummaryResponse(projectRepository.findAllAccessibleByUser(userId));
    }

    @Override
    public ProjectResponse getUserProjectById(Long id, Long userId) {
        Project project = access.requireRole(id, userId, ProjectRole.VIEWER);
        return projectMapper.toProjectResponse(project);
    }

    @Override
    public ProjectResponse createProject(ProjectRequest request, Long userId) {
        User owner = userRepository.findById(userId).orElseThrow(() ->
                new ResourceNotFoundException("User", userId.toString())
        );

        // plan limit: how many projects may this user own?
        Plan plan = planService.getEffectivePlan(userId);
        if (plan.getMaxProjects() != null
                && projectRepository.countByOwnerIdAndDeletedAtIsNull(userId) >= plan.getMaxProjects()) {
            throw new PlanLimitException("Your " + plan.getName() + " plan allows " + plan.getMaxProjects()
                    + " projects. Delete one or upgrade your plan.");
        }

        Project project = Project.builder()
                .name(request.name().trim())
                .isPublic(false)
                .owner(owner)
                .build();
        project = projectRepository.save(project);

        ProjectMemberId projectMemberId = new ProjectMemberId(project.getId(), userId);
        ProjectMember projectMember = ProjectMember.builder()
                .id(projectMemberId)
                .projectRole(ProjectRole.OWNER)
                .user(owner)
                .acceptedAt(Instant.now())
                .invitedAt(Instant.now())
                .project(project)
                .build();
        projectMemberRepository.save(projectMember);

        fileService.scaffold(project.getId(), userId, project.getName());   // every project starts with a working Vite + React app
        return projectMapper.toProjectResponse(project);
    }

    @Override
    public ProjectResponse updateProject(Long id, ProjectRequest request, Long userId) {
        Project project = access.requireRole(id, userId, ProjectRole.EDITOR);

        project.setName(request.name().trim());
        project = projectRepository.save(project);
        return projectMapper.toProjectResponse(project);
    }

    @Override
    public void softDelete(Long id, Long userId) {
        Project project = access.requireRole(id, userId, ProjectRole.OWNER);   // only the owner can delete

        project.setDeletedAt(Instant.now());
        projectRepository.save(project);
        executionService.destroyQuietly(id);   // stop the running preview, free its resources
    }
}
