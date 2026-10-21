package com.platform.maintenance.controller;

import com.platform.maintenance.common.ApiResponse;
import com.platform.maintenance.dto.WorkspaceDtos;
import com.platform.maintenance.model.User;
import com.platform.maintenance.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkspaceDtos.WorkspaceResponse>>> getUserWorkspaces(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(workspaceService.getUserWorkspaces(currentUser)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkspaceDtos.WorkspaceResponse>> createWorkspace(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody WorkspaceDtos.CreateWorkspaceRequest request) {
        WorkspaceDtos.WorkspaceResponse workspace = workspaceService.createWorkspace(currentUser, request);
        return ResponseEntity.ok(ApiResponse.ok("Workspace created", workspace));
    }
}
