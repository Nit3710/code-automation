package com.platform.maintenance.service;

import com.platform.maintenance.common.ResourceNotFoundException;
import com.platform.maintenance.common.UnauthorizedException;
import com.platform.maintenance.dto.WorkspaceDtos;
import com.platform.maintenance.model.User;
import com.platform.maintenance.model.Workspace;
import com.platform.maintenance.model.WorkspaceMember;
import com.platform.maintenance.model.enums.Role;
import com.platform.maintenance.repository.WorkspaceMemberRepository;
import com.platform.maintenance.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public List<WorkspaceDtos.WorkspaceResponse> getUserWorkspaces(User currentUser) {
        List<Workspace> workspaces = workspaceRepository.findWorkspacesByUserId(currentUser.getId());
        return workspaces.stream().map(ws -> {
            WorkspaceMember member = workspaceMemberRepository
                    .findByWorkspaceIdAndUserId(ws.getId(), currentUser.getId())
                    .orElse(null);
            Role role = member != null ? member.getRole() : Role.MEMBER;
            return WorkspaceDtos.WorkspaceResponse.builder()
                    .id(ws.getId())
                    .name(ws.getName())
                    .slug(ws.getSlug())
                    .ownerId(ws.getOwner().getId())
                    .userRole(role)
                    .createdAt(ws.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public WorkspaceDtos.WorkspaceResponse createWorkspace(User currentUser, WorkspaceDtos.CreateWorkspaceRequest request) {
        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = request.getName().toLowerCase().replaceAll("[^a-z0-9]", "-");
        }
        if (workspaceRepository.existsBySlug(slug)) {
            slug = slug + "-" + System.currentTimeMillis() % 10000;
        }

        Workspace workspace = Workspace.builder()
                .name(request.getName())
                .slug(slug)
                .owner(currentUser)
                .build();
        workspace = workspaceRepository.save(workspace);

        WorkspaceMember member = WorkspaceMember.builder()
                .workspace(workspace)
                .user(currentUser)
                .role(Role.OWNER)
                .build();
        workspaceMemberRepository.save(member);

        return WorkspaceDtos.WorkspaceResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .slug(workspace.getSlug())
                .ownerId(currentUser.getId())
                .userRole(Role.OWNER)
                .createdAt(workspace.getCreatedAt())
                .build();
    }

    public Workspace validateWorkspaceAccess(Long workspaceId, User currentUser) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));

        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, currentUser.getId())) {
            throw new UnauthorizedException("Access denied: You are not a member of this workspace.");
        }
        return workspace;
    }
}
