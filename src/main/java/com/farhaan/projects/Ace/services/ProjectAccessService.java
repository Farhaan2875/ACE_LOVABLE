package com.farhaan.projects.Ace.services;

import com.farhaan.projects.Ace.entities.Project;
import com.farhaan.projects.Ace.entities.ProjectMember;
import com.farhaan.projects.Ace.entities.ProjectMemberId;
import com.farhaan.projects.Ace.enums.ProjectRole;
import com.farhaan.projects.Ace.error.ForbiddenException;
import com.farhaan.projects.Ace.error.ResourceNotFoundException;
import com.farhaan.projects.Ace.repositories.ProjectMemberRepository;
import com.farhaan.projects.Ace.repositories.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ONE place that decides "may this user do this in this project?".
 * VIEWER: read files / see preview.   EDITOR: + chat with the AI, edit, run preview.   OWNER: + members, delete.
 */
@Service
@RequiredArgsConstructor
public class ProjectAccessService {
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    /**
     * Returns the project if the user is a member with at least the given role.
     * Non-members get a 404 (we don't reveal that the project exists); members with too low a role get a 403.
     */
    @Transactional(readOnly = true)
    public Project requireRole(Long projectId, Long userId, ProjectRole minimum) {
        Project project = projectRepository.findAccessibleProjectById(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
        ProjectMember member = projectMemberRepository.findById(new ProjectMemberId(projectId, userId))
                .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
        if (!member.getProjectRole().atLeast(minimum)) {
            throw new ForbiddenException("You need " + minimum + " access for this action");
        }
        return project;
    }
}
