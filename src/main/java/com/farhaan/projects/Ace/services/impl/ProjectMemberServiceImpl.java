package com.farhaan.projects.Ace.services.impl;

import com.farhaan.projects.Ace.Dto.member.InviteMemberRequest;
import com.farhaan.projects.Ace.Dto.member.MemberResponse;
import com.farhaan.projects.Ace.Dto.member.UpdateMemberRoleRequest;
import com.farhaan.projects.Ace.entities.Project;
import com.farhaan.projects.Ace.entities.ProjectMember;
import com.farhaan.projects.Ace.entities.ProjectMemberId;
import com.farhaan.projects.Ace.entities.User;
import com.farhaan.projects.Ace.enums.ProjectRole;
import com.farhaan.projects.Ace.error.BadRequestException;
import com.farhaan.projects.Ace.error.ForbiddenException;
import com.farhaan.projects.Ace.error.ResourceNotFoundException;
import com.farhaan.projects.Ace.mapper.ProjectMemberMapper;
import com.farhaan.projects.Ace.repositories.ProjectMemberRepository;
import com.farhaan.projects.Ace.repositories.UserRepository;
import com.farhaan.projects.Ace.services.ProjectAccessService;
import com.farhaan.projects.Ace.services.ProjectMemberService;
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
@Transactional
public class ProjectMemberServiceImpl implements ProjectMemberService {

    ProjectMemberRepository projectMemberRepository;
    ProjectMemberMapper projectMemberMapper;
    UserRepository userRepository;
    ProjectAccessService access;

    @Override
    public List<MemberResponse> getProjectMembers(Long projectId, Long userId) {
        access.requireRole(projectId, userId, ProjectRole.VIEWER);
        return projectMemberRepository.findByIdProjectId(projectId)
                .stream()
                .map(projectMemberMapper::toProjectMemberResponseFromMember)
                .toList();
    }

    @Override
    public MemberResponse inviteMember(Long projectId, InviteMemberRequest request, Long userId) {
        Project project = access.requireRole(projectId, userId, ProjectRole.OWNER);   // only the owner invites

        if (request.role() == ProjectRole.OWNER) {
            throw new BadRequestException("A project has exactly one owner - invite as EDITOR or VIEWER");
        }
        String username = request.username().trim().toLowerCase();
        User invitee = userRepository.findByUsername(username)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        // BUG FIX: the key must contain the INVITEE's id (it used the inviter's id before)
        ProjectMemberId projectMemberId = new ProjectMemberId(projectId, invitee.getId());
        if (projectMemberRepository.existsById(projectMemberId)) {
            throw new BadRequestException("This user is already a member of the project");
        }

        ProjectMember member = ProjectMember.builder()
                .id(projectMemberId)
                .project(project)
                .user(invitee)
                .projectRole(request.role())
                .invitedAt(Instant.now())
                .acceptedAt(Instant.now())   // no accept-flow yet: invited users get access immediately
                .build();
        projectMemberRepository.save(member);
        return projectMemberMapper.toProjectMemberResponseFromMember(member);
    }

    @Override
    public MemberResponse updateMemberRole(Long projectId, Long memberId, UpdateMemberRoleRequest request, Long userId) {
        access.requireRole(projectId, userId, ProjectRole.OWNER);

        ProjectMember projectMember = projectMemberRepository.findById(new ProjectMemberId(projectId, memberId))
                .orElseThrow(() -> new ResourceNotFoundException("Member", memberId.toString()));
        if (projectMember.getProjectRole() == ProjectRole.OWNER || request.role() == ProjectRole.OWNER) {
            throw new BadRequestException("The owner role cannot be changed or handed out");
        }

        projectMember.setProjectRole(request.role());
        projectMemberRepository.save(projectMember);
        return projectMemberMapper.toProjectMemberResponseFromMember(projectMember);
    }

    @Override
    public Void removeProjectMember(Long projectId, Long memberId, Long userId) {
        ProjectRole myRole = access.requireRole(projectId, userId, ProjectRole.VIEWER) == null ? null :
                projectMemberRepository.findById(new ProjectMemberId(projectId, userId)).orElseThrow().getProjectRole();
        boolean leavingMyself = memberId.equals(userId);
        if (!leavingMyself && myRole != ProjectRole.OWNER) {
            throw new ForbiddenException("Only the owner can remove other members");
        }

        ProjectMember target = projectMemberRepository.findById(new ProjectMemberId(projectId, memberId))
                .orElseThrow(() -> new ResourceNotFoundException("Member", memberId.toString()));
        if (target.getProjectRole() == ProjectRole.OWNER) {
            throw new BadRequestException("The owner cannot be removed - delete the project instead");
        }
        projectMemberRepository.delete(target);
        return null;
    }
}
