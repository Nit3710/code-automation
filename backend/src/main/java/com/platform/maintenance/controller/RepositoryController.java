package com.platform.maintenance.controller;

import com.platform.maintenance.common.ApiResponse;
import com.platform.maintenance.dto.RepositoryDtos;
import com.platform.maintenance.model.RepositoryEntity;
import com.platform.maintenance.model.User;
import com.platform.maintenance.service.ProjectDetectorService;
import com.platform.maintenance.service.RepositoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/repositories")
@RequiredArgsConstructor
public class RepositoryController {

    private final RepositoryService repositoryService;
    private final ProjectDetectorService projectDetectorService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RepositoryDtos.RepositoryResponse>>> getRepositories(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(repositoryService.getRepositories(workspaceId, currentUser)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RepositoryDtos.RepositoryResponse>> connectRepository(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody RepositoryDtos.ConnectRepositoryRequest request) {
        RepositoryDtos.RepositoryResponse response = repositoryService.connectRepository(workspaceId, currentUser, request);
        return ResponseEntity.ok(ApiResponse.ok("Repository connected successfully", response));
    }

    @GetMapping("/{repositoryId}")
    public ResponseEntity<ApiResponse<RepositoryDtos.RepositoryResponse>> getRepository(
            @PathVariable Long workspaceId,
            @PathVariable Long repositoryId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(repositoryService.getRepository(workspaceId, repositoryId, currentUser)));
    }

    @PutMapping("/{repositoryId}/config")
    public ResponseEntity<ApiResponse<RepositoryDtos.RepositoryResponse>> updateConfiguration(
            @PathVariable Long workspaceId,
            @PathVariable Long repositoryId,
            @AuthenticationPrincipal User currentUser,
            @RequestBody RepositoryDtos.UpdateConfigurationRequest request) {
        RepositoryDtos.RepositoryResponse response = repositoryService.updateConfiguration(workspaceId, repositoryId, currentUser, request);
        return ResponseEntity.ok(ApiResponse.ok("Repository configuration updated", response));
    }

    @PostMapping("/{repositoryId}/detect")
    public ResponseEntity<ApiResponse<RepositoryDtos.ProjectDetectionResult>> detectStack(
            @PathVariable Long workspaceId,
            @PathVariable Long repositoryId,
            @AuthenticationPrincipal User currentUser) {
        RepositoryDtos.RepositoryResponse repo = repositoryService.getRepository(workspaceId, repositoryId, currentUser);
        File localDir = new File(repo.getCloneUrl());
        RepositoryDtos.ProjectDetectionResult detectionResult;
        if (localDir.exists() && localDir.isDirectory()) {
            detectionResult = projectDetectorService.detect(localDir);
        } else {
            // Default inference from repo name or configuration
            detectionResult = projectDetectorService.detect(new File("."));
        }
        return ResponseEntity.ok(ApiResponse.ok("Project stack detected", detectionResult));
    }
}
